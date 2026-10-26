#pragma once

#include <QObject>
#include <QString>

class QueueManager;

#ifdef Q_OS_WIN
#include <windows.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Media.h>
#include <winrt/Windows.Storage.Streams.h>
#endif

class SmtcController : public QObject
{
    Q_OBJECT
public:
    explicit SmtcController(void *windowHandle, QueueManager *queue, QObject *parent = nullptr);
    ~SmtcController() override;

    void updateMetadata(const QString &title, const QString &artist, const QString &artPath);
    void setPlaybackStatus(bool playing);

private:
#ifdef Q_OS_WIN
    void onButtonPressed(winrt::Windows::Media::SystemMediaTransportControls const &sender,
                         winrt::Windows::Media::SystemMediaTransportControlsButtonPressedEventArgs const &args);

    winrt::Windows::Media::SystemMediaTransportControls m_smtc{nullptr};
    winrt::event_token m_buttonToken{};
    bool m_hasToken = false;
#endif
    QueueManager *m_queue = nullptr;
    void *m_windowHandle = nullptr;
};
