#include "platform/smtc_controller.h"

#include "playback/queue_manager.h"

#ifdef Q_OS_WIN
#include <SystemMediaTransportControlsInterop.h>
#endif

#include <QUrl>

SmtcController::SmtcController(void *windowHandle, QueueManager *queue, QObject *parent)
    : QObject(parent)
    , m_queue(queue)
    , m_windowHandle(windowHandle)
{
#ifdef Q_OS_WIN
    try {
        winrt::init_apartment();
        auto interop = winrt::get_activation_factory<winrt::Windows::Media::SystemMediaTransportControls,
                                                    ISystemMediaTransportControlsInterop>();
        winrt::Windows::Media::SystemMediaTransportControls smtc{nullptr};
        interop->GetForWindow(reinterpret_cast<HWND>(windowHandle),
                              winrt::guid_of<winrt::Windows::Media::SystemMediaTransportControls>(),
                              winrt::put_abi(smtc));
        m_smtc = smtc;
        if (m_smtc) {
            m_smtc.IsEnabled(true);
            m_smtc.IsPlayEnabled(true);
            m_smtc.IsPauseEnabled(true);
            m_smtc.IsNextEnabled(true);
            m_smtc.IsPreviousEnabled(true);
            m_smtc.IsStopEnabled(true);
            m_buttonToken = m_smtc.ButtonPressed(
                [this](winrt::Windows::Media::SystemMediaTransportControls const &s,
                       winrt::Windows::Media::SystemMediaTransportControlsButtonPressedEventArgs const &a) {
                    onButtonPressed(s, a);
                });
            m_hasToken = true;
        }
    } catch (...) {
        m_smtc = nullptr;
    }
#endif
}

SmtcController::~SmtcController()
{
#ifdef Q_OS_WIN
    if (m_smtc && m_hasToken) {
        try { m_smtc.ButtonPressed(m_buttonToken); } catch (...) {}
    }
#endif
}

void SmtcController::updateMetadata(const QString &title, const QString &artist, const QString &artPath)
{
#ifdef Q_OS_WIN
    if (!m_smtc)
        return;
    try {
        auto updater = m_smtc.DisplayUpdater();
        updater.Type(winrt::Windows::Media::MediaPlaybackType::Music);
        auto music = updater.MusicProperties();
        music.Title(winrt::hstring(title.toStdWString()));
        music.Artist(winrt::hstring(artist.toStdWString()));
        if (!artPath.isEmpty()) {
            try {
                winrt::Windows::Foundation::Uri uri(winrt::hstring(
                    (artPath.startsWith(QStringLiteral("http")) ? artPath : QStringLiteral("file:///") + artPath).toStdWString()));
                auto ref = winrt::Windows::Storage::Streams::RandomAccessStreamReference::CreateFromUri(uri);
                updater.Thumbnail(ref);
            } catch (...) {}
        }
        updater.Update();
    } catch (...) {}
#endif
    Q_UNUSED(title);
    Q_UNUSED(artist);
    Q_UNUSED(artPath);
}

void SmtcController::setPlaybackStatus(bool playing)
{
#ifdef Q_OS_WIN
    if (!m_smtc)
        return;
    try {
        m_smtc.PlaybackStatus(playing
            ? winrt::Windows::Media::MediaPlaybackStatus::Playing
            : winrt::Windows::Media::MediaPlaybackStatus::Paused);
    } catch (...) {}
#endif
}

#ifdef Q_OS_WIN
void SmtcController::onButtonPressed(winrt::Windows::Media::SystemMediaTransportControls const &,
                                     winrt::Windows::Media::SystemMediaTransportControlsButtonPressedEventArgs const &args)
{
    if (!m_queue)
        return;
    using Button = winrt::Windows::Media::SystemMediaTransportControlsButton;
    const auto btn = args.Button();
    if (btn == Button::Play) {
        QMetaObject::invokeMethod(m_queue, "play", Qt::QueuedConnection);
    } else if (btn == Button::Pause) {
        QMetaObject::invokeMethod(m_queue, "pause", Qt::QueuedConnection);
    } else if (btn == Button::Next) {
        QMetaObject::invokeMethod(m_queue, "playNext", Qt::QueuedConnection);
    } else if (btn == Button::Previous) {
        QMetaObject::invokeMethod(m_queue, "playPrevious", Qt::QueuedConnection);
    } else if (btn == Button::Stop) {
        QMetaObject::invokeMethod(m_queue, "pause", Qt::QueuedConnection);
    }
}
#endif
