#include <QCoreApplication>
#include <gtest/gtest.h>

// gtest_main custom: QCoreApplication wajib dulu diangkat — QSqlDatabase/
// plugin sqlite & beberapa Qt singleton membutuhkan event loop instance
// (ctest Debug tanpa ini -> akses 0xc0000005 di ctor LibraryDatabase).
int main(int argc, char **argv)
{
    QCoreApplication app(argc, argv);
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}
