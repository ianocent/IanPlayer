#include "platform/taskbar_controller.h"

#include "playback/queue_manager.h"
#include <QCoreApplication>
#include <QMetaObject>

#ifdef Q_OS_WIN
constexpr UINT ID_THUMB_PREV = 1001;
constexpr UINT ID_THUMB_PLAY_PAUSE = 1002;
constexpr UINT ID_THUMB_NEXT = 1003;

static HICON createIconForMediaAction(int actionType)
{
    // actionType: 0 = Prev, 1 = Play, 2 = Pause, 3 = Next
    const int w = 20, h = 20;
    HDC hdcScreen = GetDC(NULL);
    HDC hdcMem = CreateCompatibleDC(hdcScreen);
    HDC hdcMask = CreateCompatibleDC(hdcScreen);

    HBITMAP hbmColor = CreateCompatibleBitmap(hdcScreen, w, h);
    HBITMAP hbmMask = CreateBitmap(w, h, 1, 1, NULL);

    HBITMAP hOldColor = static_cast<HBITMAP>(SelectObject(hdcMem, hbmColor));
    HBITMAP hOldMask = static_cast<HBITMAP>(SelectObject(hdcMask, hbmMask));

    RECT r = {0, 0, w, h};
    HBRUSH hBlack = static_cast<HBRUSH>(GetStockObject(BLACK_BRUSH));
    HBRUSH hWhite = static_cast<HBRUSH>(GetStockObject(WHITE_BRUSH));

    FillRect(hdcMem, &r, hBlack);
    FillRect(hdcMask, &r, hWhite); // Mask white = transparent background

    SelectObject(hdcMem, hWhite);
    SelectObject(hdcMem, GetStockObject(NULL_PEN));
    SelectObject(hdcMask, hBlack);
    SelectObject(hdcMask, GetStockObject(NULL_PEN));

    if (actionType == 0) { // Prev (⏮)
        POINT ptsTri[] = {{12, 4}, {5, 10}, {12, 16}};
        Polygon(hdcMem, ptsTri, 3);
        Polygon(hdcMask, ptsTri, 3);
        RECT rBar = {4, 4, 6, 16};
        FillRect(hdcMem, &rBar, hWhite);
        FillRect(hdcMask, &rBar, hBlack);
    } else if (actionType == 1) { // Play (▶)
        POINT ptsTri[] = {{6, 4}, {15, 10}, {6, 16}};
        Polygon(hdcMem, ptsTri, 3);
        Polygon(hdcMask, ptsTri, 3);
    } else if (actionType == 2) { // Pause (⏸)
        RECT rBar1 = {5, 4, 8, 16};
        RECT rBar2 = {12, 4, 15, 16};
        FillRect(hdcMem, &rBar1, hWhite);
        FillRect(hdcMask, &rBar1, hBlack);
        FillRect(hdcMem, &rBar2, hWhite);
        FillRect(hdcMask, &rBar2, hBlack);
    } else if (actionType == 3) { // Next (⏭)
        POINT ptsTri[] = {{6, 4}, {13, 10}, {6, 16}};
        Polygon(hdcMem, ptsTri, 3);
        Polygon(hdcMask, ptsTri, 3);
        RECT rBar = {13, 4, 15, 16};
        FillRect(hdcMem, &rBar, hWhite);
        FillRect(hdcMask, &rBar, hBlack);
    }

    SelectObject(hdcMem, hOldColor);
    SelectObject(hdcMask, hOldMask);
    DeleteDC(hdcMem);
    DeleteDC(hdcMask);
    ReleaseDC(NULL, hdcScreen);

    ICONINFO ii = {};
    ii.fIcon = TRUE;
    ii.hbmColor = hbmColor;
    ii.hbmMask = hbmMask;

    HICON hIcon = CreateIconIndirect(&ii);

    DeleteObject(hbmColor);
    DeleteObject(hbmMask);

    return hIcon;
}
#endif

TaskbarController::TaskbarController(void *windowHandle, QueueManager *queue, QObject *parent)
    : QObject(parent)
    , m_windowHandle(windowHandle)
    , m_queue(queue)
{
#ifdef Q_OS_WIN
    if (m_windowHandle) {
        CoInitialize(NULL);
        HRESULT hr = CoCreateInstance(CLSID_TaskbarList, NULL, CLSCTX_INPROC_SERVER,
                                      IID_PPV_ARGS(&m_taskbarList));
        if (SUCCEEDED(hr) && m_taskbarList) {
            m_taskbarList->HrInit();

            m_iconPrev = createIconForMediaAction(0);
            m_iconPlay = createIconForMediaAction(1);
            m_iconPause = createIconForMediaAction(2);
            m_iconNext = createIconForMediaAction(3);

            initTaskbarButtons();
        }
    }
#endif
    if (QCoreApplication::instance()) {
        QCoreApplication::instance()->installNativeEventFilter(this);
    }
}

TaskbarController::~TaskbarController()
{
    if (QCoreApplication::instance()) {
        QCoreApplication::instance()->removeNativeEventFilter(this);
    }
#ifdef Q_OS_WIN
    if (m_iconPrev) DestroyIcon(m_iconPrev);
    if (m_iconPlay) DestroyIcon(m_iconPlay);
    if (m_iconPause) DestroyIcon(m_iconPause);
    if (m_iconNext) DestroyIcon(m_iconNext);
    if (m_taskbarList) {
        m_taskbarList->Release();
        m_taskbarList = nullptr;
    }
#endif
}

void TaskbarController::initTaskbarButtons()
{
#ifdef Q_OS_WIN
    if (!m_taskbarList || !m_windowHandle)
        return;

    HWND hwnd = reinterpret_cast<HWND>(m_windowHandle);

    THUMBBUTTON buttons[3] = {};

    buttons[0].dwMask = THB_ICON | THB_TOOLTIP | THB_FLAGS;
    buttons[0].iId = ID_THUMB_PREV;
    buttons[0].hIcon = m_iconPrev;
    wcscpy_s(buttons[0].szTip, L"Previous");
    buttons[0].dwFlags = THBF_ENABLED;

    buttons[1].dwMask = THB_ICON | THB_TOOLTIP | THB_FLAGS;
    buttons[1].iId = ID_THUMB_PLAY_PAUSE;
    buttons[1].hIcon = m_isPlaying ? m_iconPause : m_iconPlay;
    wcscpy_s(buttons[1].szTip, m_isPlaying ? L"Pause" : L"Play");
    buttons[1].dwFlags = THBF_ENABLED;

    buttons[2].dwMask = THB_ICON | THB_TOOLTIP | THB_FLAGS;
    buttons[2].iId = ID_THUMB_NEXT;
    buttons[2].hIcon = m_iconNext;
    wcscpy_s(buttons[2].szTip, L"Next");
    buttons[2].dwFlags = THBF_ENABLED;

    HRESULT hr = m_taskbarList->ThumbBarAddButtons(hwnd, 3, buttons);
    if (SUCCEEDED(hr)) {
        m_initialized = true;
    }
#endif
}

void TaskbarController::updateButtons(bool playing)
{
#ifdef Q_OS_WIN
    if (!m_taskbarList || !m_windowHandle || !m_initialized)
        return;

    HWND hwnd = reinterpret_cast<HWND>(m_windowHandle);

    THUMBBUTTON buttons[3] = {};

    buttons[0].dwMask = THB_ICON | THB_TOOLTIP | THB_FLAGS;
    buttons[0].iId = ID_THUMB_PREV;
    buttons[0].hIcon = m_iconPrev;
    wcscpy_s(buttons[0].szTip, L"Previous");
    buttons[0].dwFlags = THBF_ENABLED;

    buttons[1].dwMask = THB_ICON | THB_TOOLTIP | THB_FLAGS;
    buttons[1].iId = ID_THUMB_PLAY_PAUSE;
    buttons[1].hIcon = playing ? m_iconPause : m_iconPlay;
    wcscpy_s(buttons[1].szTip, playing ? L"Pause" : L"Play");
    buttons[1].dwFlags = THBF_ENABLED;

    buttons[2].dwMask = THB_ICON | THB_TOOLTIP | THB_FLAGS;
    buttons[2].iId = ID_THUMB_NEXT;
    buttons[2].hIcon = m_iconNext;
    wcscpy_s(buttons[2].szTip, L"Next");
    buttons[2].dwFlags = THBF_ENABLED;

    m_taskbarList->ThumbBarUpdateButtons(hwnd, 3, buttons);
#else
    Q_UNUSED(playing);
#endif
}

void TaskbarController::setPlaybackStatus(bool playing)
{
    if (m_isPlaying != playing) {
        m_isPlaying = playing;
        updateButtons(playing);
    }
}

bool TaskbarController::nativeEventFilter(const QByteArray &eventType, void *message, qintptr *result)
{
    Q_UNUSED(result);
#ifdef Q_OS_WIN
    if (eventType == "windows_generic_MSG") {
        MSG *msg = static_cast<MSG *>(message);
        if (msg->message == WM_COMMAND) {
            const UINT id = LOWORD(msg->wParam);
            if (id == ID_THUMB_PREV) {
                if (m_queue) {
                    QMetaObject::invokeMethod(m_queue, "playPrevious", Qt::QueuedConnection);
                }
                return true;
            } else if (id == ID_THUMB_PLAY_PAUSE) {
                if (m_queue) {
                    QMetaObject::invokeMethod(m_queue, "togglePlayPause", Qt::QueuedConnection);
                }
                return true;
            } else if (id == ID_THUMB_NEXT) {
                if (m_queue) {
                    QMetaObject::invokeMethod(m_queue, "playNext", Qt::QueuedConnection);
                }
                return true;
            }
        }
    }
#else
    Q_UNUSED(eventType);
    Q_UNUSED(message);
#endif
    return false;
}
