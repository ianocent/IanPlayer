#include "models/album_list_model.h"

AlbumListModel::AlbumListModel(LibraryDatabase *db, QObject *parent)
    : QAbstractListModel(parent)
    , m_db(db)
{
    reload();
}

void AlbumListModel::reload()
{
    if (!m_db || !m_db->isOpen()) {
        reset();
        return;
    }
    beginResetModel();
    m_albums = m_db->allAlbums();
    endResetModel();
    emit countChanged();
}

void AlbumListModel::reset()
{
    beginResetModel();
    m_albums.clear();
    endResetModel();
    emit countChanged();
}

int AlbumListModel::rowCount(const QModelIndex &parent) const
{
    return parent.isValid() ? 0 : int(m_albums.size());
}

QHash<int, QByteArray> AlbumListModel::roleNames() const
{
    return {
        { AlbumIdRole, "albumKey" },
        { NameRole, "albumName" },
        { ArtistRole, "albumArtist" },
        { Art256Role, "albumArt" },
        { SongCountRole, "albumSongCount" },
    };
}

QVariant AlbumListModel::data(const QModelIndex &index, int role) const
{
    if (!index.isValid() || index.row() < 0 || index.row() >= m_albums.size())
        return {};
    const LibraryDatabase::AlbumRow &row = m_albums.at(index.row());
    switch (role) {
    case AlbumIdRole: return row.id;
    case NameRole: return row.name;
    case ArtistRole: return row.artist;
    case Art256Role: return row.art256;
    case SongCountRole: return row.songCount;
    default: return {};
    }
}
