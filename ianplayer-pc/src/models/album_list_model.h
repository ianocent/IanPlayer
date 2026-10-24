#pragma once

#include <QAbstractListModel>
#include <QVector>

#include "db/library_database.h"

// Model daftar albums untuk grid UI (M1.2). Snapshot dumb, reload via main.cpp
// saat signal libraryChanged — sama pola SongsModel.
class AlbumListModel : public QAbstractListModel
{
    Q_OBJECT
    Q_PROPERTY(int count READ count NOTIFY countChanged)

public:
    enum Roles {
        AlbumIdRole = Qt::UserRole + 1,
        NameRole,
        ArtistRole,
        Art256Role,
        SongCountRole,
    };

    explicit AlbumListModel(LibraryDatabase *db, QObject *parent = nullptr);

    int rowCount(const QModelIndex &parent = {}) const override;
    QVariant data(const QModelIndex &index, int role) const override;
    QHash<int, QByteArray> roleNames() const override;

    int count() const { return int(m_albums.size()); }

    Q_INVOKABLE void reload();
    Q_INVOKABLE void reset();

signals:
    void countChanged();

private:
    LibraryDatabase *m_db;
    QVector<LibraryDatabase::AlbumRow> m_albums;
};
