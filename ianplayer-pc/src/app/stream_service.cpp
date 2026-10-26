#include "app/stream_service.h"

#include "playback/queue_manager.h"
#include "net/stream_url_resolver.h"

#include <QSet>

StreamService::StreamService(StreamUrlResolver *resolver, QueueManager *queue, QObject *parent)
    : QObject(parent)
    , m_resolver(resolver)
    , m_queue(queue)
{
    QObject::connect(&m_innertube, &InnertubeClient::searchFinished, this,
                     [this](const QList<InnertubeSong> &songs, const QString &err) {
        QVector<StreamSong> out;
        for (const auto &s : songs) {
            StreamSong ss;
            ss.title = s.title;
            ss.artist = s.artist;
            ss.album = s.album;
            ss.durationMs = s.durationMs;
            ss.videoId = s.videoId;
            ss.thumbnailUrl = s.thumbnailUrl;
            ss.source = QStringLiteral("yt");
            out.append(ss);
            m_metadata.insert(QStringLiteral("yt:") + s.videoId, ss);
        }
        m_searchAccum += out;
        if (--m_searchPending <= 0) {
            m_searchModel.setSongs(m_searchAccum);
            m_searchAccum.clear();
        }
        Q_UNUSED(err);
    });

    QObject::connect(&m_tidal, &TidalRepository::searchFinished, this,
                     [this](const QList<TidalTrack> &tracks, const QString &err) {
        QVector<StreamSong> out;
        for (const auto &t : tracks) {
            StreamSong ss;
            ss.title = t.title;
            ss.artist = t.artist;
            ss.album = t.album;
            ss.durationMs = t.durationMs;
            ss.videoId = QString::number(t.id);
            ss.thumbnailUrl = t.artUrl;
            ss.source = QStringLiteral("tidal");
            out.append(ss);
        }
        m_searchAccum += out;
        if (--m_searchPending <= 0) {
            m_searchModel.setSongs(m_searchAccum);
            m_searchAccum.clear();
        }
        Q_UNUSED(err);
    });

    QObject::connect(&m_innertube, &InnertubeClient::browseFinished, this,
                     [this](const QList<InnertubeSong> &songs, const QString &) {
        QVector<StreamSong> out;
        for (const auto &s : songs) {
            StreamSong ss;
            ss.title = s.title;
            ss.artist = s.artist;
            ss.album = s.album;
            ss.durationMs = s.durationMs;
            ss.videoId = s.videoId;
            ss.thumbnailUrl = s.thumbnailUrl;
            ss.source = QStringLiteral("yt");
            out.append(ss);
            m_metadata.insert(QStringLiteral("yt:") + s.videoId, ss);
        }
        m_browseModel.setSongs(out);
    });

    QObject::connect(&m_innertube, &InnertubeClient::relatedFinished, this,
                     [this](const QList<InnertubeSong> &songs, const QString &) {
        QVector<StreamSong> out;
        for (const auto &s : songs) {
            StreamSong ss;
            ss.title = s.title;
            ss.artist = s.artist;
            ss.album = s.album;
            ss.durationMs = s.durationMs;
            ss.videoId = s.videoId;
            ss.thumbnailUrl = s.thumbnailUrl;
            ss.source = QStringLiteral("yt");
            out.append(ss);
            m_metadata.insert(QStringLiteral("yt:") + s.videoId, ss);
        }
        m_relatedModel.setSongs(out);
    });
}

void StreamService::search(const QString &query)
{
    m_searchModel.clear();
    m_searchAccum.clear();
    m_searchPending = 2;
    m_innertube.search(query, 20);
    m_tidal.searchSongs(query);
}

void StreamService::browseHome()
{
    m_browseModel.clear();
    m_innertube.browseHome();
}

void StreamService::fetchRelated(const QString &videoId)
{
    m_relatedModel.clear();
    m_innertube.fetchRelated(videoId);
}

void StreamService::playStream(const QString &videoId)
{
    if (!m_queue)
        return;
    m_queue->playSingle(QStringLiteral("yt:") + videoId);
}

bool StreamService::metadataFor(const QString &key, StreamSong &out) const
{
    auto it = m_metadata.find(key);
    if (it == m_metadata.end())
        return false;
    out = it.value();
    return true;
}
