#pragma once

#include <QAbstractListModel>
#include <QVector>

#include "db/library_database.h"

// Model daftar artists untuk grid UI (M1.2). Snapshot dumb, pola sama AlbumListModel.
class ArtistListModel : public QAbstractListModel
{
    Q_OBJECT
    Q_PROPERTY(int count READ count NOTIFY countChanged)

public:
    enum Roles {
        ArtistIdRole = Qt::UserRole + 1,
        NameRole,
        Art256Role,
        SongCountRole,
    };

    explicit ArtistListModel(LibraryDatabase *db, QObject *parent = nullptr);

    int rowCount(const QModelIndex &parent = {}) const override;
    QVariant data(const QModelIndex &index, int role) const override;
    QHash<int, QByteArray> roleNames() const override;

    int count() const { return int(m_artists.size()); }

    Q_INVOKABLE void reload();
    Q_INVOKABLE void reset();

signals:
    void countChanged();

private:
    LibraryDatabase *m_db;
    QVector<LibraryDatabase::ArtistRow> m_artists;
};
