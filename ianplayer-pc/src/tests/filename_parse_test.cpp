#include <gtest/gtest.h>

#include "core/library_scanner.h"

TEST(ParseTaglessFileNameTest, DashSplit) {
    auto p = LibraryScanner::parseTaglessFileName("The Beatles - Hey Jude");
    ASSERT_TRUE(p.has_value());
    EXPECT_EQ(p->first, "The Beatles");
    EXPECT_EQ(p->second, "Hey Jude");
}

TEST(ParseTaglessFileNameTest, MultipleDashesFirstOnly) {
    auto p = LibraryScanner::parseTaglessFileName("Artist - Title - Extra");
    ASSERT_TRUE(p.has_value());
    EXPECT_EQ(p->first, "Artist");
    EXPECT_EQ(p->second, "Title - Extra");
}

TEST(ParseTaglessFileNameTest, NoDash) {
    auto p = LibraryScanner::parseTaglessFileName("JustASong");
    ASSERT_FALSE(p.has_value());
}

TEST(ParseTaglessFileNameTest, LeadingDashIndexZero) {
    auto p = LibraryScanner::parseTaglessFileName("- Title");
    ASSERT_FALSE(p.has_value());
}

TEST(ParseTaglessFileNameTest, EmptyParts) {
    auto p = LibraryScanner::parseTaglessFileName(" - Title");
    ASSERT_FALSE(p.has_value());
    p = LibraryScanner::parseTaglessFileName("Artist - ");
    ASSERT_FALSE(p.has_value());
}
