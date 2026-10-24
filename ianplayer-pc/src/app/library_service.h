#pragma once

#include <QObject>
#include <QString>
#include <QStringList>
#include <QTimer>
#include <QFileSystemWatcher>
#include <QSet>

#include "core/library_scanner.h"
#include "db/library_database.h"

// Orkestrasi M1.1: scan + persist DB + watcher realtime.
// Ditempatkan di main via context property "LibraryService".
// Semua akses DB di thread ini (main) — scan berat di worker, dirangkum via signal.
class LibraryService : public QObject
{
    Q_OBJECT

public:
    explicit LibraryService(LibraryScanner *scanner, LibraryDatabase *db,
                            QObject *parent = nullptr);

    Q_PROPERTY(bool scanning READ scanning NOTIFY scanningChanged)
    Q_PROPERTY(int progressTotal READ progressTotal NOTIFY progressChanged)
    Q_PROPERTY(int progressCurrent READ progressCurrent NOTIFY progressChanged)
    Q_PROPERTY(int songCount READ songCount NOTIFY libraryChanged)
    Q_PROPERTY(int favoriteCount READ favoriteCount NOTIFY favoritesChanged)
    Q_PROPERTY(QString scanStatusText READ scanStatusText NOTIFY scanStatusChanged)

    bool scanning() const { return m_scanner->isScanning(); }
    int progressTotal() const { return m_progressTotal; }
    int progressCurrent() const { return m_progressCurrent; }
    int songCount() const;
    int favoriteCount() const;
    QString scanStatusText() const { return m_scanStatus; }
    QStringList roots() const { return m_roots; }
    void setRoots(const QStringList &roots);

    Q_INVOKABLE void addRoot(const QString &path);
    Q_INVOKABLE void removeRoot(const QString &path);
    Q_INVOKABLE void rescanNow();

    // ---- paritas #7 target M1.1 (favorites / play counts / history) ----
    // song_key = "local:<id>" | "yt:<videoId>"; kosong kalau path tak dikenal
    Q_INVOKABLE QString localKeyForPath(const QString &path) const;
    Q_INVOKABLE bool isFavorite(const QString &songKey) const;
    Q_INVOKABLE void setFavorite(const QString &songKey, bool favorite);
    Q_INVOKABLE void toggleFavorite(const QString &songKey);
    // Recording play event: naikkan play_counts + tulis play_history (trim 90d).
    // Dipanggil controller playback saat track lokal mulai putar.
    Q_INVOKABLE void recordLocalPlayed(const QString &path);

signals:
    void scanningChanged();
    void progressChanged();
    void libraryChanged();
    void favoritesChanged();
    void playStateChanged();
    void scanStatusChanged();
    void rootsChanged();

private slots:
    void handleWatcherChange(const QString &path);
    void onScanCompleted(const QVector<SongRecord> &newRecords,
                         const QStringList &alivePaths, int totalTracked);

private:
    void startScan();

    LibraryScanner *m_scanner;
    LibraryDatabase *m_db;
    QFileSystemWatcher m_watcher;
    QTimer m_debounce;
    QStringList m_roots;
    int m_progressCurrent = 0;
    int m_progressTotal = 0;
    QString m_scanStatus;
};
