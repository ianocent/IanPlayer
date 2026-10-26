#include "net/thumbnail_cache.h"

#include <QDir>
#include <QStandardPaths>
#include <QFile>
#include <QFileInfo>
#include <QNetworkRequest>
#include <QCryptographicHash>
#include <QNetworkReply>
#include <QCryptographicHash>

namespace {
constexpr qint64 kMaxBytes = 200LL * 1024 * 1024;
}

ThumbnailCache::ThumbnailCache(QObject *parent) : QObject(parent) {}

QString ThumbnailCache::cacheDir() const
{
    const QString base = QStandardPaths::writableLocation(QStandardPaths::AppLocalDataLocation);
    const QString dir = base + QStringLiteral("/imgcache");
    QDir().mkpath(dir);
    return dir;
}

QString ThumbnailCache::cachedPath(const QString &url) const
{
    const QString name = QString::fromUtf8(
        QCryptographicHash::hash(url.toUtf8(), QCryptographicHash::Sha1).toHex())
        + QStringLiteral(".img");
    const QString path = cacheDir() + QStringLiteral("/") + name;
    if (QFileInfo::exists(path))
        return path;
    return {};
}

void ThumbnailCache::fetch(const QString &url)
{
    const QString existing = cachedPath(url);
    if (!existing.isEmpty()) {
        emit loaded(url, existing);
        return;
    }
    QNetworkRequest req(url);
    QNetworkReply *reply = m_nam.get(req);
    QObject::connect(reply, &QNetworkReply::finished, this, [this, reply, url]() {
        if (reply->error() != QNetworkReply::NoError) {
            reply->deleteLater();
            return;
        }
        const QString path = cachedPath(url).isEmpty()
            ? (cacheDir() + QStringLiteral("/") +
               QString::fromUtf8(QCryptographicHash::hash(url.toUtf8(), QCryptographicHash::Sha1).toHex()) +
               QStringLiteral(".img"))
            : cachedPath(url);
        QFile f(path);
        if (f.open(QIODevice::WriteOnly)) {
            f.write(reply->readAll());
            f.close();
        }
        evictIfNeeded();
        emit loaded(url, path);
        reply->deleteLater();
    });
}

void ThumbnailCache::evictIfNeeded()
{
    QDir d(cacheDir());
    QFileInfoList files = d.entryInfoList(QDir::Files, QDir::Time);
    qint64 total = 0;
    for (const QFileInfo &fi : files)
        total += fi.size();
    int i = files.size() - 1;
    while (total > kMaxBytes && i >= 0) {
        total -= files.at(i).size();
        QFile::remove(files.at(i).absoluteFilePath());
        --i;
    }
}
