#include "playback/playback_controller.h"

#include <QMetaObject>

// Destructor urutan teardown (pitfall 01-TOOLCHAIN.md bagian 5):
//   1. stop playback (command "stop")
//   2. matikan event thread (join)
//   3. mpv_terminate_destroy
// Keibalik urutan = crash saat exit.
PlaybackController::PlaybackController(QObject *parent)
    : QObject(parent)
{
    m_mpv = mpv_create();
    if (!m_mpv) {
        emit errorOccurred(QStringLiteral("mpv_create gagal"));
        return;
    }

    // Option M0.3: volume default 100, audio tampan gapless "weak"
    mpv_set_option_string(m_mpv, "volume", "100");
    mpv_set_option_string(m_mpv, "gapless-audio", "weak");
    // Current desktop: window "ao" default (WASAPI), tidak perlu diset eksplisit;
    // modus video menyusul di M5.

    if (mpv_initialize(m_mpv) < 0) {
        mpv_terminate_destroy(m_mpv);
        m_mpv = nullptr;
        emit errorOccurred(QStringLiteral("mpv_initialize gagal"));
        return;
    }

    mpv_observe_property(m_mpv, 0, "time-pos", MPV_FORMAT_DOUBLE);
    mpv_observe_property(m_mpv, 0, "duration", MPV_FORMAT_DOUBLE);
    mpv_observe_property(m_mpv, 0, "pause", MPV_FORMAT_FLAG);
    mpv_observe_property(m_mpv, 0, "eof-reached", MPV_FORMAT_FLAG);
    mpv_observe_property(m_mpv, 0, "metadata", MPV_FORMAT_NODE);

    startEventThread();
}

PlaybackController::~PlaybackController()
{
    stopEventThread();
    if (m_mpv) {
        mpv_terminate_destroy(m_mpv);
        m_mpv = nullptr;
    }
}

void PlaybackController::startEventThread()
{
    m_running = true;
    m_eventThread = std::thread([this]() {
        while (m_running) {
            mpv_event *ev = mpv_wait_event(m_mpv, 0.1);
            if (ev->event_id == MPV_EVENT_NONE)
                continue;
            handleEvent(ev);
            if (ev->event_id == MPV_EVENT_SHUTDOWN)
                break;
        }
    });
}

void PlaybackController::stopEventThread()
{
    if (m_mpv)
        mpv_command_string(m_mpv, "stop");

    m_running = false;
    if (m_eventThread.joinable())
        m_eventThread.join();
}

void PlaybackController::loadTrack(const QUrl &url)
{
    if (!m_mpv) {
        emit errorOccurred(QStringLiteral("mpv belum siap"));
        return;
    }
    m_endReported = false;

    const QByteArray encoded = url.toString(QUrl::FullyEncoded).toUtf8();
    const char *loadfile[] = {"loadfile", encoded.constData(), "replace", nullptr};
    if (mpv_command_async(m_mpv, 1, loadfile) < 0)
        emit errorOccurred(QStringLiteral("loadfile gagal: %1").arg(encoded.constData()));
}

void PlaybackController::play()
{
    if (m_mpv)
        mpv_command_string(m_mpv, "set pause no");
}

void PlaybackController::pause()
{
    if (m_mpv)
        mpv_command_string(m_mpv, "set pause yes");
}

void PlaybackController::toggle()
{
    if (m_mpv)
        mpv_command_string(m_mpv, "cycle pause");
}

void PlaybackController::stop()
{
    if (m_mpv)
        mpv_command_string(m_mpv, "stop");
}

void PlaybackController::seekBy(double seconds)
{
    if (!m_mpv)
        return;
    const QByteArray delta = QByteArray::number(seconds, 'f', 3);
    const char *args[] = {"seek", delta.constData(), "relative", nullptr};
    const int rc = mpv_command(m_mpv, args);
    if (rc < 0)
        emit errorOccurred(QStringLiteral("seek gagal (rc=%1)").arg(rc));
}

void PlaybackController::setVolume(int percent)
{
    if (!m_mpv)
        return;
    const QByteArray value = QByteArray::number(percent);
    const char *args[] = {"set", "volume", value.constData(), nullptr};
    if (mpv_command(m_mpv, args) < 0)
        emit errorOccurred(QStringLiteral("set volume gagal"));
}

void PlaybackController::handleEvent(mpv_event *event)
{
    switch (event->event_id) {
    case MPV_EVENT_PROPERTY_CHANGE:
        if (event->data)
            handlePropertyChange(static_cast<mpv_event_property *>(event->data));
        break;
    case MPV_EVENT_FILE_LOADED:
        emit trackLoaded();
        break;
    case MPV_EVENT_END_FILE: {
        auto *reason = static_cast<mpv_event_end_file *>(event->data);
        if (reason && reason->reason == MPV_END_FILE_REASON_EOF && !m_endReported) {
            m_endReported = true;
            emit endReached();
        }
        break;
    }
    case MPV_EVENT_LOG_MESSAGE:
        // sink: debug log via env libmpv kalau perlu
        break;
    default:
        break;
    }
}

void PlaybackController::handlePropertyChange(mpv_event_property *prop)
{
    switch (prop->format) {
    case MPV_FORMAT_DOUBLE: {
        if (!prop->data)
            break; // 3rd arg: null data = slot property removed
        const double value = *static_cast<double *>(prop->data);
        const QString name = QString::fromUtf8(prop->name);
        if (name == QLatin1String("time-pos")) {
            m_position = value;
            emit positionChanged(value);
        } else if (name == QLatin1String("duration")) {
            m_duration = value;
            emit durationChanged(value);
        }
        break;
    }
    case MPV_FORMAT_FLAG: {
        const bool on = prop->data && *static_cast<int *>(prop->data) != 0;
        const QString name = QString::fromUtf8(prop->name);
        if (name == QLatin1String("pause")) {
            if (on != m_paused) {
                m_paused = on;
                emit pausedChanged(on);
            }
        } else if (name == QLatin1String("eof-reached") && on && !m_endReported) {
            // Backup signal kalau MPV_EVENT_END_FILE kelewat; guard dedupe.
            m_endReported = true;
            emit endReached();
        }
        break;
    }
    case MPV_FORMAT_NODE:
        if (prop->data)
            mpv_free_node_contents(static_cast<mpv_node *>(prop->data));
        emit MetadataChanged();
        break;
    default:
        break;
    }
}
