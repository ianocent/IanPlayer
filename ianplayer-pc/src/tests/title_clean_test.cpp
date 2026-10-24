#include <gtest/gtest.h>

#include "core/title_cleaner.h"

// Port 1:1 SongTags.kt — parity #30 (target M1.1 + M4.3)

TEST(CleanTitleTest, StripsOfficialVideoSuffix)
{
    EXPECT_EQ(SongTags::cleanTitle("Song (Official Video)"), "Song");
    EXPECT_EQ(SongTags::cleanTitle("Song [Official Music Video]"), "Song");
    EXPECT_EQ(SongTags::cleanTitle("Song (Music Video)"), "Song");
}

TEST(CleanTitleTest, StripsLyricsAndAudioJunk)
{
    EXPECT_EQ(SongTags::cleanTitle("Song [Lyrics]"), "Song");
    EXPECT_EQ(SongTags::cleanTitle("Song (Lyric Video)"), "Song");
    EXPECT_EQ(SongTags::cleanTitle("Song (Official Audio)"), "Song");
    EXPECT_EQ(SongTags::cleanTitle("Song [Official MV]"), "Song");
    EXPECT_EQ(SongTags::cleanTitle("Song [Official Visualizer]"), "Song");
}

TEST(CleanTitleTest, BareMvMarkerWithoutOfficialStays)
{
    // 1:1 Kotlin: [MV] / [Visualizer] cuma dibersihkan kalau ada prefix
    // "official" (reguler #3); tanpa prefix regex #4 tidak menyentuhnya.
    EXPECT_EQ(SongTags::cleanTitle("Song [MV]"), "Song [MV]");
    EXPECT_EQ(SongTags::cleanTitle("Song [Visualizer]"), "Song [Visualizer]");
}

TEST(CleanTitleTest, StripsDashAndPipeSuffix)
{
    EXPECT_EQ(SongTags::cleanTitle("Song - Official Music Video"), "Song");
    EXPECT_EQ(SongTags::cleanTitle("Song - Official Video"), "Song");
    EXPECT_EQ(SongTags::cleanTitle("Song - Lyrics"), "Song");
    EXPECT_EQ(SongTags::cleanTitle("Song | Official Video"), "Song");
    EXPECT_EQ(SongTags::cleanTitle("Song - Lyric Video"), "Song");
}

TEST(CleanTitleTest, KeepsHdHq4kMarkers)
{
    EXPECT_EQ(SongTags::cleanTitle("Song (HD)"), "Song");
    EXPECT_EQ(SongTags::cleanTitle("Song (4K)"), "Song");
    EXPECT_EQ(SongTags::cleanTitle("Song (M/V)"), "Song");
}

TEST(CleanTitleTest, StripsStackedJunkUntilStable)
{
    // loop Kotlin: junk bertumpuk di-strip sampai tidak berubah
    EXPECT_EQ(SongTags::cleanTitle("Song (Official Video) [Lyrics] (HD)"), "Song");
}

TEST(CleanTitleTest, KeepsNormalTitle)
{
    EXPECT_EQ(SongTags::cleanTitle("Let It Be (Remastered)"), "Let It Be (Remastered)");
    EXPECT_EQ(SongTags::cleanTitle("Plain Title"), "Plain Title");
}

TEST(CleanTitleTest, BlankResultFallsBackToRaw)
{
    EXPECT_EQ(SongTags::cleanTitle("(Official Video)"), "(Official Video)");
}

TEST(CleanTitleTest, TrimsTrailingSeparators)
{
    EXPECT_EQ(SongTags::cleanTitle("Song -"), "Song");
    EXPECT_EQ(SongTags::cleanTitle("Song (Official Video) -"), "Song");
    EXPECT_EQ(SongTags::cleanTitle("Song ["), "Song");
}

TEST(IsJunkArtistTest, DetectsJunk)
{
    EXPECT_TRUE(SongTags::isJunkArtist(""));
    EXPECT_TRUE(SongTags::isJunkArtist("Unknown Artist"));
    EXPECT_TRUE(SongTags::isJunkArtist("unknown"));
    EXPECT_TRUE(SongTags::isJunkArtist("YouTube"));
    EXPECT_TRUE(SongTags::isJunkArtist("Various Artists"));
    EXPECT_FALSE(SongTags::isJunkArtist("Eminem"));
}

TEST(ResolveTest, TopicAndVevoCleanup)
{
    auto r = SongTags::resolve("Ed Sheeran - Perfect", "Ed Sheeran - Topic");
    EXPECT_EQ(r.first, "Perfect");
    EXPECT_EQ(r.second, "Ed Sheeran");

    r = SongTags::resolve("Perfect", "EdSheeranVEVO");
    EXPECT_EQ(r.second, "EdSheeran");
}

TEST(ResolveTest, TrustedArtistSuffixInTitle)
{
    // re-upload YouTube: "Without Me - Eminem", artist terpercaya "Eminem"
    auto r = SongTags::resolve("Without Me - Eminem", "Eminem");
    EXPECT_EQ(r.first, "Without Me");
    EXPECT_EQ(r.second, "Eminem");

    // urutan "Eminem - Without Me": prefix dipangkas
    r = SongTags::resolve("Eminem - Without Me", "Eminem");
    EXPECT_EQ(r.first, "Without Me");
    EXPECT_EQ(r.second, "Eminem");
}

TEST(ResolveTest, JunkArtistDashSplit)
{
    // artist junk + "Artist - Title" -> pecah jadi left=artist
    auto r = SongTags::resolve("Ed Sheeran - Perfect", "Unknown Artist");
    EXPECT_EQ(r.first, "Perfect");
    EXPECT_EQ(r.second, "Ed Sheeran");
}

TEST(ResolveTest, JunkArtistNoDashSplitKeepsTitle)
{
    // 1:1 Kotlin: artist junk TETAP dipertahankan kalau tidak blank —
    // hanya blank yang diganti "Unknown Artist" di akhir resolve().
    auto r = SongTags::resolve("Just A Song", "YouTube");
    EXPECT_EQ(r.first, "Just A Song");
    EXPECT_EQ(r.second, "YouTube");
}

TEST(ResolveTest, LeftTooLongNotTreatedAsArtist)
{
    // left candidate > 60 char -> tidak dianggap artist
    const QString left = QString(61, QChar('A'));
    auto r = SongTags::resolve(left + " - " + "Real Title", "Unknown Artist");
    EXPECT_EQ(r.first, left + " - Real Title");
    EXPECT_EQ(r.second, "Unknown Artist");
}

TEST(ResolveTest, LeftWithParenthesisNotTreatedAsArtist)
{
    auto r = SongTags::resolve("Live (2004) - Real Title", "Unknown Artist");
    // "(2004)" membuat left tidak tampak seperti artist
    EXPECT_EQ(r.first, "Live (2004) - Real Title");
    EXPECT_EQ(r.second, "Unknown Artist");
}

TEST(ResolveTest, BlankArtistDefaultsToUnknown)
{
    auto r = SongTags::resolve("Song", "  ");
    EXPECT_EQ(r.first, "Song");
    EXPECT_EQ(r.second, "Unknown Artist");
}
