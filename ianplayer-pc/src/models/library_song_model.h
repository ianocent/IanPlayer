#pragma once

#include <QAbstractListModel>
#include <QVector>
#include <QHash>
#include <QSet>

#include "db/library_database.h"

// Model lagu untuk UI library (M1.2). Dumb snapshot dari LibraryDatabase:
// reload() dipanggil main.cpp tiap signal libraryChanged/favoritesChanged.
// Semua akses DB thread-main; sort/search/detail di delegated ke ProxyModel.
class LibrarySongModel : public QAbstractListModel
{
    Q_OBJECT
    Q_PROPERTY(int count READ count NOTIFY countChanged)

public:
    explicit LibrarySongModel(LibraryDatabase *db, QObject *parent = nullptr);

    enum Roles {
        IdRole = Qt::UserRole + 1,
        PathRole,
        TitleRole,
        ArtistRole,
        AlbumRole,
        DurationMsRole,
        BitrateRole,
        CodecRole,
        Art64Role,
        Art256Role,
        TrackNoRole,
        YearRole,
        AddedTsRole,
        AlbumIdRole,
        ArtistIdRole,
        FavoriteRole,
        PlayCountRole,
        SectionLetterRole,  // huruf pertama judul, "A".."Z" | "#" | "%" (non-huruf)
        SortTitleRole,       // judul lowercase biar sort case-insensitive
        SortArtistRole,
        SortAlbumRole,
    };

    int rowCount(const QModelIndex &parent = {}) const override;
    QVariant data(const QModelIndex &index, int role) const override;
    QHash<int, QByteArray> roleNames() const override;

    int count() const { return int(m_songs.size()); }

    Q_INVOKABLE void reload();
    Q_INVOKABLE void reset();

signals:
    void countChanged();

private:
    LibraryDatabase *m_db;
    QVector<SongRecord> m_songs;
    QSet<QString> m_favorites;
    QHash<QString, int> m_playCounts;
};
