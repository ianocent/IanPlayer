#pragma once

#include <QObject>
#include <QString>
#include <QNetworkAccessManager>

// Disk cache LRU sederhana untuk thumbnail (M3.2). Max 200MB di
// %LOCALAPPDATA%\IanPlayer\imgcache.
class ThumbnailCache : public QObject
{
    Q_OBJECT
public:
    explicit ThumbnailCache(QObject *parent = nullptr);

    // Kembalikan path lokal kalau sudah ada; kalau tidak, download async
    // dan emit loaded(url, localPath) saat selesai.
    QString cachedPath(const QString &url) const;
    void fetch(const QString &url);

signals:
    void loaded(const QString &url, const QString &localPath);

private:
    QString cacheDir() const;
    void evictIfNeeded();

    QNetworkAccessManager m_nam;
};
