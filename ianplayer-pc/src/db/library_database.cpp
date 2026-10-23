#include "db/library_database.h"

#include <QDateTime>
#include <QDir>
#include <QFileInfo>
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
