#pragma once

#include <QObject>
#include <QUrl>
#include <QString>

#include <atomic>
#include <thread>

#include <mpv/client.h>

// Wrapper libmpv minimal untuk M0.3.
// Event loop berjalan di thread sendiri (mpv_wait_event); API mpv thread-safe,
// tapi wait_event hanya boleh dari satu thread — jadi semua properti yang
// di-observe diproses di situ dan diteruskan ke main thread via signal Qt
// (auto-connection -> queued).
class PlaybackController : public QObject
{
    Q_OBJECT

public:
    explicit PlaybackController(QObject *parent = nullptr);
    ~PlaybackController() override;

    PlaybackController(const PlaybackController &) = delete;
    PlaybackController &operator=(const PlaybackController &) = delete;

    // Sinkron API (main thread) ------------------------------------------------
    bool valid() const { return m_mpv != nullptr; }

    void loadTrack(const QUrl &url);   // async: mpv_command_async loadfile
    void play();
    void pause();
    void toggle();
    void stop();
    void seekBy(double seconds);
    void setVolume(int percent);       // 0..130 (mpv default range)

    double positionSeconds() const { return m_position; }
    double durationSeconds() const { return m_duration; }
    bool pausedNow() const { return m_paused; }

signals:
    void positionChanged(double seconds);
    void durationChanged(double seconds);
    void pausedChanged(bool paused);
    void MetadataChanged();
    void trackLoaded();                // MPV_EVENT_FILE_LOADED
    void endReached();                 // eof
    void errorOccurred(const QString &message);

private:
    void startEventThread();
    void stopEventThread();
    void handleEvent(mpv_event *event);
    void handlePropertyChange(mpv_event_property *prop);

    mpv_handle *m_mpv = nullptr;
    std::thread m_eventThread;
    std::atomic_bool m_running{false};

    // dibaca dari event thread (atomic/protected) — cukup plain: hanya dipakai
    // sebagai cache nilai terakhir untuk QML nanti
    double m_position = 0.0;
    double m_duration = 0.0;
    bool m_paused = false;
    bool m_endReported = false;
};
