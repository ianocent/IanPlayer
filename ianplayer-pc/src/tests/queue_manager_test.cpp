#include <gtest/gtest.h>

#include "playback/queue_manager.h"

#include <QTemporaryDir>
#include <QSettings>
#include <QStringList>

namespace {

struct FakeEngine
{
    QStringList loaded;
    int playCount = 0;
    int pauseCount = 0;
    int seekCount = 0;
    qint64 lastSeekMs = -1;
    double position = 0.0;
    double duration = 100.0;
    bool paused = true;
};

QueueManager::SongInfo makeSong(int n)
{
    QueueManager::SongInfo s;
    s.key = QStringLiteral("local:%1").arg(n);
    s.title = QStringLiteral("Song %1").arg(n);
    s.artist = QStringLiteral("Artist");
    s.album = QStringLiteral("Album");
    s.durationMs = 100000;
    s.playUrl = QStringLiteral("C:/music/s%1.mp3").arg(n);
    s.isStream = false;
    return s;
}

} // namespace

TEST(QueueManagerTest, PlayNowSetsQueueAndLoads)
{
    QTemporaryDir dir;
    QSettings settings(dir.filePath(QStringLiteral("q.ini")), QSettings::IniFormat);
    FakeEngine fe;
    QueueManager::EngineHooks hooks;
    hooks.load = [&fe](const QUrl &u) { fe.loaded.append(u.toLocalFile()); };
    hooks.play = [&fe]() { ++fe.playCount; fe.paused = false; };
    hooks.pause = [&fe]() { ++fe.pauseCount; fe.paused = true; };
    hooks.position = [&fe]() { return fe.position; };
    hooks.duration = [&fe]() { return fe.duration; };
    hooks.paused = [&fe]() { return fe.paused; };
    hooks.setVolume = [](int) {};
    hooks.seekToMs = [&fe](qint64 ms) { ++fe.seekCount; fe.lastSeekMs = ms; };

    auto resolver = [](const QString &key, QueueManager::SongInfo &out) {
        const int n = key.mid(6).toInt();
        out = makeSong(n);
        return true;
    };

    bool started = false;
    QueueManager qm(hooks, resolver, &settings);
    QObject::connect(&qm, &QueueManager::songStarted, [&started](const QString &) { started = true; });

    qm.playNow({QStringLiteral("local:1"), QStringLiteral("local:2")}, 0);

    EXPECT_EQ(qm.currentSongKey(), QStringLiteral("local:1"));
    EXPECT_EQ(qm.queueCount(), 2);
    ASSERT_FALSE(fe.loaded.isEmpty());
    EXPECT_TRUE(fe.loaded.last().endsWith(QStringLiteral("s1.mp3")));
    EXPECT_TRUE(started);
    EXPECT_EQ(fe.playCount, 1);
}

TEST(QueueManagerTest, PlayNextAdvancesAndWrapsWithRepeatAll)
{
    QTemporaryDir dir;
    QSettings settings(dir.filePath(QStringLiteral("q.ini")), QSettings::IniFormat);
    FakeEngine fe;
    QueueManager::EngineHooks hooks;
    hooks.load = [&fe](const QUrl &u) { fe.loaded.append(u.toLocalFile()); };
    hooks.play = [&fe]() { ++fe.playCount; fe.paused = false; };
    hooks.pause = [&fe]() { ++fe.pauseCount; fe.paused = true; };
    hooks.position = [&fe]() { return fe.position; };
    hooks.duration = [&fe]() { return fe.duration; };
    hooks.paused = [&fe]() { return fe.paused; };
    hooks.setVolume = [](int) {};
    hooks.seekToMs = [&fe](qint64 ms) { ++fe.seekCount; fe.lastSeekMs = ms; };

    auto resolver = [](const QString &key, QueueManager::SongInfo &out) {
        out = makeSong(key.mid(6).toInt());
        return true;
    };

    QueueManager qm(hooks, resolver, &settings);
    qm.playNow({QStringLiteral("local:1"), QStringLiteral("local:2")}, 0);

    // Off -> All
    qm.toggleRepeat();
    EXPECT_EQ(qm.repeatMode(), QueueManager::RepeatAll);

    qm.playNext();
    EXPECT_EQ(qm.currentSongKey(), QStringLiteral("local:2"));

    qm.playNext(); // last -> wrap
    EXPECT_EQ(qm.currentSongKey(), QStringLiteral("local:1"));
}

TEST(QueueManagerTest, PlayNextStopsAtEndWithoutRepeat)
{
    QTemporaryDir dir;
    QSettings settings(dir.filePath(QStringLiteral("q.ini")), QSettings::IniFormat);
    FakeEngine fe;
    QueueManager::EngineHooks hooks;
    hooks.load = [&fe](const QUrl &u) { fe.loaded.append(u.toLocalFile()); };
    hooks.play = [&fe]() { ++fe.playCount; fe.paused = false; };
    hooks.pause = [&fe]() { ++fe.pauseCount; fe.paused = true; };
    hooks.position = [&fe]() { return fe.position; };
    hooks.duration = [&fe]() { return fe.duration; };
    hooks.paused = [&fe]() { return fe.paused; };
    hooks.setVolume = [](int) {};
    hooks.seekToMs = [&fe](qint64 ms) { ++fe.seekCount; fe.lastSeekMs = ms; };

    auto resolver = [](const QString &key, QueueManager::SongInfo &out) {
        out = makeSong(key.mid(6).toInt());
        return true;
    };

    QueueManager qm(hooks, resolver, &settings);
    qm.playNow({QStringLiteral("local:1"), QStringLiteral("local:2")}, 0);
    qm.playNext();
    EXPECT_EQ(qm.currentSongKey(), QStringLiteral("local:2"));

    qm.playNext(); // no wrap
    EXPECT_EQ(qm.currentSongKey(), QStringLiteral("local:2"));
}

TEST(QueueManagerTest, PlayPreviousSeeksIfPositionGt3)
{
    QTemporaryDir dir;
    QSettings settings(dir.filePath(QStringLiteral("q.ini")), QSettings::IniFormat);
    FakeEngine fe;
    fe.position = 5.0;
    QueueManager::EngineHooks hooks;
    hooks.load = [&fe](const QUrl &u) { fe.loaded.append(u.toLocalFile()); };
    hooks.play = [&fe]() { ++fe.playCount; fe.paused = false; };
    hooks.pause = [&fe]() { ++fe.pauseCount; fe.paused = true; };
    hooks.position = [&fe]() { return fe.position; };
    hooks.duration = [&fe]() { return fe.duration; };
    hooks.paused = [&fe]() { return fe.paused; };
    hooks.setVolume = [](int) {};
    hooks.seekToMs = [&fe](qint64 ms) { ++fe.seekCount; fe.lastSeekMs = ms; };

    auto resolver = [](const QString &key, QueueManager::SongInfo &out) {
        out = makeSong(key.mid(6).toInt());
        return true;
    };

    QueueManager qm(hooks, resolver, &settings);
    qm.playNow({QStringLiteral("local:1"), QStringLiteral("local:2")}, 1);
    fe.position = 5.0;
    qm.playPrevious();
    EXPECT_EQ(fe.seekCount, 1);
    EXPECT_EQ(fe.lastSeekMs, 0);
    EXPECT_EQ(qm.currentSongKey(), QStringLiteral("local:2"));
}

TEST(QueueManagerTest, RemoveSongFromQueue)
{
    QTemporaryDir dir;
    QSettings settings(dir.filePath(QStringLiteral("q.ini")), QSettings::IniFormat);
    FakeEngine fe;
    QueueManager::EngineHooks hooks;
    hooks.load = [&fe](const QUrl &u) { fe.loaded.append(u.toLocalFile()); };
    hooks.play = [&fe]() { ++fe.playCount; fe.paused = false; };
    hooks.pause = [&fe]() { ++fe.pauseCount; fe.paused = true; };
    hooks.position = [&fe]() { return fe.position; };
    hooks.duration = [&fe]() { return fe.duration; };
    hooks.paused = [&fe]() { return fe.paused; };
    hooks.setVolume = [](int) {};
    hooks.seekToMs = [&fe](qint64 ms) { ++fe.seekCount; fe.lastSeekMs = ms; };

    auto resolver = [](const QString &key, QueueManager::SongInfo &out) {
        out = makeSong(key.mid(6).toInt());
        return true;
    };

    QueueManager qm(hooks, resolver, &settings);
    qm.playNow({QStringLiteral("local:1"), QStringLiteral("local:2"), QStringLiteral("local:3")}, 0);
    qm.removeSongFromQueue(QStringLiteral("local:2"));
    EXPECT_EQ(qm.queueCount(), 2);
    EXPECT_TRUE(qm.queueKeyAt(0) == QStringLiteral("local:1"));
    EXPECT_TRUE(qm.queueKeyAt(1) == QStringLiteral("local:3"));
}

TEST(QueueManagerTest, ReorderUpNext)
{
    QTemporaryDir dir;
    QSettings settings(dir.filePath(QStringLiteral("q.ini")), QSettings::IniFormat);
    FakeEngine fe;
    QueueManager::EngineHooks hooks;
    hooks.load = [&fe](const QUrl &u) { fe.loaded.append(u.toLocalFile()); };
    hooks.play = [&fe]() { ++fe.playCount; fe.paused = false; };
    hooks.pause = [&fe]() { ++fe.pauseCount; fe.paused = true; };
    hooks.position = [&fe]() { return fe.position; };
    hooks.duration = [&fe]() { return fe.duration; };
    hooks.paused = [&fe]() { return fe.paused; };
    hooks.setVolume = [](int) {};
    hooks.seekToMs = [&fe](qint64 ms) { ++fe.seekCount; fe.lastSeekMs = ms; };

    auto resolver = [](const QString &key, QueueManager::SongInfo &out) {
        out = makeSong(key.mid(6).toInt());
        return true;
    };

    QueueManager qm(hooks, resolver, &settings);
    qm.playNow({QStringLiteral("local:1"), QStringLiteral("local:2"), QStringLiteral("local:3")}, 0);
    qm.reorderUpNext(0, 1); // swap up-next first two
    EXPECT_EQ(qm.queueKeyAt(0), QStringLiteral("local:1"));
    EXPECT_EQ(qm.queueKeyAt(1), QStringLiteral("local:3"));
    EXPECT_EQ(qm.queueKeyAt(2), QStringLiteral("local:2"));
}

TEST(QueueManagerTest, ToggleShuffleCurrentFirst)
{
    QTemporaryDir dir;
    QSettings settings(dir.filePath(QStringLiteral("q.ini")), QSettings::IniFormat);
    FakeEngine fe;
    QueueManager::EngineHooks hooks;
    hooks.load = [&fe](const QUrl &u) { fe.loaded.append(u.toLocalFile()); };
    hooks.play = [&fe]() { ++fe.playCount; fe.paused = false; };
    hooks.pause = [&fe]() { ++fe.pauseCount; fe.paused = true; };
    hooks.position = [&fe]() { return fe.position; };
    hooks.duration = [&fe]() { return fe.duration; };
    hooks.paused = [&fe]() { return fe.paused; };
    hooks.setVolume = [](int) {};
    hooks.seekToMs = [&fe](qint64 ms) { ++fe.seekCount; fe.lastSeekMs = ms; };

    auto resolver = [](const QString &key, QueueManager::SongInfo &out) {
        out = makeSong(key.mid(6).toInt());
        return true;
    };

    QueueManager qm(hooks, resolver, &settings);
    qm.playNow({QStringLiteral("local:1"), QStringLiteral("local:2"), QStringLiteral("local:3")}, 0);
    qm.toggleShuffle();
    EXPECT_TRUE(qm.shuffleOn());
    EXPECT_EQ(qm.queueKeyAt(0), QStringLiteral("local:1"));
    EXPECT_EQ(qm.queueCount(), 3);
}

TEST(QueueManagerTest, ToggleRepeatCycle)
{
    QTemporaryDir dir;
    QSettings settings(dir.filePath(QStringLiteral("q.ini")), QSettings::IniFormat);
    FakeEngine fe;
    QueueManager::EngineHooks hooks;
    hooks.load = [&fe](const QUrl &u) { fe.loaded.append(u.toLocalFile()); };
    hooks.play = [&fe]() { ++fe.playCount; fe.paused = false; };
    hooks.pause = [&fe]() { ++fe.pauseCount; fe.paused = true; };
    hooks.position = [&fe]() { return fe.position; };
    hooks.duration = [&fe]() { return fe.duration; };
    hooks.paused = [&fe]() { return fe.paused; };
    hooks.setVolume = [](int) {};
    hooks.seekToMs = [&fe](qint64 ms) { ++fe.seekCount; fe.lastSeekMs = ms; };

    auto resolver = [](const QString &key, QueueManager::SongInfo &out) {
        out = makeSong(key.mid(6).toInt());
        return true;
    };

    QueueManager qm(hooks, resolver, &settings);
    EXPECT_EQ(qm.repeatMode(), QueueManager::RepeatOff);
    qm.toggleRepeat();
    EXPECT_EQ(qm.repeatMode(), QueueManager::RepeatAll);
    qm.toggleRepeat();
    EXPECT_EQ(qm.repeatMode(), QueueManager::RepeatOne);
    qm.toggleRepeat();
    EXPECT_EQ(qm.repeatMode(), QueueManager::RepeatOff);
}

TEST(QueueManagerTest, OnTrackEndedRepeatOneRestarts)
{
    QTemporaryDir dir;
    QSettings settings(dir.filePath(QStringLiteral("q.ini")), QSettings::IniFormat);
    FakeEngine fe;
    QueueManager::EngineHooks hooks;
    hooks.load = [&fe](const QUrl &u) { fe.loaded.append(u.toLocalFile()); };
    hooks.play = [&fe]() { ++fe.playCount; fe.paused = false; };
    hooks.pause = [&fe]() { ++fe.pauseCount; fe.paused = true; };
    hooks.position = [&fe]() { return fe.position; };
    hooks.duration = [&fe]() { return fe.duration; };
    hooks.paused = [&fe]() { return fe.paused; };
    hooks.setVolume = [](int) {};
    hooks.seekToMs = [&fe](qint64 ms) { ++fe.seekCount; fe.lastSeekMs = ms; };

    auto resolver = [](const QString &key, QueueManager::SongInfo &out) {
        out = makeSong(key.mid(6).toInt());
        return true;
    };

    QueueManager qm(hooks, resolver, &settings);
    qm.playNow({QStringLiteral("local:1")}, 0);
    qm.toggleRepeat(); // Off->All
    qm.toggleRepeat(); // All->One
    EXPECT_EQ(qm.repeatMode(), QueueManager::RepeatOne);

    const int playsBefore = fe.playCount;
    const int seeksBefore = fe.seekCount;
    qm.onTrackEnded();
    EXPECT_GT(fe.seekCount, seeksBefore);
    EXPECT_GT(fe.playCount, playsBefore);
    EXPECT_EQ(qm.currentSongKey(), QStringLiteral("local:1"));
}

TEST(QueueManagerTest, OnTrackEndedAutoNextAdvances)
{
    QTemporaryDir dir;
    QSettings settings(dir.filePath(QStringLiteral("q.ini")), QSettings::IniFormat);
    FakeEngine fe;
    QueueManager::EngineHooks hooks;
    hooks.load = [&fe](const QUrl &u) { fe.loaded.append(u.toLocalFile()); };
    hooks.play = [&fe]() { ++fe.playCount; fe.paused = false; };
    hooks.pause = [&fe]() { ++fe.pauseCount; fe.paused = true; };
    hooks.position = [&fe]() { return fe.position; };
    hooks.duration = [&fe]() { return fe.duration; };
    hooks.paused = [&fe]() { return fe.paused; };
    hooks.setVolume = [](int) {};
    hooks.seekToMs = [&fe](qint64 ms) { ++fe.seekCount; fe.lastSeekMs = ms; };

    auto resolver = [](const QString &key, QueueManager::SongInfo &out) {
        out = makeSong(key.mid(6).toInt());
        return true;
    };

    QueueManager qm(hooks, resolver, &settings);
    qm.playNow({QStringLiteral("local:1"), QStringLiteral("local:2")}, 0);
    qm.onTrackEnded();
    EXPECT_EQ(qm.currentSongKey(), QStringLiteral("local:2"));
}

TEST(QueueManagerTest, RestorePersistence)
{
    QTemporaryDir dir;
    const QString ini = dir.filePath(QStringLiteral("q.ini"));
    {
        QSettings settings(ini, QSettings::IniFormat);
        FakeEngine fe;
        QueueManager::EngineHooks hooks;
        hooks.load = [&fe](const QUrl &u) { fe.loaded.append(u.toLocalFile()); };
        hooks.play = [&fe]() { ++fe.playCount; fe.paused = false; };
        hooks.pause = [&fe]() { ++fe.pauseCount; fe.paused = true; };
        hooks.position = [&fe]() { return fe.position; };
        hooks.duration = [&fe]() { return fe.duration; };
        hooks.paused = [&fe]() { return fe.paused; };
        hooks.setVolume = [](int) {};
        hooks.seekToMs = [&fe](qint64 ms) { ++fe.seekCount; fe.lastSeekMs = ms; };

        auto resolver = [](const QString &key, QueueManager::SongInfo &out) {
            out = makeSong(key.mid(6).toInt());
            return true;
        };

        QueueManager qm(hooks, resolver, &settings);
        qm.playNow({QStringLiteral("local:1"), QStringLiteral("local:2")}, 0);
    }

    QSettings settings2(ini, QSettings::IniFormat);
    FakeEngine fe2;
    QueueManager::EngineHooks hooks2;
    hooks2.load = [&fe2](const QUrl &u) { fe2.loaded.append(u.toLocalFile()); };
    hooks2.play = [&fe2]() { ++fe2.playCount; fe2.paused = false; };
    hooks2.pause = [&fe2]() { ++fe2.pauseCount; fe2.paused = true; };
    hooks2.position = [&fe2]() { return fe2.position; };
    hooks2.duration = [&fe2]() { return fe2.duration; };
    hooks2.paused = [&fe2]() { return fe2.paused; };
    hooks2.setVolume = [](int) {};
    hooks2.seekToMs = [&fe2](qint64 ms) { ++fe2.seekCount; fe2.lastSeekMs = ms; };

    auto resolver2 = [](const QString &key, QueueManager::SongInfo &out) {
        out = makeSong(key.mid(6).toInt());
        return true;
    };

    QueueManager qm2(hooks2, resolver2, &settings2);
    EXPECT_EQ(qm2.currentSongKey(), QStringLiteral("local:1"));
    EXPECT_EQ(qm2.queueCount(), 2);
}

TEST(QueueManagerTest, ErrorHandlingStopsAfterThree)
{
    QTemporaryDir dir;
    QSettings settings(dir.filePath(QStringLiteral("q.ini")), QSettings::IniFormat);
    FakeEngine fe;
    QueueManager::EngineHooks hooks;
    hooks.load = [&fe](const QUrl &u) { fe.loaded.append(u.toLocalFile()); };
    hooks.play = [&fe]() { ++fe.playCount; fe.paused = false; };
    hooks.pause = [&fe]() { ++fe.pauseCount; fe.paused = true; };
    hooks.position = [&fe]() { return fe.position; };
    hooks.duration = [&fe]() { return fe.duration; };
    hooks.paused = [&fe]() { return fe.paused; };
    hooks.setVolume = [](int) {};
    hooks.seekToMs = [&fe](qint64 ms) { ++fe.seekCount; fe.lastSeekMs = ms; };

    auto resolver = [](const QString &key, QueueManager::SongInfo &out) {
        out = makeSong(key.mid(6).toInt());
        return true;
    };

    QueueManager qm(hooks, resolver, &settings);
    qm.playNow({QStringLiteral("local:1"), QStringLiteral("local:2")}, 0);
    const int pauseBefore = fe.pauseCount;
    qm.onPlaybackError(QStringLiteral("boom1"));
    qm.onPlaybackError(QStringLiteral("boom2"));
    qm.onPlaybackError(QStringLiteral("boom3"));
    EXPECT_GT(fe.pauseCount, pauseBefore);
}