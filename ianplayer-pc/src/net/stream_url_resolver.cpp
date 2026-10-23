#include "net/stream_url_resolver.h"

#include <QCoreApplication>
#include <QDir>
#include <QFileInfo>
#include <QJsonArray>
#include <QJsonDocument>
#include <QJsonObject>
#include <QProcess>
#include <QStandardPaths>
#include <QUrlQuery>
#include <QtSql/QSqlError>
#include <QtSql/QSqlQuery>

namespace {

constexpr qint64 kNoExpire = -1;
// Fallback kalau direct URL tidak membawa `expire=` — anggap valid 6 jam
// (nilai umum googlevideo), lalu re-resolve di percobaan berikutnya.
constexpr qint64 kDefaultTtlMs = 6 * 60 * 60 * 1000;

QString defaultDbPath()
{
    const QString dir = QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation);
    if (dir.isEmpty())
        return {};
    return dir + QStringLiteral("/ianplayer-pc.db");
}

QString ytDlpCandidates(const QString &exeDir)
{
    // 1) di samping exe (layout deploy), 2) fallback repo third_party (dev build)
    const QString third = exeDir + QStringLiteral("/third_party/ytdlp/yt-dlp.exe");
    if (QFileInfo::exists(exeDir + QStringLiteral("/yt-dlp.exe")))
        return exeDir + QStringLiteral("/yt-dlp.exe");
    if (QFileInfo::exists(third))
        return third;
    return exeDir + QStringLiteral("/yt-dlp.exe");
}

} // namespace

StreamUrlResolver::StreamUrlResolver(QString dbPath)
{
    if (dbPath.isEmpty())
        dbPath = defaultDbPath();

    const QString connId = QStringLiteral("stream-cache");
    if (!QSqlDatabase::contains(connId))
        m_db = QSqlDatabase::addDatabase(QStringLiteral("QSQLITE"), connId);
    else
        m_db = QSqlDatabase::database(connId);

    if (dbPath.isEmpty() || m_db.isValid() == false)
        return;

    QDir().mkpath(QFileInfo(dbPath).absolutePath());
    m_db.setDatabaseName(dbPath);
    if (!m_db.open()) {
        m_db = QSqlDatabase(); // kosong; cache jadi no-op pasif
        return;
    }

    QSqlQuery q(m_db);
    q.exec(QStringLiteral(
        "CREATE TABLE IF NOT EXISTS cached_stream_urls ("
        "video_id TEXT PRIMARY KEY NOT NULL,"
        "url TEXT NOT NULL,"
        "resolvedAtMs INTEGER NOT NULL,"
        "expiresAtMs INTEGER NOT NULL)"));

    // Migrasi dari tabel lama (kolom videoId): rename ke video_id sesuai
    // 02-ARCHITECTURE.md. Abaikan error kalau sudah video_id / tabel belum ada.
    QSqlQuery mig(m_db);
    mig.exec(QStringLiteral(
        "ALTER TABLE cached_stream_urls RENAME COLUMN videoId TO video_id"));
}

bool StreamUrlResolver::isYouTubeUrl(const QUrl &url)
{
    const QString host = url.host().toLower();
    return host == QLatin1String("youtu.be")
        || host == QLatin1String("m.youtube.com")
        || host.endsWith(QLatin1String(".youtube.com"))
        || host.endsWith(QLatin1String(".youtube-nocookie.com"));
}

QString StreamUrlResolver::extractVideoId(const QUrl &url)
{
    if (!isYouTubeUrl(url))
        return {};

    const QString host = url.host().toLower();
    if (host == QLatin1String("youtu.be")) {
        const QStringList segs = url.path().split(QLatin1Char('/'), Qt::SkipEmptyParts);
        if (!segs.isEmpty())
            return segs.constFirst();
    }

    QUrlQuery query(url);
    const QString v = query.queryItemValue(QStringLiteral("v"));
    if (!v.isEmpty())
        return v;

    // youtube.com/shorts/<id>, /embed/<id>, /live/<id>
    const QStringList segs = url.path().split(QLatin1Char('/'), Qt::SkipEmptyParts);
    for (int i = 0; i + 1 < segs.size(); ++i) {
        const QString &seg = segs.at(i);
        if (seg == QLatin1String("shorts") || seg == QLatin1String("embed")
            || seg == QLatin1String("live"))
            return segs.at(i + 1);
    }
    return {};
}

qint64 StreamUrlResolver::extractExpireEpoch(const QUrl &url)
{
    QUrlQuery query(url);
    const QString raw = query.queryItemValue(QStringLiteral("expire"));
    if (raw.isEmpty())
        return kNoExpire;

    bool ok = false;
    const qint64 epoch = raw.toLongLong(&ok);
    if (!ok || epoch <= 0)
        return kNoExpire;
    return epoch;
}

std::optional<StreamUrlResolver::ResolvedStream>
StreamUrlResolver::getStreamUrl(const QUrl &youtubeUrl, QString *err)
{
    if (!isYouTubeUrl(youtubeUrl)) {
        if (err) *err = QStringLiteral("URL bukan YouTube");
        return std::nullopt;
    }
    const QString videoId = extractVideoId(youtubeUrl);
    if (videoId.isEmpty()) {
        if (err) *err = QStringLiteral("videoId tidak ketemu di URL");
        return std::nullopt;
    }

    if (auto hit = lookupCache(videoId))
        return hit;

    const QString url = resolveViaYtDlp(youtubeUrl, err);
    if (url.isEmpty())
        return std::nullopt;

    ResolvedStream item;
    item.videoId = videoId;
    item.url = url;
    const qint64 now = QDateTime::currentMSecsSinceEpoch();
    item.resolvedAtMs = now;

    const QUrl direct(url);
    const qint64 expireSec = extractExpireEpoch(direct);
    item.expiresAtMs = expireSec > 0 ? expireSec * 1000 : now + kDefaultTtlMs;

    if (item.expiresAtMs <= now) {
        if (err) *err = QStringLiteral("URL hasil resolve sudah expired (expire=%1)").arg(expireSec);
        return std::nullopt;
    }

    storeToCache(item);
    return item;
}

std::optional<StreamUrlResolver::ResolvedStream>
StreamUrlResolver::lookupCache(const QString &videoId)
{
    if (!m_db.isOpen())
        return std::nullopt;

    QSqlQuery q(m_db);
    q.prepare(QStringLiteral(
        "SELECT url, resolvedAtMs, expiresAtMs FROM cached_stream_urls WHERE video_id = :id"));
    q.bindValue(QStringLiteral(":id"), videoId);
    if (!q.exec() || !q.next())
        return std::nullopt;

    ResolvedStream item;
    item.videoId = videoId;
    item.url = q.value(0).toString();
    item.resolvedAtMs = q.value(1).toLongLong();
    item.expiresAtMs = q.value(2).toLongLong();

    const qint64 now = QDateTime::currentMSecsSinceEpoch();
    if (now >= item.expiresAtMs) {
        QSqlQuery del(m_db);
        del.prepare(QStringLiteral("DELETE FROM cached_stream_urls WHERE video_id = :id"));
        del.bindValue(QStringLiteral(":id"), videoId);
        del.exec();
        return std::nullopt;
    }
    return item;
}

void StreamUrlResolver::storeToCache(const ResolvedStream &item)
{
    if (!m_db.isOpen())
        return;

    QSqlQuery q(m_db);
    q.prepare(QStringLiteral(
        "INSERT OR REPLACE INTO cached_stream_urls"
        " (video_id, url, resolvedAtMs, expiresAtMs)"
        " VALUES (:id, :url, :resolved, :expires)"));
    q.bindValue(QStringLiteral(":id"), item.videoId);
    q.bindValue(QStringLiteral(":url"), item.url);
    q.bindValue(QStringLiteral(":resolved"), item.resolvedAtMs);
    q.bindValue(QStringLiteral(":expires"), item.expiresAtMs);
    q.exec();
}

QString StreamUrlResolver::ytDlpExecutable() const
{
    const QString exeDir = QCoreApplication::applicationDirPath();
    return ytDlpCandidates(exeDir);
}

QString StreamUrlResolver::resolveViaYtDlp(const QUrl &youtubeUrl, QString *err)
{
    QProcess proc;
    proc.setProgram(ytDlpExecutable());
    // Pitfall docs: setProgram + setArguments (jangan gabung string; aman utk spasi)
    proc.setArguments({QStringLiteral("-J"), QStringLiteral("--no-download"),
                       youtubeUrl.toString(QUrl::FullyEncoded)});

    proc.start();
    if (!proc.waitForStarted(5000)) {
        if (err) *err = QStringLiteral("yt-dlp tidak bisa dijalankan di %1").arg(proc.program());
        return {};
    }
    if (!proc.waitForFinished(30000)) { // -J bisa beberapa detik utk stream music
        proc.kill();
        if (err) *err = QStringLiteral("yt-dlp timeout (30 s)");
        return {};
    }
    if (proc.exitStatus() != QProcess::NormalExit || proc.exitCode() != 0) {
        const QString tail = QString::fromUtf8(proc.readAllStandardError()).right(400);
        if (err) *err = QStringLiteral("yt-dlp exit %1: %2").arg(proc.exitCode()).arg(tail);
        return {};
    }

    QJsonParseError parseErr{};
    const QJsonDocument doc = QJsonDocument::fromJson(proc.readAllStandardOutput(), &parseErr);
    if (parseErr.error != QJsonParseError::NoError || !doc.isObject()) {
        if (err) *err = QStringLiteral("JSON yt-dlp tidak valid: %1").arg(parseErr.errorString());
        return {};
    }

    // Pilih format audio-only terbaik (vcodec=="none" dan acodec!="none")
    // berdasarkan abr/tbr tertinggi.
    const QJsonArray formats = doc.object().value(QLatin1String("formats")).toArray();
    double bestBitrate = -1.0;
    QString bestUrl;
    for (const QJsonValue &v : formats) {
        const QJsonObject f = v.toObject();
        const QString url = f.value(QLatin1String("url")).toString();
        const QString vcodec = f.value(QLatin1String("vcodec")).toString();
        const QString acodec = f.value(QLatin1String("acodec")).toString();
        if (url.isEmpty() || vcodec != QLatin1String("none") || acodec == QLatin1String("none"))
            continue;
        double bitrate = f.value(QLatin1String("abr")).toDouble();
        if (bitrate <= 0)
            bitrate = f.value(QLatin1String("tbr")).toDouble();
        if (bitrate > bestBitrate) {
            bestBitrate = bitrate;
            bestUrl = url;
        }
    }
    if (bestUrl.isEmpty()) {
        // Fallback: single-format manifest di root object
        bestUrl = doc.object().value(QLatin1String("url")).toString();
    }
    if (bestUrl.isEmpty()) {
        if (err) *err = QStringLiteral("tidak ada format audio di hasil yt-dlp");
        return {};
    }
    return bestUrl;
}
