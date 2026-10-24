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
