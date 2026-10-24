#include "models/playlist_list_model.h"

PlaylistListModel::PlaylistListModel(LibraryDatabase *db, QObject *parent)
    : QAbstractListModel(parent)
    , m_db(db)
{
    reload();
}

void PlaylistListModel::reload()
{
    if (!m_db || !m_db->isOpen()) {
        reset();
        return;
    }
    beginResetModel();
    m_playlists = m_db->allPlaylists();
    endResetModel();
    emit countChanged();
}

void PlaylistListModel::reset()
{
    beginResetModel();
    m_playlists.clear();
    endResetModel();
    emit countChanged();
}

int PlaylistListModel::rowCount(const QModelIndex &parent) const
{
    return parent.isValid() ? 0 : int(m_playlists.size());
}

QHash<int, QByteArray> PlaylistListModel::roleNames() const
{
    return {
        { PlaylistIdRole, "playlistKey" },
        { NameRole, "playlistName" },
        { ImageRole, "playlistImage" },
        { CreatedTsRole, "playlistCreatedTs" },
        { SongCountRole, "playlistSongCount" },
    };
}

QVariant PlaylistListModel::data(const QModelIndex &index, int role) const
{
    if (!index.isValid() || index.row() < 0 || index.row() >= m_playlists.size())
        return {};
    const LibraryDatabase::PlaylistRow &row = m_playlists.at(index.row());
    switch (role) {
    case PlaylistIdRole: return row.id;
    case NameRole: return row.name;
    case ImageRole: return row.image;
    case CreatedTsRole: return row.createdTs;
    case SongCountRole: return row.songCount;
    default: return {};
    }
}
