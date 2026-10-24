#pragma once

#include <QSortFilterProxyModel>

#include "models/library_song_model.h"

// Proxy per library M1.2: sort 5 mode (paritas sort_mode Android),
// search filter (debounce di QML), filter album/artist detail screen.
// fast scroller A-Z: indexForLetter bekerja saat SortTitle; QML sembunyikan
// rail kalau mode lain.
class LibraryProxyModel : public QSortFilterProxyModel
{
    Q_OBJECT

    // 0 title | 1 artist | 2 album | 3 recently added | 4 play count
    Q_PROPERTY(int sortMode READ sortMode WRITE setSortMode NOTIFY sortModeChanged)
    Q_PROPERTY(QString searchText READ searchText WRITE setSearchText NOTIFY searchTextChanged)
    Q_PROPERTY(qint64 albumFilter READ albumFilter WRITE setAlbumFilter NOTIFY filterChanged)
    Q_PROPERTY(qint64 artistFilter READ artistFilter WRITE setArtistFilter NOTIFY filterChanged)
    Q_PROPERTY(QString sectionProperty READ sectionProperty NOTIFY sortModeChanged)
    Q_PROPERTY(int count READ count NOTIFY countChanged)

public:
    enum class SortMode { Title = 0, Artist = 1, Album = 2, RecentlyAdded = 3, PlayCount = 4 };
    Q_ENUM(SortMode)

    explicit LibraryProxyModel(QObject *parent = nullptr);

    int count() const { return rowCount(); }

    int sortMode() const { return int(m_sortMode); }
    void setSortMode(int mode);

    QString searchText() const { return m_searchText; }
    void setSearchText(const QString &text);

    qint64 albumFilter() const { return m_albumFilter; }
    void setAlbumFilter(qint64 albumId);

    qint64 artistFilter() const { return m_artistFilter; }
    void setArtistFilter(qint64 artistId);

    // "sectionLetter" saat sort title; string kosong kalau mode lain nuju
    // (rail A-Z disembunyikan di QML)
    QString sectionProperty() const { return m_sortMode == SortMode::Title ? QStringLiteral("sectionLetter") : QString(); }

    // A-Z rail: index proxy pertama yang berawalan ch (baris terurut judul);
    // -1 kalau tidak ketemu / bukan SortTitle.
    Q_INVOKABLE int indexForLetter(int letterAscii);

    bool lessThan(const QModelIndex &left, const QModelIndex &right) const override;
    bool filterAcceptsRow(int sourceRow, const QModelIndex &sourceParent) const override;

signals:
    void sortModeChanged();
    void searchTextChanged();
    void filterChanged();
    void countChanged();

protected:
    QVariant data(const QModelIndex &index, int role) const override;
    QHash<int, QByteArray> roleNames() const override;

private:
    SortMode m_sortMode = SortMode::Title;
    QString m_searchText;
    qint64 m_albumFilter = 0;
    qint64 m_artistFilter = 0;
};
