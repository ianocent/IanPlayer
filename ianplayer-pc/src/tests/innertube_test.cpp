#include <gtest/gtest.h>

#include "net/innertube_client.h"

TEST(InnertubeTest, GenreAndMoodCounts)
{
    EXPECT_EQ(InnertubeClient::genres().size(), 12);
    EXPECT_EQ(InnertubeClient::moods().size(), 12);
}

TEST(InnertubeTest, NoiseFilter)
{
    EXPECT_TRUE(InnertubeClient::isNoise(QStringLiteral("Best of 2024 Compilation"), QStringLiteral("Various")));
    EXPECT_TRUE(InnertubeClient::isNoise(QStringLiteral("Song (Karaoke)"), QStringLiteral("Artist")));
    EXPECT_TRUE(InnertubeClient::isNoise(QStringLiteral("Hit Song"), QStringLiteral("Arijit Singh Hindi")));
    EXPECT_FALSE(InnertubeClient::isNoise(QStringLiteral("Bohemian Rhapsody"), QStringLiteral("Queen")));
    EXPECT_TRUE(InnertubeClient::isNoise(QStringLiteral(""), QStringLiteral("Artist")));
}