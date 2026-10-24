#include "models/playlist_song_model.h"

#include <algorithm>

PlaylistSongModel::PlaylistSongModel(LibraryDatabase *db, QObject *parent)
    : QAbstractListModel(parent)
    , m_db(db)
{
}

void PlaylistSongModel::setPlaylistKey(qint64 id)
{
    if (m_playlistId == id)
        return;
    m_playlistId = id;
    emit playlistKeyChanged();
    reload();
}

void PlaylistSongModel::reload()
{
    beginResetModel();
    m_songs = (m_db && m_db->isOpen() && m_playlistId > 0)
                  ? m_db->playlistSongs(m_playlistId)
                  : QVector<SongRecord>{};
    endResetModel();
    emit countChanged();
}

void PlaylistSongModel::reset()
{
    beginResetModel();
    m_songs.clear();
    endResetModel();
    emit countChanged();
}

bool PlaylistSongModel::moveSong(qint64 songId, int toPos)
{
    if (!m_db || !m_db->isOpen() || m_playlistId <= 0)
        return false;
    if (!m_db->movePlaylistSong(m_playlistId, songId, toPos))
        return false;

    // posisi lama di snapshot
    int from = -1;
    for (int i = 0; i < m_songs.size(); ++i)
        if (m_songs.at(i).id == songId) { from = i; break; }
    if (from < 0) {
        reload();
        return true;
    }

    toPos = std::clamp(toPos, 0, int(m_songs.size()) - 1);
    if (from == toPos) {
        reload();
        return true;
    }

    // Qt moveRows: arah turun perlu destRow+1 (end-exclusive illusion)
    const int destRow = toPos > from ? toPos + 1 : toPos;
    beginMoveRows({}, from, from, {}, destRow);
    const SongRecord rec = m_songs.takeAt(from);
    m_songs.insert(toPos, rec);
    endMoveRows();
    return true;
}

int PlaylistSongModel::rowCount(const QModelIndex &parent) const
{
    return parent.isValid() ? 0 : m_songs.size();
}

QHash<int, QByteArray> PlaylistSongModel::roleNames() const
{
    return {
        { SongIdRole, "songId" },
        { PathRole, "path" },
        { TitleRole, "title" },
        { ArtistRole, "artist" },
        { AlbumRole, "album" },
        { DurationMsRole, "durationMs" },
        { Art64Role, "art64" },
        { FavoriteRole, "favorite" },
        { PlayCountRole, "playCount" },
    };
}

QVariant PlaylistSongModel::data(const QModelIndex &index, int role) const
{
    if (!index.isValid() || index.row() < 0 || index.row() >= m_songs.size())
        return {};
    const SongRecord &rec = m_songs.at(index.row());
    switch (role) {
    case SongIdRole: return rec.id;
    case PathRole: return rec.path;
    case TitleRole: return rec.title;
    case ArtistRole: return rec.artist;
    case AlbumRole: return rec.album;
    case DurationMsRole: return rec.durationMs;
    case Art64Role: return rec.art64;
    case FavoriteRole:
        return false;   // filled in by refresh phase; v1 minimal di playlist
    case PlayCountRole: return 0;
    default: return {};
    }
}
