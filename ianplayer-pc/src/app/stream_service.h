#pragma once

#include <QObject>
#include <QString>
#include <QMap>
#include "models/stream_song_model.h"
#include "net/innertube_client.h"
#include "net/tidal_repository.h"

class QueueManager;
class StreamUrlResolver;

class StreamService : public QObject
{
    Q_OBJECT
    Q_PROPERTY(StreamSongModel *searchModel READ searchModel CONSTANT)
    Q_PROPERTY(StreamSongModel *browseModel READ browseModel CONSTANT)
    Q_PROPERTY(StreamSongModel *relatedModel READ relatedModel CONSTANT)

public:
    explicit StreamService(StreamUrlResolver *resolver, QueueManager *queue, QObject *parent = nullptr);

    StreamSongModel *searchModel() { return &m_searchModel; }
    StreamSongModel *browseModel() { return &m_browseModel; }
    StreamSongModel *relatedModel() { return &m_relatedModel; }

    Q_INVOKABLE void search(const QString &query);
    Q_INVOKABLE void browseHome();
    Q_INVOKABLE void fetchRelated(const QString &videoId);
    Q_INVOKABLE void playStream(const QString &videoId);

    // metadata cache for QueueManager resolver (key "yt:<videoId>")
    bool metadataFor(const QString &key, StreamSong &out) const;

private:
    void mergeInto(StreamSongModel &model, const QVector<StreamSong> &songs);

    StreamUrlResolver *m_resolver = nullptr;
    QueueManager *m_queue = nullptr;
    InnertubeClient m_innertube;
    TidalRepository m_tidal;

    StreamSongModel m_searchModel;
    StreamSongModel m_browseModel;
    StreamSongModel m_relatedModel;

    // pending search fan-out
    int m_searchPending = 0;
    QVector<StreamSong> m_searchAccum;

    QMap<QString, StreamSong> m_metadata;
};
