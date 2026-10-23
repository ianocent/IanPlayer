#include "app/library_service.h"

#include "core/library_scanner.h"
#include "db/library_database.h"

#include <QSettings>

namespace {

// roots persist dengan app name dari QGuiApplication ("ianocent"/"IanPlayer")
QString rootsSettingKey() { return QStringLiteral("library/folder_roots"); }

} // namespace

LibraryService::LibraryService(LibraryScanner *scanner, LibraryDatabase *db, QObject *parent)
    : QObject(parent),
      m_scanner(scanner),
      m_db(db)
{
    qRegisterMetaType<QVector<SongRecord>>("QVector<SongRecord>");

    connect(m_scanner, &LibraryScanner::scanningStarted, this, [this] {
        emit scanningChanged();
        m_scanStatus = tr("Memindai library...");
        emit scanStatusChanged();
    });
    connect(m_scanner, &LibraryScanner::progressChanged, this, [this](int current, int total) {
        m_progressCurrent = current;
        m_progressTotal = total;
        emit progressChanged();
        if (total > 0) {
            m_scanStatus = tr("Memindai %1 / %2").arg(current).arg(total);
            emit scanStatusChanged();
        }
    });
    connect(m_scanner, &LibraryScanner::scanError, this, [this](const QString &message) {
        m_scanStatus = message;
        emit scanStatusChanged();
        emit scanningChanged();
    });
    connect(m_scanner, &LibraryScanner::scanCancelled, this, [this] {
        m_scanStatus = tr("Scan dibatalkan");
        emit scanStatusChanged();
        emit scanningChanged();
    });
    connect(m_scanner, &LibraryScanner::scanCompleted, this, &LibraryService::onScanCompleted);

    // restore folder set sebelumnya (tanpa auto-scan saaat start)
    QSettings settings;
    m_roots = settings.value(rootsSettingKey()).toStringList();
    for (const QString &path : m_roots)
        m_watcher.addPath(path);

    m_debounce.setInterval(2000);
    m_debounce.setSingleShot(true);
    connect(&m_debounce, &QTimer::timeout, this, &LibraryService::rescanNow);
    connect(&m_watcher, &QFileSystemWatcher::directoryChanged,
            this, &LibraryService::handleWatcherChange);
}

void LibraryService::setRoots(const QStringList &roots)
{
    m_roots = roots;
    QSettings settings;
    settings.setValue(rootsSettingKey(), m_roots);

    const QStringList watched = m_watcher.directories();
    for (const QString &path : watched)
        if (!m_roots.contains(path))
            m_watcher.removePath(path);
    for (const QString &path : m_roots)
        if (!watched.contains(path))
            m_watcher.addPath(path);
    emit rootsChanged();
}

void LibraryService::addRoot(const QString &path)
{
    if (m_roots.contains(path))
        return;
    QStringList next = m_roots;
    next.append(path);
    setRoots(next);
    startScan();
}

void LibraryService::removeRoot(const QString &path)
{
    QStringList next = m_roots;
    next.removeAll(path);
    setRoots(next);
    startScan();
}

void LibraryService::handleWatcherChange(const QString &path)
{
    Q_UNUSED(path);
    m_debounce.start();   // debounce 2 dtk lalu rescanNow
}

void LibraryService::rescanNow()
{
    startScan();
}

void LibraryService::startScan()
{
    if (m_roots.isEmpty()) {
        m_scanStatus = tr("Belum ada folder musik. Pilih folder dulu.");
        emit scanStatusChanged();
        return;
    }
    m_scanner->scanAsync(m_roots, m_db->knownModifiedByPath());
}

void LibraryService::onScanCompleted(const QVector<SongRecord> &newRecords,
                                     const QStringList &alivePaths, int totalTracked)
{
    m_progressTotal = totalTracked;
    m_progressCurrent = totalTracked;

    if (m_db->persistRecords(newRecords)) {
        m_db->markUnavailableExcept(QSet<QString>(alivePaths.cbegin(), alivePaths.cend()));
        m_db->rebuildIndexes();
        m_scanStatus = tr("%1 lagu di library. %2 di-update/ditambah").
                       arg(totalTracked).arg(newRecords.size());
    } else {
        m_scanStatus = tr("Gagal simpan ke cakram.");
    }
    emit progressChanged();
    emit scanStatusChanged();
    emit scanningChanged();
    emit libraryChanged();
}

int LibraryService::songCount() const
{
    return m_db->songCount();
}
