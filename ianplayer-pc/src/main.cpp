#include <QGuiApplication>
#include <QQuickStyle>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QFontDatabase>
#include <QFont>
#include <QObject>
#include <QFileInfo>
#include <QTimer>
#include <QStandardPaths>
#include <QWindow>
#include <QSettings>

#include "playback/playback_controller.h"
#include "playback/queue_manager.h"
#include "platform/smtc_controller.h"
#include "net/stream_url_resolver.h"

#include "core/library_scanner.h"
#include "db/library_database.h"
#include "app/library_service.h"
#include "models/library_song_model.h"
#include "models/library_proxy_model.h"
#include "models/album_list_model.h"
#include "models/artist_list_model.h"
#include "models/playlist_list_model.h"
#include "models/playlist_song_model.h"

#include <iostream>

#include <windows.h>

namespace {

// Mode CLI dipanggil dari terminal: exe subsystem GUI tak punya stdout.
// Attach ke console parent biar cout kelihatan.
void attachParentConsole()
{
    if (AttachConsole(ATTACH_PARENT_PROCESS)) {
        freopen("CONOUT$", "w", stdout);
        freopen("CONOUT$", "w", stderr);
    }
}

constexpr const char *kAudioExt = "mp3 flac m4a aac wav ogg opus";

bool looksLikeUrl(const QString &arg)
{
    const QUrl url = QUrl::fromUserInput(arg);
    return url.isValid()
        && (url.scheme() == QLatin1String("http") || url.scheme() == QLatin1String("https"));
}

bool looksLikeLocalMedia(const QString &arg)
{
    const QFileInfo fi(arg);
    if (!fi.exists() || !fi.isFile())
        return false;
    const QStringList extensions = QString::fromUtf8(kAudioExt).split(QLatin1Char(' '));
    return extensions.contains(fi.suffix().toLower());
}

// Smoke "Hello Audio": IanPlayer.exe <file.flac|YouTube URL>
// Skrip programatik: main 6 s -> seek +10 -> jeda -> lanjut + volume 60 -> keluar
// pada endReached (atau timeout 120 s). Output progres ke stdout.
int runHiAudio(const QGuiApplication &app, const QString &target)
{
    attachParentConsole();

    PlaybackController mpv;
    if (!mpv.valid()) {
        std::cout << "[FAIL] mpv tidak siap\n";
        return 1;
    }

    QString trackUrl;
    if (looksLikeUrl(target)) {
        StreamUrlResolver resolver;
        QString err;
        const auto resolved = resolver.getStreamUrl(QUrl::fromUserInput(target), &err);
        if (!resolved) {
            std::cout << "[FAIL] resolve streaming: " << err.toStdString() << "\n";
            return 1;
        }
        std::cout << "[CACHE/RESOLVE] expiresAtMs=" << resolved->expiresAtMs << "\n";
        trackUrl = resolved->url;
    } else {
        trackUrl = QUrl::fromLocalFile(QFileInfo(target).absoluteFilePath()).toString();
    }

    QObject::connect(&mpv, &PlaybackController::trackLoaded, [] {
        std::cout << "[LOAD] file loaded, mulai putar\n";
    });
    QObject::connect(&mpv, &PlaybackController::endReached, [&app] {
        std::cout << "[END] eof tercapai\n";
        app.quit();
    });
    QObject::connect(&mpv, &PlaybackController::errorOccurred, [&app](const QString &msg) {
        std::cout << "[ERR] " << msg.toStdString() << "\n";
        app.quit();
    });

    QTimer::singleShot(6000, [&mpv] {
        std::cout << "[SEEK] +10 s (pos=" << mpv.positionSeconds() << ")\n";
        mpv.seekBy(10.0);
    });
    QTimer::singleShot(8000, [&mpv] {
        std::cout << "[PAUSE]\n";
        mpv.pause();
    });
    QTimer::singleShot(10000, [&mpv] {
        std::cout << "[RESUME] volume->60\n";
        mpv.setVolume(60);
        mpv.play();
    });
    // Safety quit: jangan gantung kalau track lebih panjang dari skrip
    QTimer::singleShot(120000, [&app] {
        std::cout << "[TIMEOUT]\n";
        app.quit();
    });

    mpv.loadTrack(QUrl(trackUrl));

    const int rc = app.exec();
    // Teardown order aman: PlaybackController dibangun STACK sebelum app keluar
    // scope main (destructor mpv=stop -> join thread -> mpv_terminate_destroy,
    // jauh sebelum QGuiApplication destructor).
    return rc == 0 ? 0 : 1;
}

} // namespace

int main(int argc, char *argv[])
{
    QGuiApplication app(argc, argv);
    app.setApplicationName(QStringLiteral("IanPlayer"));
    app.setApplicationVersion(QStringLiteral("0.1.0"));
    app.setOrganizationName(QStringLiteral("ianocent"));

    // Font bundel (M0.4): Inter static 4 weight; fail-soft kalau resource hilang
    const QStringList fontFiles = {
        QStringLiteral(":/fonts/Inter-Regular.ttf"),
        QStringLiteral(":/fonts/Inter-Medium.ttf"),
        QStringLiteral(":/fonts/Inter-SemiBold.ttf"),
        QStringLiteral(":/fonts/Inter-Bold.ttf"),
    };
    for (const QString &file : fontFiles)
        [[maybe_unused]] const int fontId = QFontDatabase::addApplicationFont(file);
    if (QFontDatabase::families().contains(QLatin1String("Inter")))
        app.setFont(QFont(QStringLiteral("Inter"), 10));

    // Mode CLI smoke "Hello Audio" (exit criteria M0.3)
    const QStringList args = QCoreApplication::arguments();
    if (args.size() > 1) {
        const QString target = args.at(1);
        if (looksLikeUrl(target) || looksLikeLocalMedia(target))
            return runHiAudio(app, target);
        std::cout << "Argumen bukan file media atau URL yang dikenal: "
                  << target.toStdString() << "\n";
        return 2;
    }

    // Mode GUI biasa
    QQuickStyle::setStyle(QStringLiteral("Basic"));

    QQmlApplicationEngine engine;

    // DB + scanner + service (M1.1) — di-expose ke QML context
    const QString dbPath = QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation)
        + QStringLiteral("/ianplayer-pc.db");
    LibraryScanner scanner;
    LibraryDatabase libDb(dbPath);
    LibraryService service(&scanner, &libDb);
    engine.rootContext()->setContextProperty(QStringLiteral("LibraryService"), &service);

    // Models library M1.2 — semua akses DB di main thread (pitfall doc)
    LibrarySongModel songModel(&libDb);
    LibraryProxyModel songProxy;
    songProxy.setSourceModel(&songModel);
    songProxy.sort(0);
    AlbumListModel albumModel(&libDb);
    ArtistListModel artistModel(&libDb);
    PlaylistListModel playlistModel(&libDb);
    PlaylistSongModel playlistSongsModel(&libDb);
    engine.rootContext()->setContextProperty(QStringLiteral("LibraryProxy"), &songProxy);
    engine.rootContext()->setContextProperty(QStringLiteral("AlbumListModel"), &albumModel);
    engine.rootContext()->setContextProperty(QStringLiteral("ArtistListModel"), &artistModel);
    engine.rootContext()->setContextProperty(QStringLiteral("PlaylistListModel"), &playlistModel);
    engine.rootContext()->setContextProperty(QStringLiteral("PlaylistSongModel"), &playlistSongsModel);

    // Playback engine M2.1 — QueueManager satu sumber kebenaran queue
    PlaybackController playbackController;
    QSettings playbackSettings(QStringLiteral("ianocent"), QStringLiteral("IanPlayer"));
    auto resolveSong = [&libDb](const QString &key, QueueManager::SongInfo &out) -> bool {
        if (!key.startsWith(QStringLiteral("local:")))
            return false;
        const qint64 id = key.mid(6).toLongLong();
        for (const auto &rec : libDb.allSongs()) {
            if (rec.id != id)
                continue;
            out.key = key;
            out.title = rec.title;
            out.artist = rec.artist;
            out.album = rec.album;
            out.durationMs = rec.durationMs;
            out.art = rec.art256;
            out.playUrl = rec.path;
            out.isStream = false;
            out.needsResolution = false;
            return true;
        }
        return false;
    };

    QueueManager::EngineHooks hooks;
    hooks.load = [&playbackController](const QUrl &u) { playbackController.loadTrack(u); };
    hooks.play = [&playbackController]() { playbackController.play(); };
    hooks.pause = [&playbackController]() { playbackController.pause(); };
    hooks.position = [&playbackController]() { return playbackController.positionSeconds(); };
    hooks.duration = [&playbackController]() { return playbackController.durationSeconds(); };
    hooks.paused = [&playbackController]() { return playbackController.pausedNow(); };
    hooks.setVolume = [&playbackController](int v) { playbackController.setVolume(v); };
    hooks.seekToMs = [&playbackController](qint64 ms) { playbackController.seekBy(ms / 1000.0); };

    QueueManager queueManager(hooks, resolveSong, &playbackSettings, &app);
    engine.rootContext()->setContextProperty(QStringLiteral("QueueManager"), &queueManager);

    QObject::connect(&playbackController, &PlaybackController::endReached,
                     &queueManager, &QueueManager::onTrackEnded);
    QObject::connect(&playbackController, &PlaybackController::errorOccurred,
                     &queueManager, &QueueManager::onPlaybackError);
    QObject::connect(&playbackController, &PlaybackController::positionChanged,
                     &queueManager, [&playbackController, &queueManager](double) {
        queueManager.onPositionTick(
            static_cast<qint64>(playbackController.positionSeconds() * 1000.0),
            static_cast<qint64>(playbackController.durationSeconds() * 1000.0));
    });
    QObject::connect(&playbackController, &PlaybackController::pausedChanged,
                     &queueManager, [&queueManager](bool paused) { queueManager.setPlaying(!paused); });
    QObject::connect(&queueManager, &QueueManager::songStarted,
                     &service, &LibraryService::recordPlayedByKey);

    // snapshot ulang model saat scan sukses / favorit ganti
    QObject::connect(&service, &LibraryService::libraryChanged, [&] {
        songModel.reload();
        albumModel.reload();
        artistModel.reload();
        songProxy.invalidate();   // ulang filter+sort di atas snapshot baru
    });
    QObject::connect(&service, &LibraryService::favoritesChanged, [&] {
        songModel.reload();
        songProxy.invalidate();
    });
    QObject::connect(&service, &LibraryService::playlistsChanged, [&] {
        playlistModel.reload();
    });
    QObject::connect(&service, &LibraryService::playlistContentsChanged,
                     [&](qint64 playlistId) {
        if (playlistId == playlistSongsModel.playlistKey())
            playlistSongsModel.reload();
        playlistModel.reload();   // count ikut berubah
    });

    QObject::connect(
        &engine,
        &QQmlApplicationEngine::objectCreationFailed,
        &app,
        []() { QCoreApplication::exit(-1); },
        Qt::QueuedConnection);

    engine.loadFromModule("IanPlayer.PC", "AppWindow");

    // SMTC (M2.3): expose playback to Windows media keys / volume OSD
    SmtcController *smtc = nullptr;
    if (!engine.rootObjects().isEmpty()) {
        if (QWindow *w = qobject_cast<QWindow *>(engine.rootObjects().first())) {
            smtc = new SmtcController(reinterpret_cast<void *>(w->winId()), &queueManager, &app);
            smtc->updateMetadata(queueManager.currentTitle(), queueManager.currentArtist(),
                                 queueManager.currentArt());
            smtc->setPlaybackStatus(queueManager.isPlaying());
            QObject::connect(&queueManager, &QueueManager::currentSongChanged, smtc,
                             [smtc, &queueManager]() {
                smtc->updateMetadata(queueManager.currentTitle(), queueManager.currentArtist(),
                                     queueManager.currentArt());
            });
            QObject::connect(&queueManager, &QueueManager::isPlayingChanged, smtc,
                             [smtc](bool p) { smtc->setPlaybackStatus(p); });
        }
    }

    return app.exec();
}
