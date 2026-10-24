#pragma once

#include <QString>
#include <QStringList>
#include <QVector>
#include <QHash>
#include <QSet>
#include <QPair>

#include <optional>
#include <QtCore/qmetatype.h>

// Struktur data + akses DB library lokal (M1.1).
// Schema per 02-ARCHITECTURE.md bagian SQLite (pemetaan Room/SongStore Android).
struct SongRecord
{
    qint64 id = 0;
    QString path;
    QString title;
    QString artist;
    qint64 artistId = 0;
    QString album;
    qint64 albumId = 0;
    int trackNo = 0;
    int year = 0;
    qint64 durationMs = 0;
    int bitrate = 0;
    QString codec;          // mp3 | flac | m4a | aac | wav | ogg | opus
    QString art256;         // path thumbnail cache (kosong kalau tidak ada)
    QString art64;          // path thumbnail cache (kosong kalau tidak ada)
    qint64 addedTs = 0;     // ms epoch — pertama kali masuk DB
    qint64 modifiedTs = 0;  // ms epoch — lastModified file
    int avail = 1;
};

Q_DECLARE_METATYPE(SongRecord)
Q_DECLARE_METATYPE(QVector<SongRecord>)
class LibraryDatabase
{
public:
    explicit LibraryDatabase(const QString &dbPath);

    // path DB di AppLocalData/ianplayer-pc.db; kosong jika pabrikan gagal
    QString dbPath() const { return m_dbPath; }
    bool isOpen() const { return m_open; }

    // ---- scan pipeline ----
    QHash<QString, qint64> knownModifiedByPath() const;    // path -> modifiedTs
    // upsert single file (transaksi dibungkus oleh pemanggil)
    bool upsertSong(const SongRecord &rec);
    // batch upsert dalam 1 transaksi + commit; true jika semua sukses
    bool persistRecords(const QVector<SongRecord> &records);
    // sekali di akhir scan: hilangkan (avail=0) baris yang tak lagi ada
    // dalam alivePaths (in-memory diff biar hemat SQL parameter limit)
    int markUnavailableExcept(const QSet<QString> &alivePaths);
    // isi artist_id/album_id + tabel albums/artists/art_256 hasil rekursif
    void rebuildIndexes();

    // ---- query untuk UI/model (M1.2+) ----
    QVector<SongRecord> allSongs() const;
    int songCount() const;

    // ---- paritas SongStore.kt (#7 target M1.1) ----
    // song_key = "local:<id>" | "yt:<videoId>" (02-ARCHITECTURE.md)
    std::optional<qint64> songIdByPath(const QString &path) const;
    std::optional<QPair<qint64, qint64>> songIdAndDurationByPath(const QString &path) const;

    // favorites
    bool isFavorite(const QString &songKey) const;
    QSet<QString> favoriteKeys() const;
    bool setFavorite(const QString &songKey, bool favorite);

    // play counts + play history (window 90 hari, dipangkas di recordPlayed —
    // paritas SongStore.recordPlayed Android)
    bool recordPlayed(const QString &songKey, qint64 playedTsMs, qint64 durationMs);
    int playCount(const QString &songKey) const;
    QHash<QString, int> allPlayCounts() const;

    struct PlayHistoryEntry
    {
        QString songKey;
        qint64 playedTsMs = 0;
        qint64 durationMs = 0;
    };
    // Baris history yang masih dalam window 90d (lihat recordPlayed)
    QVector<PlayHistoryEntry> playHistory() const;

    // ---- daftar albums/artists untuk UI grid (M1.2) ----
    struct AlbumRow
    {
        qint64 id = 0;
        QString name;
        QString artist;
        QString art256;
        int songCount = 0;
    };
    struct ArtistRow
    {
        qint64 id = 0;
        QString name;
        QString art256;
        int songCount = 0;
    };
    QVector<AlbumRow> allAlbums() const;   // urut nama COLLATE NOCASE
    QVector<ArtistRow> allArtists() const;

    // ---- playlists untuk UI (M1.3, paritas SongStore.kt) ----
    struct PlaylistRow
    {
        qint64 id = 0;
        QString name;
        QString image;
        qint64 createdTs = 0;
        int songCount = 0;
    };
    QVector<PlaylistRow> allPlaylists() const;             // urut nama NOCASE
    QVector<SongRecord> playlistSongs(qint64 playlistId) const; // urut position, avail=1

    qint64 createPlaylist(const QString &name);            // id baru / -1 gagal
    bool renamePlaylist(qint64 id, const QString &name);
    bool deletePlaylist(qint64 id);                        // ikut hapus playlist_songs
    bool setPlaylistImage(qint64 id, const QString &imagePath);
    bool addToPlaylist(qint64 playlistId, qint64 songId);  // append posisi akhir
    bool removeFromPlaylist(qint64 playlistId, qint64 songId);
    // reorder live: ambil lagu, lepas dari posisinya, selipkan ke toPos (0..n-1),
    // tulis ulang semua position. for DnD drag reorder playlist detail.
    bool movePlaylistSong(qint64 playlistId, qint64 songId, int toPos);

private:
    void createSchema();

    QString m_dbPath;
    bool m_open = false;
};
