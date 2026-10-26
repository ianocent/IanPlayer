#pragma once

#include <QObject>
#include <QVariant>
#include <QString>
#include <QVector>
#include <QUrl>
#include <QSettings>
#include <QJsonArray>
#include <QJsonObject>
#include <QSet>
#include <functional>

// Satu sumber kebenaran queue (paritas PlayerGateway.kt). Engine mpv hanya
// eksekutor; QueueManager yang pegang daftar, shuffle/repeat, persist, auto-next.
class QueueManager : public QObject
{
    Q_OBJECT

    Q_PROPERTY(bool isPlaying READ isPlaying NOTIFY isPlayingChanged)
    Q_PROPERTY(QString currentSongKey READ currentSongKey NOTIFY currentSongChanged)
    Q_PROPERTY(QString currentTitle READ currentTitle NOTIFY currentSongChanged)
    Q_PROPERTY(QString currentArtist READ currentArtist NOTIFY currentSongChanged)
    Q_PROPERTY(QString currentArt READ currentArt NOTIFY currentSongChanged)
    Q_PROPERTY(qint64 currentDurationMs READ currentDurationMs NOTIFY currentSongChanged)
    Q_PROPERTY(bool shuffleOn READ shuffleOn NOTIFY shuffleChanged)
    Q_PROPERTY(int repeatMode READ repeatMode NOTIFY repeatModeChanged)
    Q_PROPERTY(int crossfadeMs READ crossfadeMs WRITE setCrossfadeMs NOTIFY crossfadeMsChanged)
    Q_PROPERTY(int currentIndex READ currentIndex NOTIFY currentSongChanged)
    Q_PROPERTY(int queueCount READ queueCount NOTIFY queueChanged)

public:
    struct SongInfo
    {
        QString key;         // "local:<id>" | "yt:<videoId>"
        QString title;
        QString artist;
        QString album;
        qint64 durationMs = 0;
        QString art;         // 256/64 path or remote url
        QString playUrl;     // local path / resolved stream url
        bool isStream = false;
        bool needsResolution = false;
    };

    struct EngineHooks
    {
        std::function<void(const QUrl &)> load;
        std::function<void()> play;
        std::function<void()> pause;
        std::function<double()> position;
        std::function<double()> duration;
        std::function<bool()> paused;
        std::function<void(int)> setVolume;
        std::function<void(qint64)> seekToMs;
    };

    enum RepeatMode { RepeatOff = 0, RepeatOne = 1, RepeatAll = 2 };
    enum AutoFillSource { AutoFillDefault = 0, AutoFillPlaylist, AutoFillGenre };

    QueueManager(EngineHooks hooks,
                 std::function<bool(const QString &, SongInfo &)> resolver,
                 QSettings *settings,
                 QObject *parent = nullptr);

    // ---- state ----
    QString currentSongKey() const { return m_currentKey; }
    QString currentTitle() const { return m_current.title; }
    QString currentArtist() const { return m_current.artist; }
    QString currentArt() const { return m_current.art; }
    qint64 currentDurationMs() const { return m_current.durationMs; }
    bool shuffleOn() const { return m_shuffleOn; }
    bool isPlaying() const { return m_isPlaying; }
    void setPlaying(bool playing);

    Q_INVOKABLE qint64 positionMs() const;
    Q_INVOKABLE qint64 durationMsCurrent() const { return m_current.durationMs; }
    int repeatMode() const { return m_repeatMode; }
    int crossfadeMs() const { return m_crossfadeMs; }
    void setCrossfadeMs(int ms);
    int currentIndex() const { return m_currentIndex; }
    int queueCount() const { return int(m_queue.size()); }

    const QVector<SongInfo> &queue() const { return m_queue; }

    // ---- lifecycle / restore ----
    void restoreState();   // baca QSettings, set queue tanpa play
    void saveState();

    // ---- queue ops (paritas PlayerGateway.kt) ----
    Q_INVOKABLE void playNow(const QStringList &queueKeys, int startIndex = 0);
    Q_INVOKABLE void playSingle(const QString &songKey);
    Q_INVOKABLE void playSongAt(int index);
    Q_INVOKABLE void playNext();
    Q_INVOKABLE void playPrevious();
    Q_INVOKABLE void addToQueue(const QString &songKey);
    Q_INVOKABLE void playNextInQueue(const QString &songKey);
    Q_INVOKABLE void removeSongFromQueue(const QString &songKey);
    Q_INVOKABLE void reorderUpNext(int fromUpNext, int toUpNext);
    Q_INVOKABLE void clearQueue();
    Q_INVOKABLE void toggleShuffle();
    Q_INVOKABLE void toggleRepeat();
    Q_INVOKABLE void togglePlayPause();
    Q_INVOKABLE void play();
    Q_INVOKABLE void pause();
    Q_INVOKABLE void seekToMs(qint64 ms);
    Q_INVOKABLE void setVolume(int percent);
    Q_INVOKABLE void stop();

    // ---- engine callbacks ----
    void onTrackEnded();          // mpv EOF
    void onPlaybackError(const QString &message);
    void onPositionTick(qint64 positionMs, qint64 durationMs); // prefetch hook 8s

    // ---- accessors untuk UI (M2.2) ----
    Q_INVOKABLE QString queueKeyAt(int index) const;
    Q_INVOKABLE QString queueTitleAt(int index) const;

signals:
    void currentSongChanged();
    void queueChanged();
    void isPlayingChanged(bool playing);
    void shuffleChanged();
    void crossfadeMsChanged();
    void repeatModeChanged();
    void playbackError(const QString &message);
    void songStarted(const QString &key);   // untuk LibraryService.recordLocalPlayed

private:
    // internal
    SongInfo resolveOrPlaceholder(const QString &key);
    void publishCurrentSong(const SongInfo &song, int index, bool fireSongStarted);
    void loadCurrentAndPlay();
    void doLoadCurrentAndPlay();
    void handleResolveFailure();
    void prefetchNextIfNeeded();
    void saveStateIfNeeded();

    // persistence helpers
    QJsonArray queueToJson() const;
    bool restoreQueueFromJson(const QJsonArray &arr);

    EngineHooks m_hooks;
    std::function<bool(const QString &, SongInfo &)> m_resolver;

    QVector<SongInfo> m_queue;
    SongInfo m_current{};
    int m_currentIndex = -1;
    QString m_currentKey;
    bool m_shuffleOn = false;
    int m_crossfadeMs = 0;
    bool m_isPlaying = false;
    int m_repeatMode = RepeatOff;
    QVector<SongInfo> m_baseQueueBeforeShuffle;
    int m_continuousFailures = 0;
    int m_playbackGeneration = 0;
    QSettings *m_settings = nullptr;

    // prefetch bookkeeping
    QSet<QString> m_prefetchingKeys;
};
