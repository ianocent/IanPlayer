#pragma once

#include <QObject>
#include <QString>

class LibraryService;
class StreamUrlResolver;

class DownloaderService : public QObject
{
    Q_OBJECT
public:
    explicit DownloaderService(LibraryService *library, StreamUrlResolver *resolver, QObject *parent = nullptr);

    Q_INVOKABLE void downloadStream(const QString &videoId, const QString &title, const QString &artist);

signals:
    void downloadProgress(qint64 received, qint64 total);
    void downloadFinished(const QString &path);
    void downloadError(const QString &message);

private:
    void doDownload(const QString &url, const QString &destPath, const QString &title, const QString &artist);
    void enrichAndTag(const QString &path, const QString &title, const QString &artist);

    LibraryService *m_library = nullptr;
    StreamUrlResolver *m_resolver = nullptr;
};
