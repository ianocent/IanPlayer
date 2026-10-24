#pragma once

#include <QAbstractListModel>
#include <QVector>

#include "db/library_database.h"

// Model daftar playlist (M1.3). Snapshot dumb; reload via LibraryService signal.
class PlaylistListModel : public QAbstractListModel
{
    Q_OBJECT
    Q_PROPERTY(int count READ count NOTIFY countChanged)

public:
    enum Roles {
        PlaylistIdRole = Qt::UserRole + 1,
        NameRole,
        ImageRole,
        CreatedTsRole,
        SongCountRole,
    };

    explicit PlaylistListModel(LibraryDatabase *db, QObject *parent = nullptr);

    int rowCount(const QModelIndex &parent = {}) const override;
    QVariant data(const QModelIndex &index, int role) const override;
    QHash<int, QByteArray> roleNames() const override;

    int count() const { return int(m_playlists.size()); }

    Q_INVOKABLE void reload();
    Q_INVOKABLE void reset();

signals:
    void countChanged();

private:
    LibraryDatabase *m_db;
    QVector<LibraryDatabase::PlaylistRow> m_playlists;
};
