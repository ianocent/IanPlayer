#include "net/lyric_repository.h"

#include "db/library_database.h"

#include <QNetworkRequest>
#include <QNetworkReply>
#include <QUrl>
#include <QUrlQuery>
#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonArray>
#include <QRegularExpression>
#include <QDateTime>

namespace {
constexpr qint64 kTtlMs = 7LL * 24 * 60 * 60 * 1000;

QList<LyricSource> kCycle = {
    LyricSource::LrclibGet, LyricSource::LrclibSearch, LyricSource::LrcMux,
    LyricSource::Genius, LyricSource::Musixmatch, LyricSource::LrclibPlain,
    LyricSource::SomeRandomApi, LyricSource::LyricsOvh
};

QString urlEncode(const QString &s) { return QUrl::toPercentEncoding(s); }

} // namespace

const QList<LyricSource> &LyricRepository::cycleSources()
{
    return kCycle;
}

LyricRepository::LyricRepository(LibraryDatabase *db, QObject *parent)
    : QObject(parent), m_db(db) {}

QString LyricRepository::cacheKey(const QString &title, const QString &artist) const
{
    return (artist.trimmed().toLower() + QStringLiteral("|") + title.trimmed().toLower());
}

void LyricRepository::parseLrc(const QString &lrc, QList<LyricLine> &out)
{
    static QRegularExpression re(QStringLiteral("\\[(\\d+):(\\d+(?:\\.\\d+)?)\\](.*)"));
    const QStringList lines = lrc.split('\n');
    for (const QString &line : lines) {
        auto m = re.match(line.trimmed());
        if (!m.hasMatch())
            continue;
        const int mm = m.captured(1).toInt();
        const double ss = m.captured(2).toDouble();
        const qint64 ms = (mm * 60 + (int)ss) * 1000 + (qint64)((ss - (int)ss) * 1000);
        LyricLine ll; ll.timeMs = ms; ll.text = m.captured(3).trimmed();
        if (!ll.text.isEmpty())
            out.append(ll);
    }
}

void LyricRepository::fetchLyric(const QString &title, const QString &artist)
{
    m_title = title;
    m_artist = artist;
    m_result = LyricResult{};

    const QString key = cacheKey(title, artist);
    // cache check
    if (m_db) {
        if (auto entry = m_db->lyricCacheEntry(key)) {
            const bool expired = (QDateTime::currentMSecsSinceEpoch() - entry->cachedAtMs) > kTtlMs;
            if (!expired) {
                if (!entry->syncedJson.isEmpty() && entry->syncedJson != QStringLiteral("NONE")) {
                    QList<LyricLine> lines;
                    parseLrc(entry->syncedJson, lines);
                    if (!lines.isEmpty()) {
                        LyricResult r; r.type = LyricResult::Synced; r.syncedLines = lines; r.source = LyricSource::Unknown;
                        emit lyricReady(r);
                        return;
                    }
                }
                if (!entry->plainText.isEmpty()) {
                    LyricResult r; r.type = LyricResult::Plain; r.plainText = entry->plainText; r.source = LyricSource::Unknown;
                    emit lyricReady(r);
                    return;
                }
                if (entry->syncedJson == QStringLiteral("NONE") && entry->plainText.isEmpty()) {
                    LyricResult r; r.type = LyricResult::None;
                    emit lyricReady(r);
                    return;
                }
            }
        }
    }

    tryLrclibGet(title, artist);
}

void LyricRepository::tryLrclibGet(const QString &title, const QString &artist)
{
    QUrl url(QStringLiteral("https://lrclib.net/api/get"));
    QUrlQuery q;
    q.addQueryItem(QStringLiteral("artist_name"), artist);
    q.addQueryItem(QStringLiteral("track_name"), title);
    url.setQuery(q);

    QNetworkRequest req(url);
    QNetworkReply *reply = m_nam.get(req);
    QObject::connect(reply, &QNetworkReply::finished, this, [this, reply, title, artist]() {
        if (reply->error() == QNetworkReply::NoError) {
            QJsonParseError pe{};
            const QJsonDocument doc = QJsonDocument::fromJson(reply->readAll(), &pe);
            if (pe.error == QJsonParseError::NoError && doc.isObject()) {
                const QJsonObject o = doc.object();
                const QString synced = o.value(QStringLiteral("syncedLyrics")).toString();
                const QString plain = o.value(QStringLiteral("plainLyrics")).toString();
                if (!synced.isEmpty()) {
                    QList<LyricLine> lines;
                    parseLrc(synced, lines);
                    if (!lines.isEmpty()) {
                        if (m_db)
                            m_db->saveLyricCache(cacheKey(title, artist), synced, plain, QDateTime::currentMSecsSinceEpoch());
                        LyricResult r; r.type = LyricResult::Synced; r.syncedLines = lines; r.source = LyricSource::LrclibGet;
                        emit lyricReady(r);
                        reply->deleteLater();
                        return;
                    }
                }
                if (!plain.isEmpty()) {
                    if (m_db)
                        m_db->saveLyricCache(cacheKey(title, artist), QStringLiteral("NONE"), plain, QDateTime::currentMSecsSinceEpoch());
                    LyricResult r; r.type = LyricResult::Plain; r.plainText = plain; r.source = LyricSource::LrclibGet;
                    emit lyricReady(r);
                    reply->deleteLater();
                    return;
                }
            }
        }
        reply->deleteLater();
        tryLrclibSearch(title, artist);
    });
}

void LyricRepository::tryLrclibSearch(const QString &title, const QString &artist)
{
    QUrl url(QStringLiteral("https://lrclib.net/api/search"));
    QUrlQuery q;
    q.addQueryItem(QStringLiteral("artist_name"), artist);
    q.addQueryItem(QStringLiteral("track_name"), title);
    url.setQuery(q);

    QNetworkRequest req(url);
    QNetworkReply *reply = m_nam.get(req);
    QObject::connect(reply, &QNetworkReply::finished, this, [this, reply, title, artist]() {
        bool found = false;
        if (reply->error() == QNetworkReply::NoError) {
            QJsonParseError pe{};
            const QJsonDocument doc = QJsonDocument::fromJson(reply->readAll(), &pe);
            if (pe.error == QJsonParseError::NoError && doc.isArray()) {
                const QJsonArray arr = doc.array();
                for (const QJsonValue &v : arr) {
                    const QJsonObject o = v.toObject();
                    const QString synced = o.value(QStringLiteral("syncedLyrics")).toString();
                    const QString plain = o.value(QStringLiteral("plainLyrics")).toString();
                    if (!synced.isEmpty()) {
                        QList<LyricLine> lines;
                        parseLrc(synced, lines);
                        if (!lines.isEmpty()) {
                            if (m_db)
                                m_db->saveLyricCache(cacheKey(title, artist), synced, plain, QDateTime::currentMSecsSinceEpoch());
                            LyricResult r; r.type = LyricResult::Synced; r.syncedLines = lines; r.source = LyricSource::LrclibSearch;
                            emit lyricReady(r);
                            found = true;
                            break;
                        }
                    }
                    if (!plain.isEmpty() && !found) {
                        if (m_db)
                            m_db->saveLyricCache(cacheKey(title, artist), QStringLiteral("NONE"), plain, QDateTime::currentMSecsSinceEpoch());
                        LyricResult r; r.type = LyricResult::Plain; r.plainText = plain; r.source = LyricSource::LrclibSearch;
                        emit lyricReady(r);
                        found = true;
                        break;
                    }
                }
            }
        }
        reply->deleteLater();
        if (!found) {
            if (m_db)
                m_db->saveLyricCache(cacheKey(title, artist), QStringLiteral("NONE"), QString(), QDateTime::currentMSecsSinceEpoch());
            LyricResult r; r.type = LyricResult::None;
            emit lyricReady(r);
        }
    });
}
