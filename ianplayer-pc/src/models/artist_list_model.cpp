#include "models/artist_list_model.h"

ArtistListModel::ArtistListModel(LibraryDatabase *db, QObject *parent)
    : QAbstractListModel(parent)
    , m_db(db)
{
    reload();
}

void ArtistListModel::reload()
{
    if (!m_db || !m_db->isOpen()) {
        reset();
        return;
    }
    beginResetModel();
    m_artists = m_db->allArtists();
    endResetModel();
    emit countChanged();
}

void ArtistListModel::reset()
{
    beginResetModel();
    m_artists.clear();
    endResetModel();
    emit countChanged();
}

int ArtistListModel::rowCount(const QModelIndex &parent) const
{
    return parent.isValid() ? 0 : int(m_artists.size());
}

QHash<int, QByteArray> ArtistListModel::roleNames() const
{
    return {
        { ArtistIdRole, "artistKey" },
        { NameRole, "artistName" },
        { Art256Role, "artistArt" },
        { SongCountRole, "artistSongCount" },
    };
}

QVariant ArtistListModel::data(const QModelIndex &index, int role) const
{
    if (!index.isValid() || index.row() < 0 || index.row() >= m_artists.size())
        return {};
    const LibraryDatabase::ArtistRow &row = m_artists.at(index.row());
    switch (role) {
    case ArtistIdRole: return row.id;
    case NameRole: return row.name;
    case Art256Role: return row.art256;
    case SongCountRole: return row.songCount;
    default: return {};
    }
}
