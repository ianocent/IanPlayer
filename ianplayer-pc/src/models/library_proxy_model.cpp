#include "models/library_proxy_model.h"

#include <algorithm>

LibraryProxyModel::LibraryProxyModel(QObject *parent)
    : QSortFilterProxyModel(parent)
{
    setSortCaseSensitivity(Qt::CaseInsensitive);
    setFilterCaseSensitivity(Qt::CaseInsensitive);
}

void LibraryProxyModel::setSortMode(int mode)
{
    const SortMode next = static_cast<SortMode>(mode);
    if (m_sortMode == next)
        return;
    m_sortMode = next;
    emit sortModeChanged();
    invalidate();
}

void LibraryProxyModel::setSearchText(const QString &text)
{
    if (m_searchText == text)
        return;
    m_searchText = text;
    emit searchTextChanged();
    invalidateFilter();
    emit countChanged();
}

void LibraryProxyModel::setAlbumFilter(qint64 albumId)
{
    if (m_albumFilter == albumId)
        return;
    m_albumFilter = albumId;
    emit filterChanged();
    invalidateFilter();
}

void LibraryProxyModel::setArtistFilter(qint64 artistId)
{
    if (m_artistFilter == artistId)
        return;
    m_artistFilter = artistId;
    emit filterChanged();
    invalidateFilter();
}

bool LibraryProxyModel::lessThan(const QModelIndex &left, const QModelIndex &right) const
{
    switch (m_sortMode) {
    case SortMode::Artist: {
        const QString la = left.data(LibrarySongModel::SortArtistRole).toString();
        const QString ra = right.data(LibrarySongModel::SortArtistRole).toString();
        if (la != ra) return la < ra;
        return left.data(LibrarySongModel::SortTitleRole).toString()
             < right.data(LibrarySongModel::SortTitleRole).toString();
    }
    case SortMode::Album: {
        const QString la = left.data(LibrarySongModel::SortAlbumRole).toString();
        const QString ra = right.data(LibrarySongModel::SortAlbumRole).toString();
        if (la != ra) return la < ra;
        // dalam album: urut track #, lalu judul
        const int lt = left.data(LibrarySongModel::TrackNoRole).toInt();
        const int rt = right.data(LibrarySongModel::TrackNoRole).toInt();
        if (lt != rt) return lt < rt;
        return left.data(LibrarySongModel::SortTitleRole).toString()
             < right.data(LibrarySongModel::SortTitleRole).toString();
    }
    case SortMode::RecentlyAdded: {
        const qint64 la = left.data(LibrarySongModel::AddedTsRole).toLongLong();
        const qint64 ra = right.data(LibrarySongModel::AddedTsRole).toLongLong();
        // terbaru dulu
        if (la != ra) return la > ra;
        return left.data(LibrarySongModel::SortTitleRole).toString()
             < right.data(LibrarySongModel::SortTitleRole).toString();
    }
    case SortMode::PlayCount: {
        const int lc = left.data(LibrarySongModel::PlayCountRole).toInt();
        const int rc = right.data(LibrarySongModel::PlayCountRole).toInt();
        // terbanyak dulu
        if (lc != rc) return lc > rc;
        return left.data(LibrarySongModel::SortTitleRole).toString()
             < right.data(LibrarySongModel::SortTitleRole).toString();
    }
    case SortMode::Title:
    default: {
        const QString lt = left.data(LibrarySongModel::SortTitleRole).toString();
        const QString rt = right.data(LibrarySongModel::SortTitleRole).toString();
        if (lt != rt) return lt < rt;
        return left.data(LibrarySongModel::SortArtistRole).toString()
             < right.data(LibrarySongModel::SortArtistRole).toString();
    }
    }
}

bool LibraryProxyModel::filterAcceptsRow(int sourceRow, const QModelIndex &sourceParent) const
{
    const QModelIndex idx = sourceModel()->index(sourceRow, 0, sourceParent);

    if (m_albumFilter > 0
        && idx.data(LibrarySongModel::AlbumIdRole).toLongLong() != m_albumFilter)
        return false;
    if (m_artistFilter > 0
        && idx.data(LibrarySongModel::ArtistIdRole).toLongLong() != m_artistFilter)
        return false;

    const QString search = m_searchText.trimmed();
    if (!search.isEmpty()) {
        const QString title = idx.data(LibrarySongModel::TitleRole).toString();
        const QString artist = idx.data(LibrarySongModel::ArtistRole).toString();
        const QString album = idx.data(LibrarySongModel::AlbumRole).toString();
        if (!title.contains(search, Qt::CaseInsensitive)
            && !artist.contains(search, Qt::CaseInsensitive)
            && !album.contains(search, Qt::CaseInsensitive))
            return false;
    }

    return true;
}

QVariant LibraryProxyModel::data(const QModelIndex &index, int role) const
{
    return QSortFilterProxyModel::data(index, role);
}

QHash<int, QByteArray> LibraryProxyModel::roleNames() const
{
    return sourceModel() ? sourceModel()->roleNames() : QSortFilterProxyModel::roleNames();
}

int LibraryProxyModel::indexForLetter(int letterAscii)
{
    if (m_sortMode != SortMode::Title || rowCount() == 0)
        return -1;
    const QString letter = QString(QChar::fromLatin1(char(letterAscii)));
    for (int row = 0; row < rowCount(); ++row) {
        if (QSortFilterProxyModel::data(index(row, 0),
                LibrarySongModel::SectionLetterRole).toString() == letter)
            return row;
    }
    return -1;
}
