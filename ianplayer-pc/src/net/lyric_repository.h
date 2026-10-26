#pragma once

#include <QObject>
#include <QString>
#include <QList>
#include <QNetworkAccessManager>

struct LyricLine
{
    qint64 timeMs = 0;
    QString text;
};

enum class LyricSource {
    LrclibGet, LrclibSearch, LrcMux, Genius, Musixmatch,
    LrclibPlain, SomeRandomApi, LyricsOvh, Unknown
};

// Result
struct LyricResult
{
    enum Type { Synced, Plain, None } type = None;
    QList<LyricLine> syncedLines;
    QString plainText;
    LyricSource source = LyricSource::Unknown;
};

class LibraryDatabase;

class LyricRepository : public QObject
{
    Q_OBJECT
public:
    static const QList<LyricSource> &cycleSources();

    explicit LyricRepository(LibraryDatabase *db, QObject *parent = nullptr);

    // async
    void fetchLyric(const QString &title, const QString &artist);

signals:
    void lyricReady(const LyricResult &result);

private:
    void tryLrclibGet(const QString &title, const QString &artist);
    void tryLrclibSearch(const QString &title, const QString &artist);
    void parseLrc(const QString &lrc, QList<LyricLine> &out);
    QString cacheKey(const QString &title, const QString &artist) const;

    LibraryDatabase *m_db = nullptr;
    QNetworkAccessManager m_nam;
    LyricResult m_result;
    QString m_title;
    QString m_artist;
};
