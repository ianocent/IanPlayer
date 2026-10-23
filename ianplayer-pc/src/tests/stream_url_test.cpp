#include <gtest/gtest.h>

#include "net/stream_url_resolver.h"

// Parser `expire=` googlevideo — penting untuk keputusan cache:
// value = xamarin epoch UNIX dalam DETIK.

TEST(ExtractExpireEpoch, ParsesStandardYoutubePlaybackUrl)
{
    const QUrl u(QStringLiteral(
        "https://rr5---sn-1abcd.googlevideo.com/videoplayback?"
        "expire=1719628800&ei=abc&id=oMk&ip=1.2.3.4&key=xyz&requiressl=yes"));
    EXPECT_EQ(StreamUrlResolver::extractExpireEpoch(u), 1719628800);
}

TEST(ExtractExpireEpoch, ParsesExpireAtEndOfQueryWithoutTrailingAmpersand)
{
    const QUrl u(QStringLiteral(
        "https://rr3---sn-x.googlevideo.com/videoplayback?id=abc&expire=1719628800"));
    EXPECT_EQ(StreamUrlResolver::extractExpireEpoch(u), 1719628800);
}

TEST(ExtractExpireEpoch, ReturnsMinusOneWhenExpireMissing)
{
    const QUrl u(QStringLiteral(
        "https://rr3---sn-x.googlevideo.com/videoplayback?id=abc&ip=1.2.3.4"));
    EXPECT_EQ(StreamUrlResolver::extractExpireEpoch(u), -1);
}

TEST(ExtractExpireEpoch, ReturnsMinusOneOnNonNumericValue)
{
    const QUrl u(QStringLiteral(
        "https://rr3---sn-x.googlevideo.com/videoplayback?id=abc&expire=abc"));
    EXPECT_EQ(StreamUrlResolver::extractExpireEpoch(u), -1);
}

TEST(ExtractVideoId, ExtractsFromWatchV)
{
    const QUrl u(QStringLiteral("https://www.youtube.com/watch?v=dQw4w9WgXcQ"));
    EXPECT_EQ(StreamUrlResolver::extractVideoId(u), QStringLiteral("dQw4w9WgXcQ"));
}

TEST(ExtractVideoId, ExtractsFromMusicYoutubeWatch)
{
    const QUrl u(QStringLiteral("https://music.youtube.com/watch?v=dQw4w9WgXcQ&si=zzz"));
    EXPECT_EQ(StreamUrlResolver::extractVideoId(u), QStringLiteral("dQw4w9WgXcQ"));
}

TEST(ExtractVideoId, ExtractsFromShortenedYoutuBe)
{
    const QUrl u(QStringLiteral("https://youtu.be/dQw4w9WgXcQ?t=12"));
    EXPECT_EQ(StreamUrlResolver::extractVideoId(u), QStringLiteral("dQw4w9WgXcQ"));
}

TEST(ExtractVideoId, ExtractsFromShorts)
{
    const QUrl u(QStringLiteral("https://www.youtube.com/shorts/dQw4w9WgXcQ"));
    EXPECT_EQ(StreamUrlResolver::extractVideoId(u), QStringLiteral("dQw4w9WgXcQ"));
}

TEST(ExtractVideoId, EmptyOnNonYouTubeUrl)
{
    const QUrl u(QStringLiteral("https://example.com/watch?v=dQw4w9WgXcQ"));
    EXPECT_TRUE(StreamUrlResolver::extractVideoId(u).isEmpty());
}

TEST(IsYouTubeUrl, AcceptsCommonHosts)
{
    EXPECT_TRUE(StreamUrlResolver::isYouTubeUrl(
        QUrl(QStringLiteral("https://www.youtube.com/watch?v=x"))));
    EXPECT_TRUE(StreamUrlResolver::isYouTubeUrl(
        QUrl(QStringLiteral("https://music.youtube.com/watch?v=x"))));
    EXPECT_TRUE(StreamUrlResolver::isYouTubeUrl(
        QUrl(QStringLiteral("https://youtu.be/x"))));
    EXPECT_FALSE(StreamUrlResolver::isYouTubeUrl(
        QUrl(QStringLiteral("https://example.com/watch?v=x"))));
    EXPECT_FALSE(StreamUrlResolver::isYouTubeUrl(
        QUrl(QStringLiteral("https://notyoutube.com/watch?v=x"))));
}
