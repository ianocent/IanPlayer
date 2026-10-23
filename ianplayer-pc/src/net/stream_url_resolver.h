#pragma once

#include <QString>
#include <QUrl>
#include <QSqlDatabase>
#include <QDateTime>

#include <optional>

// Resolusi URL streaming (M0.3 scope): YouTube URL -> direct googlevideo URL via
// yt-dlp.exe (QProcess), dengan cache SQLite tabel `cached_stream_urls`
// (schema 02-ARCHITECTURE.md: video_id, url, resolvedAtMs, expiresAtMs).
//
// Pure helper (static, tanpa side effect) dipisah biar ke-test gtest.
class StreamUrlResolver
{
public:
    struct ResolvedStream
    {
        QString videoId;
        QString url;
        qint64 resolvedAtMs = 0;
        qint64 expiresAtMs = 0;
    };

    explicit StreamUrlResolver(QString dbPath = QString());

    StreamUrlResolver(const StreamUrlResolver &) = delete;
    StreamUrlResolver &operator=(const StreamUrlResolver &) = delete;

    // ---- pure helper (gtest) ----
    static bool isYouTubeUrl(const QUrl &url);
    static QString extractVideoId(const QUrl &url);
    // "expire" query param googlevideo = epoch UNIX dalam DETIK. -1 kalau tidak
    // ada/tidak bisa diparse.
    static qint64 extractExpireEpoch(const QUrl &url);

    // ---- dengan cache & yt-dlp ----
    // Main entry: videoId -> cache hit (belum expired) -> pakai;
    //             miss/expired -> yt-dlp -J --no-download -> best audio URL ->
    //             simpan cache.
    // `err` (opsional) diisi pesan gagal.
    std::optional<ResolvedStream> getStreamUrl(const QUrl &youtubeUrl, QString *err = nullptr);

private:
    std::optional<ResolvedStream> lookupCache(const QString &videoId);
    void storeToCache(const ResolvedStream &item);
    QString resolveViaYtDlp(const QUrl &youtubeUrl, QString *err);

    QString ytDlpExecutable() const;

    QSqlDatabase m_db;
};
