#pragma once

#include <QObject>
#include <QString>

#include "net/lyric_repository.h"

class LibraryDatabase;
class QueueManager;

class LyricService : public QObject
{
    Q_OBJECT
    Q_PROPERTY(QString lyricText READ lyricText NOTIFY lyricTextChanged)
    Q_PROPERTY(bool hasSynced READ hasSynced NOTIFY lyricTextChanged)
public:
    explicit LyricService(LibraryDatabase *db, QueueManager *queue, QObject *parent = nullptr);

    QString lyricText() const { return m_lyricText; }
    bool hasSynced() const { return m_hasSynced; }

    Q_INVOKABLE void fetchFor(const QString &title, const QString &artist);

signals:
    void lyricTextChanged();

private:
    void onLyricReady(const LyricResult &result);

    LibraryDatabase *m_db = nullptr;
    QueueManager *m_queue = nullptr;
    LyricRepository m_repo;
    QString m_lyricText;
    bool m_hasSynced = false;
};
