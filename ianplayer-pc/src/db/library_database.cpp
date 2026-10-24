#include "db/library_database.h"

#include <QDateTime>
#include <QDir>
#include <QFileInfo>
#include <algorithm>

#include <QtSql/QSqlDatabase>
#include <QtSql/QSqlError>
#include <QtSql/QSqlQuery>

namespace {

constexpr const char *kConnName = "library";

QSqlDatabase libraryConnection(const QString &dbPath)
{
    const QString connId = QStringLiteral("%1#%2").arg(QLatin1String(kConnName), dbPath);

    if (QSqlDatabase::contains(connId))
        return QSqlDatabase::database(connId);

    QSqlDatabase db = QSqlDatabase::addDatabase(QStringLiteral("QSQLITE"), connId);
    QDir().mkpath(QFileInfo(dbPath).absolutePath());
    db.setDatabaseName(dbPath);
    if (!db.open())
        return db;

    // WAL supaya scan & pembacaan UI tak saling mengunci (pitfall doc)
    db.exec(QStringLiteral("PRAGMA journal_mode=WAL"));
    db.exec(QStringLiteral("PRAGMA foreign_keys=ON"));
    return db;
}

QString queryLastError(const QSqlQuery &q)
{
    return q.lastError().databaseText();
}

} // namespace

LibraryDatabase::LibraryDatabase(const QString &dbPath)
    : m_dbPath(dbPath)
{
    const QSqlDatabase db = libraryConnection(dbPath);
    m_open = db.isValid() && db.isOpen();
    if (m_open)
        createSchema();
}

void LibraryDatabase::createSchema()
{
    QSqlDatabase db = libraryConnection(m_dbPath);

    const QStringList statements = {
        QStringLiteral(
            "CREATE TABLE IF NOT EXISTS songs ("
            "id INTEGER PRIMARY KEY,"
            "path TEXT UNIQUE,"
            "title TEXT,"
            "artist TEXT,"
            "artist_id INTEGER DEFAULT 0,"
            "album TEXT,"
            "album_id INTEGER DEFAULT 0,"
            "track_no INTEGER DEFAULT 0,"
            "year INTEGER DEFAULT 0,"
            "duration_ms INTEGER DEFAULT 0,"
            "bitrate INTEGER DEFAULT 0,"
            "codec TEXT,"
            "art_256 TEXT DEFAULT '',"
            "art_64 TEXT DEFAULT '',"
            "added_ts INTEGER DEFAULT 0,"
            "modified_ts INTEGER DEFAULT 0,"
            "avail INTEGER DEFAULT 1)"),
        QStringLiteral(
            "CREATE TABLE IF NOT EXISTS albums ("
            "id INTEGER PRIMARY KEY,"
            "name TEXT,"
            "artist TEXT,"
            "art_256 TEXT DEFAULT '')"),
        QStringLiteral(
            "CREATE TABLE IF NOT EXISTS artists ("
            "id INTEGER PRIMARY KEY,"
            "name TEXT,"
            "art_256 TEXT DEFAULT '')"),
        QStringLiteral(
            "CREATE TABLE IF NOT EXISTS playlists ("
            "id INTEGER PRIMARY KEY,"
            "name TEXT,"
            "image TEXT,"
            "created_ts INTEGER)"),
        QStringLiteral(
            "CREATE TABLE IF NOT EXISTS playlist_songs ("
            "playlist_id INTEGER,"
            "song_id INTEGER,"
            "position INTEGER,"
            "PRIMARY KEY(playlist_id, song_id))"),
        QStringLiteral(
            "CREATE TABLE IF NOT EXISTS favorites ("
            "song_key TEXT PRIMARY KEY)"),
        QStringLiteral(
            "CREATE TABLE IF NOT EXISTS play_counts ("
            "song_key TEXT PRIMARY KEY,"
            "count INTEGER DEFAULT 0,"
            "last_ts INTEGER DEFAULT 0)"),
        QStringLiteral(
            "CREATE TABLE IF NOT EXISTS play_history ("
            "song_key TEXT,"
            "played_ts INTEGER,"
            "duration_ms INTEGER)"),
        QStringLiteral(
            "CREATE INDEX IF NOT EXISTS idx_songs_artist ON songs(artist_id)"),
        QStringLiteral(
            "CREATE INDEX IF NOT EXISTS idx_songs_album ON songs(album_id)"),
    };

    for (const QString &sql : statements) {
        QSqlQuery q(db);
        if (!q.exec(sql))
            return;
    }
}

QHash<QString, qint64> LibraryDatabase::knownModifiedByPath() const
{
    QHash<QString, qint64> result;
    if (!m_open)
        return result;

    QSqlQuery q(libraryConnection(m_dbPath));
    q.prepare(QStringLiteral("SELECT path, modified_ts FROM songs"));
    q.exec();
    while (q.next())
        result.insert(q.value(0).toString(), q.value(1).toLongLong());
    return result;
}

bool LibraryDatabase::upsertSong(const SongRecord &rec)
{
    if (!m_open)
        return false;

    QSqlQuery q(libraryConnection(m_dbPath));
    q.prepare(QStringLiteral(
        "INSERT INTO songs (path, title, artist, artist_id, album, album_id,"
        " track_no, year, duration_ms, bitrate, codec, art_256, art_64,"
        " added_ts, modified_ts, avail) VALUES"
        " (:path, :title, :artist, 0, :album, 0, :track, :year, :dur,"
        "  :bitrate, :codec, :art256, :art64, :added, :modified, 1)"
        " ON CONFLICT(path) DO UPDATE SET"
        " title=excluded.title, artist=excluded.artist, album=excluded.album,"
        " track_no=excluded.track_no, year=excluded.year,"
        " duration_ms=excluded.duration_ms, bitrate=excluded.bitrate,"
        " codec=excluded.codec, modified_ts=excluded.modified_ts, avail=1"));
    q.bindValue(QStringLiteral(":path"), rec.path);
    q.bindValue(QStringLiteral(":title"), rec.title);
    q.bindValue(QStringLiteral(":artist"), rec.artist);
    q.bindValue(QStringLiteral(":album"), rec.album);
    q.bindValue(QStringLiteral(":track"), rec.trackNo);
    q.bindValue(QStringLiteral(":year"), rec.year);
    q.bindValue(QStringLiteral(":dur"), rec.durationMs);
    q.bindValue(QStringLiteral(":bitrate"), rec.bitrate);
    q.bindValue(QStringLiteral(":codec"), rec.codec);
    q.bindValue(QStringLiteral(":art256"), rec.art256);
    q.bindValue(QStringLiteral(":art64"), rec.art64);
    q.bindValue(QStringLiteral(":added"), rec.addedTs);
    q.bindValue(QStringLiteral(":modified"), rec.modifiedTs);

    if (!q.exec())
        return false;

    // catatan: art_256/art_64 & added_ts sengaja tidak dikut panggil di
    // ON CONFLICT UPDATE — artwork lama dan added_ts dipertahankan.
    return true;
}

bool LibraryDatabase::persistRecords(const QVector<SongRecord> &records)
{
    if (!m_open)
        return false;
    if (records.isEmpty())
        return true;

    QSqlDatabase db = libraryConnection(m_dbPath);
    if (!db.transaction())
        return false;

    bool ok = true;
    for (const SongRecord &rec : records) {
        if (!upsertSong(rec)) {
            ok = false;
            break;
        }
    }
    if (ok && !db.commit()) {
        ok = false;
    }
    if (!ok)
        db.rollback();
    return ok;
}

int LibraryDatabase::markUnavailableExcept(const QSet<QString> &alivePaths)
{
    if (!m_open)
        return 0;

    QSqlDatabase db = libraryConnection(m_dbPath);
    QSqlQuery select(db);
    select.prepare(QStringLiteral("SELECT id, path FROM songs WHERE avail = 1"));
    select.exec();

    QList<qint64> toMark;
    while (select.next()) {
        const QString path = select.value(1).toString();
        if (!alivePaths.contains(path))
            toMark.append(select.value(0).toLongLong());
    }
    if (toMark.isEmpty())
        return 0;

    QSqlQuery update(db);
    update.prepare(QStringLiteral("UPDATE songs SET avail = 0 WHERE id = :id"));
    for (qint64 id : toMark) {
        update.bindValue(QStringLiteral(":id"), id);
        update.exec();
    }
    return toMark.size();
}

void LibraryDatabase::rebuildIndexes()
{
    if (!m_open)
        return;

    QSqlDatabase db = libraryConnection(m_dbPath);
    db.transaction();

    QSqlQuery q(db);

    const QStringList statements = {
        QStringLiteral("DELETE FROM albums"),
        QStringLiteral(
            "INSERT INTO albums (name, artist)"
            " SELECT DISTINCT album, artist FROM songs WHERE avail = 1 AND album <> ''"),
        QStringLiteral("DELETE FROM artists"),
        QStringLiteral(
            "INSERT INTO artists (name)"
            " SELECT DISTINCT artist FROM songs WHERE avail = 1 AND artist <> ''"),
        QStringLiteral(
            "UPDATE songs SET artist_id ="
            " COALESCE((SELECT id FROM artists WHERE name = songs.artist), 0)"),
        QStringLiteral(
            "UPDATE songs SET album_id ="
            " COALESCE((SELECT id FROM albums WHERE name = songs.album AND artist = songs.artist), 0)"),
        QStringLiteral(
            "UPDATE albums SET art_256 ="
            " COALESCE((SELECT s.art_256 FROM songs s"
            " WHERE s.album_id = albums.id AND s.art_256 <> '' LIMIT 1), '')"),
        QStringLiteral(
            "UPDATE artists SET art_256 ="
            " COALESCE((SELECT s.art_256 FROM songs s"
            " WHERE s.artist_id = artists.id AND s.art_256 <> '' LIMIT 1), '')"),
    };
    for (const QString &sql : statements)
        q.exec(sql);

    db.commit();
}

QVector<SongRecord> LibraryDatabase::allSongs() const
{
    QVector<SongRecord> rows;
    if (!m_open)
        return rows;

    QSqlQuery q(libraryConnection(m_dbPath));
    q.prepare(QStringLiteral(
        "SELECT id, path, title, artist, artist_id, album, album_id, track_no,"
        " year, duration_ms, bitrate, codec, art_256, art_64, added_ts,"
        " modified_ts, avail FROM songs WHERE avail = 1 ORDER BY title COLLATE NOCASE"));
    q.exec();
    while (q.next()) {
        SongRecord rec;
        rec.id = q.value(0).toLongLong();
        rec.path = q.value(1).toString();
        rec.title = q.value(2).toString();
        rec.artist = q.value(3).toString();
        rec.artistId = q.value(4).toLongLong();
        rec.album = q.value(5).toString();
        rec.albumId = q.value(6).toLongLong();
        rec.trackNo = q.value(7).toInt();
        rec.year = q.value(8).toInt();
        rec.durationMs = q.value(9).toLongLong();
        rec.bitrate = q.value(10).toInt();
        rec.codec = q.value(11).toString();
        rec.art256 = q.value(12).toString();
        rec.art64 = q.value(13).toString();
        rec.addedTs = q.value(14).toLongLong();
        rec.modifiedTs = q.value(15).toLongLong();
        rec.avail = q.value(16).toInt();
        rows.append(rec);
    }
    return rows;
}

int LibraryDatabase::songCount() const
{
    if (!m_open)
        return 0;
    QSqlQuery q(libraryConnection(m_dbPath));
    q.exec(QStringLiteral("SELECT COUNT(*) FROM songs WHERE avail = 1"));
    return q.next() ? q.value(0).toInt() : 0;
}

// ==== playlists (M1.3) ====

QVector<LibraryDatabase::PlaylistRow> LibraryDatabase::allPlaylists() const
{
    QVector<PlaylistRow> result;
    if (!m_open)
        return result;
    QSqlQuery q(libraryConnection(m_dbPath));
    q.prepare(QStringLiteral(
        "SELECT p.id, p.name, p.image, p.created_ts,"
        " (SELECT COUNT(*) FROM playlist_songs ps"
        "   JOIN songs s ON s.id = ps.song_id AND s.avail = 1"
        "   WHERE ps.playlist_id = p.id)"
        " FROM playlists p ORDER BY p.name COLLATE NOCASE"));
    q.exec();
    while (q.next()) {
        PlaylistRow row;
        row.id = q.value(0).toLongLong();
        row.name = q.value(1).toString();
        row.image = q.value(2).toString();
        row.createdTs = q.value(3).toLongLong();
        row.songCount = q.value(4).toInt();
        result.append(row);
    }
    return result;
}

QVector<SongRecord> LibraryDatabase::playlistSongs(qint64 playlistId) const
{
    QVector<SongRecord> result;
    if (!m_open)
        return result;
    QSqlQuery q(libraryConnection(m_dbPath));
    q.prepare(QStringLiteral(
        "SELECT s.id, s.path, s.title, s.artist, s.artist_id, s.album,"
        " s.album_id, s.track_no, s.year, s.duration_ms, s.bitrate, s.codec,"
        " s.art_256, s.art_64, s.added_ts, s.modified_ts, s.avail"
        " FROM playlist_songs ps JOIN songs s ON s.id = ps.song_id"
        " WHERE ps.playlist_id = :pl AND s.avail = 1 ORDER BY ps.position"));
    q.bindValue(QStringLiteral(":pl"), playlistId);
    q.exec();
    while (q.next()) {
        SongRecord rec;
        rec.id = q.value(0).toLongLong();
        rec.path = q.value(1).toString();
        rec.title = q.value(2).toString();
        rec.artist = q.value(3).toString();
        rec.artistId = q.value(4).toLongLong();
        rec.album = q.value(5).toString();
        rec.albumId = q.value(6).toLongLong();
        rec.trackNo = q.value(7).toInt();
        rec.year = q.value(8).toInt();
        rec.durationMs = q.value(9).toLongLong();
        rec.bitrate = q.value(10).toInt();
        rec.codec = q.value(11).toString();
        rec.art256 = q.value(12).toString();
        rec.art64 = q.value(13).toString();
        rec.addedTs = q.value(14).toLongLong();
        rec.modifiedTs = q.value(15).toLongLong();
        rec.avail = q.value(16).toInt();
        result.append(rec);
    }
    return result;
}

qint64 LibraryDatabase::createPlaylist(const QString &name)
{
    if (!m_open || name.trimmed().isEmpty())
        return -1;
    QSqlQuery q(libraryConnection(m_dbPath));
    q.prepare(QStringLiteral(
        "INSERT INTO playlists (name, image, created_ts) VALUES (:name, '', :ts)"));
    q.bindValue(QStringLiteral(":name"), name.trimmed());
    q.bindValue(QStringLiteral(":ts"), QDateTime::currentMSecsSinceEpoch());
    if (!q.exec())
        return -1;
    return q.lastInsertId().toLongLong();
}

bool LibraryDatabase::renamePlaylist(qint64 id, const QString &name)
{
    if (!m_open || name.trimmed().isEmpty())
        return false;
    QSqlQuery q(libraryConnection(m_dbPath));
    q.prepare(QStringLiteral("UPDATE playlists SET name = :name WHERE id = :id"));
    q.bindValue(QStringLiteral(":name"), name.trimmed());
    q.bindValue(QStringLiteral(":id"), id);
    return q.exec();
}

bool LibraryDatabase::deletePlaylist(qint64 id)
{
    if (!m_open)
        return false;
    QSqlDatabase db = libraryConnection(m_dbPath);
    if (!db.transaction())
        return false;
    QSqlQuery q(db);
    q.prepare(QStringLiteral("DELETE FROM playlists WHERE id = :id"));
    q.bindValue(QStringLiteral(":id"), id);
    if (!q.exec()) {
        db.rollback();
        return false;
    }
    q.prepare(QStringLiteral("DELETE FROM playlist_songs WHERE playlist_id = :id"));
    q.bindValue(QStringLiteral(":id"), id);
    if (!q.exec()) {
        db.rollback();
        return false;
    }
    if (!db.commit()) {
        db.rollback();
        return false;
    }
    return true;
}

bool LibraryDatabase::setPlaylistImage(qint64 id, const QString &imagePath)
{
    if (!m_open)
        return false;
    QSqlQuery q(libraryConnection(m_dbPath));
    q.prepare(QStringLiteral("UPDATE playlists SET image = :img WHERE id = :id"));
    q.bindValue(QStringLiteral(":img"), imagePath);
    q.bindValue(QStringLiteral(":id"), id);
    return q.exec();
}

bool LibraryDatabase::addToPlaylist(qint64 playlistId, qint64 songId)
{
    if (!m_open)
        return false;
    QSqlDatabase db = libraryConnection(m_dbPath);
    if (!db.transaction())
        return false;
    QSqlQuery q(db);
    // posisi akhir = jumlah baris playlist
    q.prepare(QStringLiteral(
        "SELECT COALESCE(MAX(position) + 1, 0) FROM playlist_songs WHERE playlist_id = :pl"));
    q.bindValue(QStringLiteral(":pl"), playlistId);
    if (!q.exec() || !q.next()) {
        db.rollback();
        return false;
    }
    const int nextPos = q.value(0).toInt();

    q.prepare(QStringLiteral(
        "INSERT OR IGNORE INTO playlist_songs (playlist_id, song_id, position)"
        " VALUES (:pl, :song, :pos)"));
    q.bindValue(QStringLiteral(":pl"), playlistId);
    q.bindValue(QStringLiteral(":song"), songId);
    q.bindValue(QStringLiteral(":pos"), nextPos);
    if (!q.exec()) {
        db.rollback();
        return false;
    }
    const int inserted = q.numRowsAffected();   // 0 = sudah ada (INSERT OR IGNORE)
    if (!db.commit()) {
        db.rollback();
        return false;
    }
    return inserted > 0;
}

bool LibraryDatabase::removeFromPlaylist(qint64 playlistId, qint64 songId)
{
    if (!m_open)
        return false;
    // hapus + rapikan ulang position supaya urutan tetap padat
    QSqlDatabase db = libraryConnection(m_dbPath);
    if (!db.transaction())
        return false;
    QSqlQuery q(db);
    q.prepare(QStringLiteral(
        "DELETE FROM playlist_songs WHERE playlist_id = :pl AND song_id = :song"));
    q.bindValue(QStringLiteral(":pl"), playlistId);
    q.bindValue(QStringLiteral(":song"), songId);
    if (!q.exec() || q.numRowsAffected() == 0) {
        db.rollback();
        return false;   // lagu memang tidak ada di playlist
    }

    q.prepare(QStringLiteral(
        "SELECT song_id FROM playlist_songs WHERE playlist_id = :pl ORDER BY position"));
    q.bindValue(QStringLiteral(":pl"), playlistId);
    if (!q.exec()) {
        db.rollback();
        return false;
    }
    QVector<qint64> order;
    while (q.next())
        order.append(q.value(0).toLongLong());
    if (!db.commit())
        return false;
    return true;
}

bool LibraryDatabase::movePlaylistSong(qint64 playlistId, qint64 songId, int toPos)
{
    if (!m_open)
        return false;
    QSqlDatabase db = libraryConnection(m_dbPath);
    if (!db.transaction())
        return false;
    QSqlQuery q(db);
    q.prepare(QStringLiteral(
        "SELECT song_id FROM playlist_songs WHERE playlist_id = :pl ORDER BY position"));
    q.bindValue(QStringLiteral(":pl"), playlistId);
    if (!q.exec()) {
        db.rollback();
        return false;
    }
    QVector<qint64> order;
    int current = -1;
    while (q.next()) {
        const qint64 id = q.value(0).toLongLong();
        if (id == songId)
            current = int(order.size());
        order.append(id);
    }
    if (current < 0 || toPos < 0 || toPos >= order.size()) {
        db.rollback();
        return false;
    }
    order.removeAt(current);
    toPos = std::clamp(toPos, 0, int(order.size()) - 1);
    order.insert(order.begin() + toPos, songId);

    QSqlQuery upd(db);
    upd.prepare(QStringLiteral(
        "UPDATE playlist_songs SET position = :pos WHERE playlist_id = :pl AND song_id = :song"));
    for (int i = 0; i < order.size(); ++i) {
        upd.bindValue(QStringLiteral(":pos"), i);
        upd.bindValue(QStringLiteral(":pl"), playlistId);
        upd.bindValue(QStringLiteral(":song"), order.at(i));
        if (!upd.exec()) {
            db.rollback();
            return false;
        }
    }
    if (!db.commit()) {
        db.rollback();
        return false;
    }
    return true;
}

// ==== paritas SongStore.kt: favorites / play counts / play history ====

std::optional<qint64> LibraryDatabase::songIdByPath(const QString &path) const
{
    if (!m_open)
        return std::nullopt;
    QSqlQuery q(libraryConnection(m_dbPath));
    q.prepare(QStringLiteral("SELECT id FROM songs WHERE path = :path"));
    q.bindValue(QStringLiteral(":path"), path);
    if (!q.exec() || !q.next())
        return std::nullopt;
    return q.value(0).toLongLong();
}

std::optional<QPair<qint64, qint64>>
LibraryDatabase::songIdAndDurationByPath(const QString &path) const
{
    if (!m_open)
        return std::nullopt;
    QSqlQuery q(libraryConnection(m_dbPath));
    q.prepare(QStringLiteral("SELECT id, duration_ms FROM songs WHERE path = :path"));
    q.bindValue(QStringLiteral(":path"), path);
    if (!q.exec() || !q.next())
        return std::nullopt;
    return QPair<qint64, qint64>{ q.value(0).toLongLong(), q.value(1).toLongLong() };
}

bool LibraryDatabase::isFavorite(const QString &songKey) const
{
    if (!m_open)
        return false;
    QSqlQuery q(libraryConnection(m_dbPath));
    q.prepare(QStringLiteral("SELECT 1 FROM favorites WHERE song_key = :key"));
    q.bindValue(QStringLiteral(":key"), songKey);
    return q.exec() && q.next();
}

QSet<QString> LibraryDatabase::favoriteKeys() const
{
    QSet<QString> result;
    if (!m_open)
        return result;
    QSqlQuery q(libraryConnection(m_dbPath));
    q.exec(QStringLiteral("SELECT song_key FROM favorites"));
    while (q.next())
        result.insert(q.value(0).toString());
    return result;
}

bool LibraryDatabase::setFavorite(const QString &songKey, bool favorite)
{
    if (!m_open)
        return false;
    QSqlQuery q(libraryConnection(m_dbPath));
    if (favorite) {
        q.prepare(QStringLiteral(
            "INSERT OR IGNORE INTO favorites (song_key) VALUES (:key)"));
        q.bindValue(QStringLiteral(":key"), songKey);
    } else {
        q.prepare(QStringLiteral("DELETE FROM favorites WHERE song_key = :key"));
        q.bindValue(QStringLiteral(":key"), songKey);
    }
    return q.exec();
}

bool LibraryDatabase::recordPlayed(const QString &songKey, qint64 playedTsMs, qint64 durationMs)
{
    if (!m_open)
        return false;

    // Paritas SongStore.recordPlayed: counter +1, history append, lalu trim
    // baris lebih tua dari window 90 hari (cutoff = now - HISTORY_WINDOW_MS).
    QSqlDatabase db = libraryConnection(m_dbPath);
    if (!db.transaction())
        return false;

    QSqlQuery q(db);

    q.prepare(QStringLiteral(
        "INSERT INTO play_counts (song_key, count, last_ts) VALUES (:key, 1, :ts)"
        " ON CONFLICT(song_key) DO UPDATE SET count = count + 1, last_ts = excluded.last_ts"));
    q.bindValue(QStringLiteral(":key"), songKey);
    q.bindValue(QStringLiteral(":ts"), playedTsMs);
    if (!q.exec()) {
        db.rollback();
        return false;
    }

    q.prepare(QStringLiteral(
        "INSERT INTO play_history (song_key, played_ts, duration_ms)"
        " VALUES (:key, :ts, :dur)"));
    q.bindValue(QStringLiteral(":key"), songKey);
    q.bindValue(QStringLiteral(":ts"), playedTsMs);
    q.bindValue(QStringLiteral(":dur"), durationMs);
    if (!q.exec()) {
        db.rollback();
        return false;
    }

    constexpr qint64 kHistoryWindowMs = 90LL * 24 * 60 * 60 * 1000;
    const qint64 cutoff = playedTsMs - kHistoryWindowMs;
    q.prepare(QStringLiteral(
        "DELETE FROM play_history WHERE played_ts <= :cutoff"));
    q.bindValue(QStringLiteral(":cutoff"), cutoff);
    if (!q.exec()) {
        db.rollback();
        return false;
    }

    if (!db.commit()) {
        db.rollback();
        return false;
    }
    return true;
}

int LibraryDatabase::playCount(const QString &songKey) const
{
    if (!m_open)
        return 0;
    QSqlQuery q(libraryConnection(m_dbPath));
    q.prepare(QStringLiteral("SELECT count FROM play_counts WHERE song_key = :key"));
    q.bindValue(QStringLiteral(":key"), songKey);
    if (!q.exec() || !q.next())
        return 0;
    return q.value(0).toInt();
}

QHash<QString, int> LibraryDatabase::allPlayCounts() const
{
    QHash<QString, int> result;
    if (!m_open)
        return result;
    QSqlQuery q(libraryConnection(m_dbPath));
    q.exec(QStringLiteral("SELECT song_key, count FROM play_counts"));
    while (q.next())
        result.insert(q.value(0).toString(), q.value(1).toInt());
    return result;
}

QVector<LibraryDatabase::PlayHistoryEntry> LibraryDatabase::playHistory() const
{
    QVector<PlayHistoryEntry> result;
    if (!m_open)
        return result;
    QSqlQuery q(libraryConnection(m_dbPath));
    q.prepare(QStringLiteral(
        "SELECT song_key, played_ts, duration_ms FROM play_history"
        " WHERE played_ts > :cutoff ORDER BY played_ts DESC"));
    constexpr qint64 kHistoryWindowMs = 90LL * 24 * 60 * 60 * 1000;
    q.bindValue(QStringLiteral(":cutoff"),
                QDateTime::currentMSecsSinceEpoch() - kHistoryWindowMs);
    q.exec();
    while (q.next()) {
        PlayHistoryEntry entry;
        entry.songKey = q.value(0).toString();
        entry.playedTsMs = q.value(1).toLongLong();
        entry.durationMs = q.value(2).toLongLong();
        result.append(entry);
    }
    return result;
}

QVector<LibraryDatabase::AlbumRow> LibraryDatabase::allAlbums() const
{
    QVector<AlbumRow> result;
    if (!m_open)
        return result;
    QSqlQuery q(libraryConnection(m_dbPath));
    q.prepare(QStringLiteral(
        "SELECT a.id, a.name, a.artist, a.art_256,"
        " (SELECT COUNT(*) FROM songs s WHERE s.album_id = a.id AND s.avail = 1)"
        " FROM albums a ORDER BY a.name COLLATE NOCASE"));
    q.exec();
    while (q.next()) {
        AlbumRow row;
        row.id = q.value(0).toLongLong();
        row.name = q.value(1).toString();
        row.artist = q.value(2).toString();
        row.art256 = q.value(3).toString();
        row.songCount = q.value(4).toInt();
        result.append(row);
    }
    return result;
}

QVector<LibraryDatabase::ArtistRow> LibraryDatabase::allArtists() const
{
    QVector<ArtistRow> result;
    if (!m_open)
        return result;
    QSqlQuery q(libraryConnection(m_dbPath));
    q.prepare(QStringLiteral(
        "SELECT t.id, t.name, t.art_256,"
        " (SELECT COUNT(*) FROM songs s WHERE s.artist_id = t.id AND s.avail = 1)"
        " FROM artists t ORDER BY t.name COLLATE NOCASE"));
    q.exec();
    while (q.next()) {
        ArtistRow row;
        row.id = q.value(0).toLongLong();
        row.name = q.value(1).toString();
        row.art256 = q.value(2).toString();
        row.songCount = q.value(3).toInt();
        result.append(row);
    }
    return result;
}
