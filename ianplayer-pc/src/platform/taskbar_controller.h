#pragma once

#include <QObject>
#include <QAbstractNativeEventFilter>

class QueueManager;

#ifdef Q_OS_WIN
#include <windows.h>
#include <shobjidl.h>
#endif

class TaskbarController : public QObject, public QAbstractNativeEventFilter
{
    Q_OBJECT
public:
    explicit TaskbarController(void *windowHandle, QueueManager *queue, QObject *parent = nullptr);
    ~TaskbarController() override;

    bool nativeEventFilter(const QByteArray &eventType, void *message, qintptr *result) override;

public slots:
    void setPlaybackStatus(bool playing);

private:
    void initTaskbarButtons();
    void updateButtons(bool playing);

    void *m_windowHandle = nullptr;
    QueueManager *m_queue = nullptr;
    bool m_initialized = false;
    bool m_isPlaying = false;

#ifdef Q_OS_WIN
    ITaskbarList3 *m_taskbarList = nullptr;
    HICON m_iconPrev = nullptr;
    HICON m_iconPlay = nullptr;
    HICON m_iconPause = nullptr;
    HICON m_iconNext = nullptr;
#endif
};
