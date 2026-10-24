#include "models/library_song_model.h"

#include <QCollator>

namespace {

QString sectionLetterOf(const QString &title)
{
    if (title.isEmpty())
        return QStringLiteral("#");
    const QChar c = title.at(0);
    const QString upper = c.toUpper() == c ? QString(c) : QString(c.toUpper());
    if (upper.at(0).isLetter())
        return upper;
    return QStringLiteral("#");
}

} // namespace

LibrarySongModel::LibrarySongModel(LibraryDatabase *db, QObject *parent)
    : QAbstractListModel(parent)
    , m_db(db)
{
    reload();
}

void LibrarySongModel::reload()
{
    if (!m_db || !m_db->isOpen()) {
        reset();
        return;
    }
    beginResetModel();
    m_songs = m_db->allSongs();
    m_favorites = m_db->favoriteKeys();
    m_playCounts = m_db->allPlayCounts();
    endResetModel();
    emit countChanged();
}

void LibrarySongModel::reset()
{
    beginResetModel();
    m_songs.clear();
    m_favorites.clear();
    m_playCounts.clear();
    endResetModel();
    emit countChanged();
}

int LibrarySongModel::rowCount(const QModelIndex &parent) const
{
    return parent.isValid() ? 0 : int(m_songs.size());
}

QHash<int, QByteArray> LibrarySongModel::roleNames() const
{
    QHash<int, QByteArray> roles;
    roles[IdRole] = "songId";
    roles[PathRole] = "path";
    roles[TitleRole] = "title";
    roles[ArtistRole] = "artist";
    roles[AlbumRole] = "album";
    roles[DurationMsRole] = "durationMs";
    roles[BitrateRole] = "bitrate";
    roles[CodecRole] = "codec";
    roles[Art64Role] = "art64";
    roles[Art256Role] = "art256";
    roles[TrackNoRole] = "trackNo";
    roles[YearRole] = "year";
    roles[AddedTsRole] = "addedTs";
    roles[AlbumIdRole] = "albumId";
    roles[ArtistIdRole] = "artistId";
    roles[FavoriteRole] = "favorite";
    roles[PlayCountRole] = "playCount";
    roles[SectionLetterRole] = "sectionLetter";
    roles[SortTitleRole] = "sortTitle";
    roles[SortArtistRole] = "sortArtist";
    roles[SortAlbumRole] = "sortAlbum";
    return roles;
}

QVariant LibrarySongModel::data(const QModelIndex &index, int role) const
{
    if (!index.isValid() || index.row() < 0 || index.row() >= m_songs.size())
        return {};

    const SongRecord &rec = m_songs.at(index.row());
    switch (role) {
    case IdRole: return rec.id;
    case PathRole: return rec.path;
    case TitleRole: return rec.title;
    case ArtistRole: return rec.artist;
    case AlbumRole: return rec.album;
    case DurationMsRole: return rec.durationMs;
    case BitrateRole: return rec.bitrate;
    case CodecRole: return rec.codec;
    case Art64Role: return rec.art64;
    case Art256Role: return rec.art256;
    case TrackNoRole: return rec.trackNo;
    case YearRole: return rec.year;
    case AddedTsRole: return rec.addedTs;
    case AlbumIdRole: return rec.albumId;
    case ArtistIdRole: return rec.artistId;
    case FavoriteRole:
        return m_favorites.contains(QStringLiteral("local:%1").arg(rec.id));
    case PlayCountRole:
        return m_playCounts.value(
            QStringLiteral("local:%1").arg(rec.id), 0);
    case SectionLetterRole:
        return sectionLetterOf(rec.title);
    case SortTitleRole:
        return rec.title.toCaseFolded();
    case SortArtistRole:
        return rec.artist.toCaseFolded();
    case SortAlbumRole:
        return rec.album.toCaseFolded();
    default:
        return {};
    }
}
