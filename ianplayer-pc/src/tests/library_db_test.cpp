#include <gtest/gtest.h>

#include "db/library_database.h"
#include <QTemporaryDir>
#include <QDateTime>

// Paritas SongStore.kt #7 (target M1.1):
// favorites + play counts + play history window 90 hari di SQLite.

namespace {

QString openTempDb(QTemporaryDir &dir)
{
    return dir.filePath(QStringLiteral("test.db"));
}

constexpr qint64 kDay = 24LL * 60 * 60 * 1000;

} // namespace

TEST(LibraryDbTest, FavoriteToggleAndList)
{
    QTemporaryDir dir;
    LibraryDatabase db(openTempDb(dir));
    ASSERT_TRUE(db.isOpen());

    EXPECT_FALSE(db.isFavorite("local:1"));
    EXPECT_TRUE(db.setFavorite("local:1", true));
    EXPECT_TRUE(db.isFavorite("local:1"));
    EXPECT_EQ(db.favoriteKeys(), QSet<QString>{"local:1"});

    // idempotent insert
    EXPECT_TRUE(db.setFavorite("local:1", true));
    EXPECT_EQ(db.favoriteKeys().size(), 1);

    EXPECT_TRUE(db.setFavorite("local:2", true));
    EXPECT_TRUE(db.setFavorite("yt:dQw4w9WgXcQ", true));
    EXPECT_EQ(db.favoriteKeys().size(), 3);

    EXPECT_TRUE(db.setFavorite("local:1", false));
    EXPECT_FALSE(db.isFavorite("local:1"));
    EXPECT_EQ(db.favoriteKeys().size(), 2);
}

TEST(LibraryDbTest, PlayCountIncrementsPerKey)
{
    QTemporaryDir dir;
    LibraryDatabase db(openTempDb(dir));
    ASSERT_TRUE(db.isOpen());

    EXPECT_EQ(db.playCount("local:1"), 0);

    const qint64 now = QDateTime::currentMSecsSinceEpoch();
    EXPECT_TRUE(db.recordPlayed("local:1", now, 180000));
    EXPECT_TRUE(db.recordPlayed("local:1", now + 1000, 180000));
    EXPECT_TRUE(db.recordPlayed("yt:abc", now + 2000, 240000));

    EXPECT_EQ(db.playCount("local:1"), 2);
    EXPECT_EQ(db.playCount("yt:abc"), 1);
    EXPECT_EQ(db.playCount("local:999"), 0);

    const auto counts = db.allPlayCounts();
    EXPECT_EQ(counts.value(QLatin1String("local:1")), 2);
    EXPECT_EQ(counts.value(QLatin1String("yt:abc")), 1);
    EXPECT_EQ(counts.size(), 2);
}

TEST(LibraryDbTest, HistoryTrimmedTo90DayWindow)
{
    QTemporaryDir dir;
    LibraryDatabase db(openTempDb(dir));
    ASSERT_TRUE(db.isOpen());

    const qint64 now = QDateTime::currentMSecsSinceEpoch();
    // replay 100 hari lalu — dipangkas langsung oleh recordPlayed (paritas
    // filter { it.second > cutoff } Android, cutoff = now - 90d)
    EXPECT_TRUE(db.recordPlayed("local:old", now - 100 * kDay, 0));
    EXPECT_EQ(db.playCount("local:old"), 1);          // count tetap tercatat
    EXPECT_TRUE(db.playHistory().isEmpty());          // history terpangkas

    EXPECT_TRUE(db.recordPlayed("local:new", now, 1000));
    const auto history = db.playHistory();
    ASSERT_EQ(history.size(), 1);
    EXPECT_EQ(history.first().songKey, QLatin1String("local:new"));
    EXPECT_EQ(history.first().durationMs, 1000);
}

TEST(LibraryDbTest, SongIdLookupByPath)
{
    QTemporaryDir dir;
    LibraryDatabase db(openTempDb(dir));
    ASSERT_TRUE(db.isOpen());

    EXPECT_FALSE(db.songIdByPath(QStringLiteral("C:/music/a.mp3")).has_value());

    SongRecord rec;
    rec.path = QStringLiteral("C:/music/a.mp3");
    rec.title = QStringLiteral("Title");
    rec.artist = QStringLiteral("Artist");
    rec.album = QStringLiteral("Album");
    rec.durationMs = 321000;
    rec.modifiedTs = 1234;
    EXPECT_TRUE(db.upsertSong(rec));

    const auto id = db.songIdByPath(QStringLiteral("C:/music/a.mp3"));
    ASSERT_TRUE(id.has_value());

    const auto idDur = db.songIdAndDurationByPath(QStringLiteral("C:/music/a.mp3"));
    ASSERT_TRUE(idDur.has_value());
    EXPECT_EQ(idDur->first, *id);
    EXPECT_EQ(idDur->second, 321000);

    EXPECT_FALSE(db.songIdAndDurationByPath(QStringLiteral("C:/music/b.mp3")).has_value());
}

// ==== M1.3 playlists ====

namespace {

// 3 lagu contoh; balikin id sesuai urutan.
QVector<qint64> seedSongs(LibraryDatabase &db)
{
    const QStringList paths = {
        QStringLiteral("C:/music/s1.mp3"),
        QStringLiteral("C:/music/s2.mp3"),
        QStringLiteral("C:/music/s3.mp3"),
    };
    QVector<qint64> ids;
    for (int i = 0; i < paths.size(); ++i) {
        SongRecord rec;
        rec.path = paths.at(i);
        rec.title = QStringLiteral("Song %1").arg(i + 1);
        rec.artist = QStringLiteral("Artist");
        rec.album = QStringLiteral("Album");
        rec.durationMs = 100000 + i;
        rec.modifiedTs = 1;
        EXPECT_TRUE(db.upsertSong(rec));
        const auto id = db.songIdByPath(paths.at(i));
        EXPECT_TRUE(id.has_value());
        ids.append(*id);
    }
    return ids;
}

} // namespace

TEST(LibraryDbTest, PlaylistCrudAndSongOrder)
{
    QTemporaryDir dir;
    LibraryDatabase db(openTempDb(dir));
    ASSERT_TRUE(db.isOpen());
    const auto songIds = seedSongs(db);
    ASSERT_EQ(songIds.size(), 3);

    // create + allPlaylists urut nama NOCASE + songCount
    const qint64 pidA = db.createPlaylist(QStringLiteral("Alpha"));
    const qint64 pidB = db.createPlaylist(QStringLiteral("beta"));
    ASSERT_GT(pidA, 0);
    ASSERT_GT(pidB, 0);

    auto playlists = db.allPlaylists();
    ASSERT_EQ(playlists.size(), 2);
    EXPECT_EQ(playlists.at(0).name, QStringLiteral("Alpha"));
    EXPECT_EQ(playlists.at(1).name, QStringLiteral("beta"));
    EXPECT_EQ(playlists.at(0).songCount, 0);

    // addTo appends di posisi akhir
    EXPECT_TRUE(db.addToPlaylist(pidA, songIds.at(0)));
    EXPECT_TRUE(db.addToPlaylist(pidA, songIds.at(1)));
    EXPECT_TRUE(db.addToPlaylist(pidA, songIds.at(2)));

    auto songs = db.playlistSongs(pidA);
    ASSERT_EQ(songs.size(), 3);
    EXPECT_EQ(songs.at(0).id, songIds.at(0));
    EXPECT_EQ(songs.at(1).id, songIds.at(1));
    EXPECT_EQ(songs.at(2).id, songIds.at(2));

    // rename
    EXPECT_TRUE(db.renamePlaylist(pidA, QStringLiteral("Zeta")));
    playlists = db.allPlaylists();
    EXPECT_EQ(playlists.at(1).name, QStringLiteral("Zeta"));

    // movePlaylistSong: s3 (posisi 2) pindah ke 0
    EXPECT_TRUE(db.movePlaylistSong(pidA, songIds.at(2), 0));
    songs = db.playlistSongs(pidA);
    ASSERT_EQ(songs.size(), 3);
    EXPECT_EQ(songs.at(0).id, songIds.at(2));
    EXPECT_EQ(songs.at(1).id, songIds.at(0));
    EXPECT_EQ(songs.at(2).id, songIds.at(1));

    // removeFrom tengah; position ke-rapikan
    EXPECT_TRUE(db.removeFromPlaylist(pidA, songIds.at(0)));
    songs = db.playlistSongs(pidA);
    ASSERT_EQ(songs.size(), 2);
    EXPECT_EQ(songs.at(0).id, songIds.at(2));
    EXPECT_EQ(songs.at(1).id, songIds.at(1));

    // songCount ikut diperbarui di allPlaylists
    playlists = db.allPlaylists();
    qint64 countA = 0;
    for (const auto &row : playlists)
        if (row.id == pidA)
            countA = row.songCount;
    EXPECT_EQ(countA, 2);

    // delete playlist ikut hapus baris isi
    EXPECT_TRUE(db.deletePlaylist(pidA));
    EXPECT_TRUE(db.playlistSongs(pidA).isEmpty());
    playlists = db.allPlaylists();
    ASSERT_EQ(playlists.size(), 1);
    EXPECT_EQ(playlists.at(0).id, pidB);
}

TEST(LibraryDbTest, PlaylistDuplicateSongIgnoredAndGuardRails)
{
    QTemporaryDir dir;
    LibraryDatabase db(openTempDb(dir));
    ASSERT_TRUE(db.isOpen());
    const auto songIds = seedSongs(db);

    const qint64 pid = db.createPlaylist(QStringLiteral("Solo"));
    ASSERT_GT(pid, 0);

    EXPECT_TRUE(db.addToPlaylist(pid, songIds.at(0)));
    EXPECT_FALSE(db.addToPlaylist(pid, songIds.at(0)));   // INSERT OR IGNORE
    EXPECT_EQ(db.playlistSongs(pid).size(), 1);

    // baris yang tak ada di playlist tidak bisa dielap/dipindah
    EXPECT_FALSE(db.removeFromPlaylist(pid, songIds.at(1)));
    EXPECT_FALSE(db.movePlaylistSong(pid, songIds.at(1), 0));
    // out-of-range move
    EXPECT_FALSE(db.movePlaylistSong(pid, songIds.at(0), 5));

    // nama kosong ditolak
    const qint64 badId = db.createPlaylist(QStringLiteral("   "));
    EXPECT_EQ(badId, -1);
    EXPECT_FALSE(db.renamePlaylist(pid, QStringLiteral("   ")));

    // sampul tersimpan
    EXPECT_TRUE(db.setPlaylistImage(pid, QStringLiteral("file:///X:/cover.png")));
    EXPECT_EQ(db.allPlaylists().at(0).image, QStringLiteral("file:///X:/cover.png"));
}
