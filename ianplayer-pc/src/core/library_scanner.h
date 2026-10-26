#pragma once

#include <QObject>
#include <QString>
#include <QStringList>
#include <QFutureWatcher>
#include <QHash>
#include <QSet>
#include <atomic>
#include <optional>

class QImage;

#include "db/library_database.h"

// Scanner library M1.1:
// - traverse rekursif multi-folder, filter ekstensi, skip folder system/dot
// - TagLib: title/artist/album/track/year/duration/bitrate/cover
// - fallback "Artist - Title" dari nama file (port MusicRepository.kt 1:1)
// - ekstrak embedded album art -> thumbnail 256 & 64 px di AppLocalData
// Heavy I/O dijalankan di QtConcurrent; hasil dibalik via QFutureWatcher agar
// lambda/threading tak menyeberang.
//
// ScanSync MURNI (tanpa state): aman dipanggil dari worker thread.
// Incremental: file yang modifiedTs-nya match knownModified di-skip TagLib.
class LibraryScanner : public QObject
{
    Q_OBJECT

public:
    enum class StatusCode { OK, Cancelled };

    explicit LibraryScanner(QObject *parent = nullptr);

    static QSet<QString> supportedExtensions();
    static QString thumbnailsDir();

    // Bandingkan hasil (histogram) scanIn-Main accessible via finished()
    void scanAsync(const QStringList &roots, const QHash<QString, qint64> &knownModified);
    bool isScanning() const { return m_scanning; }
    void cancelScan();

    // === pure/testable: parse nama file tanpa tag ===
    // "Artist - Title" -> {artist="Artist", title="Title"}; nullptr jika
    // tidak ada " - " valid (dashIndex > 0 — port MusicRepository.kt).
    static std::optional<QPair<QString, QString>>
    parseTaglessFileName(const QString &baseName);

signals:
    void scanningStarted();
    void progressChanged(int current, int total);
    // queued: records (cuma yang berubah), alive list semua file hidup, total
    void scanCompleted(const QVector<SongRecord> &newRecords,
                       const QStringList &alivePaths, int totalTracked);
    void scanCancelled();
    void scanError(const QString &message);

private:
    struct ScanResult
    {
        int totalTracked = 0;
        int newOrUpdated = 0;
        QVector<SongRecord> songs;      // cuma sing file yang gak sesuai skip
        QStringList alivePaths;
        bool cancelled = false;
    };

    static ScanResult scanSync(const QStringList &roots,
                               const QHash<QString, qint64> &knownModified,
                               const QString &thumbsDir,
                               const std::function<bool()> &cancelled,
                               const std::function<void(int, int)> &progress);

    // TagLib helpers — dipanggil worker thread
    static SongRecord readTagLib(const QString &filePath, const QString &codec);
    static QImage embeddedArt(const QString &filePath, const QString &codec);
    static QPair<QString, QString> saveThumbnail(const QString &albumKey,
                                                 const QImage &image,
                                                 const QString &thumbsDir);

    // DB dir eksplisit biasa disipan bad paths excluded: skip folder spesial
    static bool isExcludedDir(const QString &dirName);

    QFutureWatcher<ScanResult> m_watcher;
    QStringList m_roots;
    bool m_scanning = false;
    std::atomic_bool m_cancelRequested = false;
};
