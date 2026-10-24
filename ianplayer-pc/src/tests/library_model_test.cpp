#include <gtest/gtest.h>

#include "models/library_song_model.h"
#include "models/library_proxy_model.h"
#include "db/library_database.h"

#include <QTemporaryDir>

namespace {

void upsertOne(LibraryDatabase &db, const QString &title, const QString &artist,
               const QString &album, qint64 albumId, qint64 artistId,
               qint64 addedTs)
{
    SongRecord rec;
    rec.path = QStringLiteral("C:/music/%1 - %2.mp3").arg(artist, title);
    rec.title = title;
    rec.artist = artist;
    rec.album = album;
    rec.albumId = albumId;
    rec.artistId = artistId;
    rec.durationMs = 120000;
    rec.modifiedTs = addedTs;
    rec.addedTs = addedTs;
    EXPECT_TRUE(db.upsertSong(rec));
}

} // namespace

TEST(LibraryModelTest, SongModelReloadsAndExposesRoles)
{
    QTemporaryDir dir;
    LibraryDatabase db(dir.filePath(QStringLiteral("m.db")));
    ASSERT_TRUE(db.isOpen());
    upsertOne(db, "Alpha", "Zeta", "First", 0, 0, 1000);
    upsertOne(db, "Beta", "Yan", "Second", 0, 0, 2000);

    LibrarySongModel model(&db);
    EXPECT_EQ(model.count(), 2);
    EXPECT_EQ(model.rowCount(), 2);
    EXPECT_TRUE(model.roleNames().contains(LibrarySongModel::TitleRole));
    EXPECT_TRUE(model.roleNames().contains(LibrarySongModel::SectionLetterRole));
    EXPECT_EQ(model.index(0, 0).data(LibrarySongModel::TitleRole).toString(),
              QStringLiteral("Alpha"));

    // reload setelah perubahan DB
    upsertOne(db, "Gamma", "Xavier", "First", 0, 0, 3000);
    model.reload();
    EXPECT_EQ(model.count(), 3);
}

TEST(LibraryProxyTest, SortModesAndSearchFilter)
{
    QTemporaryDir dir;
    LibraryDatabase db(dir.filePath(QStringLiteral("m.db")));
    ASSERT_TRUE(db.isOpen());
    upsertOne(db, "Zebra", "Alpha", "Album Z", 0, 0, 1000);
    upsertOne(db, "Apple", "Mid", "Album B", 0, 0, 2000);
    upsertOne(db, "Mango", "Beta", "Album A", 0, 0, 3000);

    LibrarySongModel model(&db);
    LibraryProxyModel proxy;
    proxy.setSourceModel(&model);
    proxy.sort(0);

    // sort judul: Apple, Mango, Zebra
    EXPECT_EQ(proxy.index(0, 0).data(LibrarySongModel::TitleRole).toString(), QStringLiteral("Apple"));
    EXPECT_EQ(proxy.index(2, 0).data(LibrarySongModel::TitleRole).toString(), QStringLiteral("Zebra"));
    EXPECT_EQ(proxy.count(), 3);

    // sort artis: Alpha(Zebra), Beta(Mango), Mid(Apple)
    proxy.setSortMode(int(LibraryProxyModel::SortMode::Artist));
    EXPECT_EQ(proxy.index(0, 0).data(LibrarySongModel::TitleRole).toString(), QStringLiteral("Zebra"));
    EXPECT_EQ(proxy.index(2, 0).data(LibrarySongModel::TitleRole).toString(), QStringLiteral("Apple"));

    // sort newly added: Mango (3000) first
    proxy.setSortMode(int(LibraryProxyModel::SortMode::RecentlyAdded));
    EXPECT_EQ(proxy.index(0, 0).data(LibrarySongModel::TitleRole).toString(), QStringLiteral("Mango"));

    // search case-insensitive
    proxy.setSearchText(QStringLiteral("mang"));
    EXPECT_EQ(proxy.count(), 1);
    EXPECT_EQ(proxy.index(0, 0).data(LibrarySongModel::TitleRole).toString(), QStringLiteral("Mango"));
    proxy.setSearchText(QString());
    EXPECT_EQ(proxy.count(), 3);
}

TEST(LibraryProxyTest, FastScrollerIndexForLetter)
{
    QTemporaryDir dir;
    LibraryDatabase db(dir.filePath(QStringLiteral("m.db")));
    ASSERT_TRUE(db.isOpen());
    upsertOne(db, "Apple", "A", "x", 0, 0, 1000);
    upsertOne(db, "Banana", "B", "x", 0, 0, 2000);
    upsertOne(db, "Cherry", "C", "x", 0, 0, 3000);

    LibrarySongModel model(&db);
    LibraryProxyModel proxy;
    proxy.setSourceModel(&model);
    proxy.sort(0);

    EXPECT_EQ(proxy.indexForLetter(int('A')), 0);
    EXPECT_EQ(proxy.indexForLetter(int('B')), 1);
    EXPECT_EQ(proxy.indexForLetter(int('C')), 2);
    EXPECT_EQ(proxy.indexForLetter(int('Q')), -1);
}
