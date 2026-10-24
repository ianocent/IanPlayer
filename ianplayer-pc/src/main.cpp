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

#include "playback/playback_controller.h"
#include "net/stream_url_resolver.h"

#include "core/library_scanner.h"
#include "db/library_database.h"
#include "app/library_service.h"
#include "models/library_song_model.h"
#include "models/library_proxy_model.h"
#include "models/album_list_model.h"
#include "models/artist_list_model.h"

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
    engine.rootContext()->setContextProperty(QStringLiteral("LibraryProxy"), &songProxy);
    engine.rootContext()->setContextProperty(QStringLiteral("AlbumListModel"), &albumModel);
    engine.rootContext()->setContextProperty(QStringLiteral("ArtistListModel"), &artistModel);

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

    QObject::connect(
        &engine,
        &QQmlApplicationEngine::objectCreationFailed,
        &app,
        []() { QCoreApplication::exit(-1); },
        Qt::QueuedConnection);

    engine.loadFromModule("IanPlayer.PC", "AppWindow");

    return app.exec();
}
