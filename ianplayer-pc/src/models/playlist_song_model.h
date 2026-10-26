#pragma once

#include <QAbstractListModel>
#include <QVector>

#include "db/library_database.h"

// Isi satu playlist (M1.3): lagu urut position. Dukungan drag reorder live
// via moveSong() — DB di-rewrite dan model men-emit move agar ListView
// animasi tanpa reset (pitfall M1.2: jangan reset saat drag).
class PlaylistSongModel : public QAbstractListModel
{
    Q_OBJECT

    // playlist aktif; ganti -> reload otomatis
    Q_PROPERTY(qint64 playlistKey READ playlistKey WRITE setPlaylistKey NOTIFY playlistKeyChanged)
    Q_PROPERTY(int count READ count NOTIFY countChanged)

public:
    enum Roles {
        SongIdRole = Qt::UserRole + 1,
        PathRole,
        TitleRole,
        ArtistRole,
        AlbumRole,
        DurationMsRole,
        Art64Role,
        FavoriteRole,
        PlayCountRole,
    };

    explicit PlaylistSongModel(LibraryDatabase *db, QObject *parent = nullptr);

    qint64 playlistKey() const { return m_playlistId; }
    void setPlaylistKey(qint64 id);

    int rowCount(const QModelIndex &parent = {}) const override;
    QVariant data(const QModelIndex &index, int role) const override;
    QHash<int, QByteArray> roleNames() const override;

    int count() const { return int(m_songs.size()); }

    Q_INVOKABLE void reload();
    Q_INVOKABLE void reset();

    // DnD reorder live: DB rewrite + rowsMoved signal
    Q_INVOKABLE bool moveSong(qint64 songId, int toPos);

signals:
    void playlistKeyChanged();
    void countChanged();

private:
    LibraryDatabase *m_db;
    QVector<SongRecord> m_songs;
    qint64 m_playlistId = 0;
};
