#include "models/stream_song_model.h"

StreamSongModel::StreamSongModel(QObject *parent) : QAbstractListModel(parent) {}

int StreamSongModel::rowCount(const QModelIndex &parent) const
{
    return parent.isValid() ? 0 : m_songs.size();
}

QHash<int, QByteArray> StreamSongModel::roleNames() const
{
    return {
        { TitleRole, "title" },
        { ArtistRole, "artist" },
        { AlbumRole, "album" },
        { DurationMsRole, "durationMs" },
        { VideoIdRole, "videoId" },
        { ThumbnailUrlRole, "thumbnailUrl" },
        { SourceRole, "source" },
    };
}

QVariant StreamSongModel::data(const QModelIndex &index, int role) const
{
    if (!index.isValid() || index.row() < 0 || index.row() >= m_songs.size())
        return {};
    const StreamSong &s = m_songs.at(index.row());
    switch (role) {
    case TitleRole: return s.title;
    case ArtistRole: return s.artist;
    case AlbumRole: return s.album;
    case DurationMsRole: return s.durationMs;
    case VideoIdRole: return s.videoId;
    case ThumbnailUrlRole: return s.thumbnailUrl;
    case SourceRole: return s.source;
    default: return {};
    }
}

void StreamSongModel::setSongs(const QVector<StreamSong> &songs)
{
    beginResetModel();
    m_songs = songs;
    endResetModel();
    emit countChanged();
}

void StreamSongModel::append(const QVector<StreamSong> &songs)
{
    if (songs.isEmpty())
        return;
    beginInsertRows({}, m_songs.size(), m_songs.size() + songs.size() - 1);
    m_songs += songs;
    endInsertRows();
    emit countChanged();
}

void StreamSongModel::clear()
{
    beginResetModel();
    m_songs.clear();
    endResetModel();
    emit countChanged();
}