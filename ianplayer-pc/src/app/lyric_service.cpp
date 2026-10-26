#include "app/lyric_service.h"

#include "playback/queue_manager.h"
#include "db/library_database.h"

LyricService::LyricService(LibraryDatabase *db, QueueManager *queue, QObject *parent)
    : QObject(parent)
    , m_db(db)
    , m_queue(queue)
    , m_repo(db, this)
{
    QObject::connect(&m_repo, &LyricRepository::lyricReady, this, &LyricService::onLyricReady);
    if (m_queue) {
        QObject::connect(m_queue, &QueueManager::currentSongChanged, this, [this]() {
            if (m_queue)
                fetchFor(m_queue->currentTitle(), m_queue->currentArtist());
        });
    }
}

void LyricService::fetchFor(const QString &title, const QString &artist)
{
    m_lyricText.clear();
    m_hasSynced = false;
    emit lyricTextChanged();
    m_repo.fetchLyric(title, artist);
}

void LyricService::onLyricReady(const LyricResult &result)
{
    if (result.type == LyricResult::Synced) {
        m_hasSynced = true;
        QStringList lines;
        for (const auto &l : result.syncedLines)
            lines << l.text;
        m_lyricText = lines.join('\n');
    } else if (result.type == LyricResult::Plain) {
        m_hasSynced = false;
        m_lyricText = result.plainText;
    } else {
        m_hasSynced = false;
        m_lyricText = QStringLiteral("(lirik tidak ditemukan)");
    }
    emit lyricTextChanged();
}
