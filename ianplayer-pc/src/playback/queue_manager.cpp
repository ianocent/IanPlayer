#include "playback/queue_manager.h"

#include <QJsonDocument>
#include <QJsonArray>
#include <QJsonObject>
#include <QUrl>
#include <QStringList>
#include <QRandomGenerator>
#include <QtGlobal>

using SongInfo = QueueManager::SongInfo;

QueueManager::QueueManager(EngineHooks hooks,
                           std::function<bool(const QString &, SongInfo &)> resolver,
                           QSettings *settings,
                           QObject *parent)
    : QObject(parent)
    , m_hooks(std::move(hooks))
    , m_resolver(std::move(resolver))
    , m_settings(settings)
{
    if (m_settings) {
        m_shuffleOn = m_settings->value(QStringLiteral("is_shuffle_on"), false).toBool();
        m_repeatMode = m_settings->value(QStringLiteral("repeat_mode"), RepeatOff).toInt();
    }
    restoreState();
}

QueueManager::SongInfo QueueManager::resolveOrPlaceholder(const QString &key)
{
    QueueManager::SongInfo info;
    info.key = key;
    if (m_resolver) {
        QueueManager::SongInfo resolved;
        if (m_resolver(key, resolved)) {
            return resolved;
        }
    }
    info.title = key;
    info.artist = QStringLiteral("Unknown Artist");
    return info;
}

void QueueManager::restoreState()
{
    if (!m_settings)
        return;
    const QString json = m_settings->value(QStringLiteral("last_queue")).toString();
    if (json.isEmpty())
        return;
    const QJsonDocument doc = QJsonDocument::fromJson(json.toUtf8());
    if (!doc.isArray())
        return;
    if (!restoreQueueFromJson(doc.array()))
        return;

    const QString savedKey = m_settings->value(QStringLiteral("last_song_id")).toString();
    int savedIndex = m_settings->value(QStringLiteral("last_index"), -1).toInt();
    if (savedIndex < 0 || savedIndex >= m_queue.size())
        savedIndex = 0;
    m_currentIndex = savedIndex;
    if (!savedKey.isEmpty()) {
        m_currentKey = savedKey;
        // resolve current song info
        SongInfo info = resolveOrPlaceholder(savedKey);
        // prefer queue entry at index
        if (m_currentIndex >= 0 && m_currentIndex < m_queue.size() &&
            m_queue.at(m_currentIndex).key == savedKey) {
            info = m_queue.at(m_currentIndex);
        }
        m_current = info;
    }
}

void QueueManager::saveState()
{
    if (!m_settings || m_currentKey.isEmpty())
        return;
    m_settings->setValue(QStringLiteral("last_queue"), QString::fromUtf8(QJsonDocument(queueToJson()).toJson(QJsonDocument::Compact)));
    m_settings->setValue(QStringLiteral("last_song_id"), m_currentKey);
    m_settings->setValue(QStringLiteral("last_index"), m_currentIndex);
    m_settings->setValue(QStringLiteral("is_shuffle_on"), m_shuffleOn);
    m_settings->setValue(QStringLiteral("repeat_mode"), m_repeatMode);
    m_settings->sync();
}

QJsonArray QueueManager::queueToJson() const
{
    QJsonArray arr;
    for (const SongInfo &s : m_queue) {
        QJsonObject o;
        // parse numeric id from "local:<id>"
        qint64 id = 0;
        if (s.key.startsWith(QStringLiteral("local:")))
            id = s.key.mid(6).toLongLong();
        o.insert(QStringLiteral("id"), id);
        o.insert(QStringLiteral("key"), s.key);
        o.insert(QStringLiteral("title"), s.title);
        o.insert(QStringLiteral("artist"), s.artist);
        o.insert(QStringLiteral("album"), s.album);
        o.insert(QStringLiteral("duration"), s.durationMs);
        o.insert(QStringLiteral("uri"), s.playUrl);
        o.insert(QStringLiteral("isStream"), s.isStream);
        if (!s.art.isEmpty())
            o.insert(QStringLiteral("remoteArtUrl"), s.art);
        if (s.key.startsWith(QStringLiteral("yt:")))
            o.insert(QStringLiteral("remoteId"), s.key.mid(3));
        arr.append(o);
    }
    return arr;
}

bool QueueManager::restoreQueueFromJson(const QJsonArray &arr)
{
    QVector<SongInfo> restored;
    for (const QJsonValue &v : arr) {
        if (!v.isObject())
            continue;
        const QJsonObject o = v.toObject();
        SongInfo s;
        s.key = o.value(QStringLiteral("key")).toString();
        if (s.key.isEmpty()) {
            const qint64 id = o.value(QStringLiteral("id")).toVariant().toLongLong();
            if (id > 0)
                s.key = QStringLiteral("local:%1").arg(id);
            else
                s.key = o.value(QStringLiteral("remoteId")).toString();
            if (s.key.isEmpty())
                continue;
        }
        s.title = o.value(QStringLiteral("title")).toString(s.key);
        s.artist = o.value(QStringLiteral("artist")).toString(QStringLiteral("Unknown Artist"));
        s.album = o.value(QStringLiteral("album")).toString();
        s.durationMs = o.value(QStringLiteral("duration")).toVariant().toLongLong();
        s.playUrl = o.value(QStringLiteral("uri")).toString();
        s.isStream = o.value(QStringLiteral("isStream")).toBool(false);
        s.art = o.value(QStringLiteral("remoteArtUrl")).toString();
        // refresh from resolver if available
        SongInfo resolved;
        if (m_resolver && m_resolver(s.key, resolved)) {
            // keep playUrl from resolved if stored empty
            if (s.playUrl.isEmpty())
                s.playUrl = resolved.playUrl;
            if (s.title == s.key)
                s.title = resolved.title;
            if (s.artist == QStringLiteral("Unknown Artist"))
                s.artist = resolved.artist;
            if (s.durationMs == 0)
                s.durationMs = resolved.durationMs;
            if (s.art.isEmpty())
                s.art = resolved.art;
            s.needsResolution = resolved.needsResolution;
        } else {
            s.needsResolution = s.isStream;
        }
        restored.append(s);
    }
    if (restored.isEmpty())
        return false;
    m_queue = restored;
    emit queueChanged();
    return true;
}

void QueueManager::playNow(const QStringList &queueKeys, int startIndex)
{
    QVector<SongInfo> newQueue;
    newQueue.reserve(queueKeys.size());
    for (const QString &k : queueKeys)
        newQueue.append(resolveOrPlaceholder(k));

    // parity setQueue shuffle handling
    SongInfo target;
    bool hasTarget = false;
    if (startIndex >= 0 && startIndex < newQueue.size()) {
        target = newQueue.at(startIndex);
        hasTarget = true;
    }

    if (m_shuffleOn) {
        const bool isSameContent = (!m_baseQueueBeforeShuffle.isEmpty()) &&
            newQueue.size() == m_baseQueueBeforeShuffle.size() &&
            [&]() {
                for (int i = 0; i < newQueue.size(); ++i)
                    if (newQueue.at(i).key != m_baseQueueBeforeShuffle.at(i).key)
                        return false;
                return true;
            }();
        if (!isSameContent) {
            m_baseQueueBeforeShuffle = newQueue;
            QVector<SongInfo> shuffled = newQueue;
            if (hasTarget) {
                QVector<SongInfo> others;
                for (const SongInfo &s : newQueue)
                    if (s.key != target.key)
                        others.append(s);
                // simple shuffle
                for (int i = others.size() - 1; i > 0; --i) {
                    int j = QRandomGenerator::global()->bounded(i + 1);
                    std::swap(others[i], others[j]);
                }
                shuffled.clear();
                shuffled.append(target);
                shuffled += others;
            } else {
                for (int i = shuffled.size() - 1; i > 0; --i) {
                    int j = QRandomGenerator::global()->bounded(i + 1);
                    std::swap(shuffled[i], shuffled[j]);
                }
            }
            m_queue = shuffled;
        }
    } else {
        m_queue = newQueue;
        m_baseQueueBeforeShuffle.clear();
    }
    emit queueChanged();

    SongInfo toPlay;
    if (hasTarget) {
        // match by id in latest queue
        for (const SongInfo &s : m_queue)
            if (s.key == target.key) { toPlay = s; break; }
        if (toPlay.key.isEmpty())
            toPlay = target;
    } else if (!m_queue.isEmpty()) {
        toPlay = m_queue.first();
    }
    if (!toPlay.key.isEmpty()) {
        // ensure in queue
        int idx = -1;
        for (int i = 0; i < m_queue.size(); ++i)
            if (m_queue.at(i).key == toPlay.key) { idx = i; break; }
        if (idx == -1) {
            m_queue.append(toPlay);
            idx = m_queue.size() - 1;
            emit queueChanged();
        }
        publishCurrentSong(toPlay, idx, true);
        loadCurrentAndPlay();
    }
    saveState();
}

void QueueManager::playSingle(const QString &songKey)
{
    playNow(QStringList{songKey}, 0);
}

void QueueManager::playSongAt(int index)
{
    if (index < 0 || index >= m_queue.size())
        return;
    publishCurrentSong(m_queue.at(index), index, true);
    loadCurrentAndPlay();
    saveState();
}

void QueueManager::playNext()
{
    if (m_queue.isEmpty())
        return;
    const bool isLast = m_currentIndex >= m_queue.size() - 1;
    int nextIndex = -1;
    if (isLast) {
        if (m_repeatMode == RepeatAll)
            nextIndex = 0;
        else
            return; // repeat off / one: stop
    } else {
        nextIndex = m_currentIndex + 1;
    }
    playSongAt(nextIndex);
    // autoFillUpNext hook (stream only) — noop for local
}

void QueueManager::playPrevious()
{
    if (m_queue.isEmpty())
        return;
    // if position > 3s -> seek 0
    const double pos = m_hooks.position ? m_hooks.position() : 0.0;
    if (pos > 3.0) {
        seekToMs(0);
        return;
    }
    const bool isFirst = m_currentIndex <= 0;
    int prevIndex = 0;
    if (isFirst) {
        if (m_repeatMode == RepeatAll)
            prevIndex = m_queue.size() - 1;
        else
            prevIndex = 0;
    } else {
        prevIndex = m_currentIndex - 1;
    }
    playSongAt(prevIndex);
}

void QueueManager::addToQueue(const QString &songKey)
{
    m_queue.append(resolveOrPlaceholder(songKey));
    emit queueChanged();
    saveState();
}

void QueueManager::playNextInQueue(const QString &songKey)
{
    const SongInfo song = resolveOrPlaceholder(songKey);
    if (m_currentIndex < 0) {
        m_queue.append(song);
        emit queueChanged();
        publishCurrentSong(song, m_queue.size() - 1, true);
        loadCurrentAndPlay();
        saveState();
        return;
    }
    const int insertIdx = (m_currentIndex + 1 <= m_queue.size()) ? m_currentIndex + 1 : m_queue.size();
    m_queue.insert(insertIdx, song);
    emit queueChanged();
    // if current not playing? play the inserted
    publishCurrentSong(song, insertIdx, true);
    loadCurrentAndPlay();
    saveState();
}

void QueueManager::removeSongFromQueue(const QString &songKey)
{
    int removedIndex = -1;
    QVector<SongInfo> filtered;
    for (int i = 0; i < m_queue.size(); ++i) {
        if (m_queue.at(i).key == songKey) {
            removedIndex = i;
            continue;
        }
        filtered.append(m_queue.at(i));
    }
    if (removedIndex < 0)
        return;
    m_queue = filtered;
    if (m_currentKey == songKey) {
        m_currentKey.clear();
        m_current = SongInfo{};
        m_currentIndex = -1;
        if (m_hooks.pause)
            m_hooks.pause();
        emit currentSongChanged();
    } else {
        // adjust index
        if (m_currentIndex > removedIndex)
            --m_currentIndex;
        if (m_currentIndex >= 0 && m_currentIndex < m_queue.size())
            m_current = m_queue.at(m_currentIndex);
    }
    emit queueChanged();
    saveState();
}

void QueueManager::reorderUpNext(int fromUpNext, int toUpNext)
{
    if (m_currentIndex < 0 || m_queue.isEmpty())
        return;
    const int curIdx = m_currentIndex;
    const int actualFrom = curIdx + 1 + fromUpNext;
    const int actualTo = curIdx + 1 + toUpNext;
    if (actualFrom < 0 || actualFrom >= m_queue.size() ||
        actualTo < 0 || actualTo >= m_queue.size())
        return;
    const SongInfo moved = m_queue.takeAt(actualFrom);
    m_queue.insert(actualTo, moved);
    m_currentIndex = curIdx;
    for (int i = 0; i < m_queue.size(); ++i)
        if (m_queue.at(i).key == m_currentKey) { m_currentIndex = i; break; }
    emit queueChanged();
    saveState();
}

void QueueManager::clearQueue()
{
    m_queue.clear();
    m_currentKey.clear();
    m_current = SongInfo{};
    m_currentIndex = -1;
    m_baseQueueBeforeShuffle.clear();
    if (m_hooks.pause)
        m_hooks.pause();
    emit queueChanged();
    emit currentSongChanged();
    saveState();
}

void QueueManager::toggleShuffle()
{
    m_shuffleOn = !m_shuffleOn;
    const SongInfo current = m_current;
    if (m_shuffleOn) {
        m_baseQueueBeforeShuffle = m_queue;
        QVector<SongInfo> others;
        for (const SongInfo &s : m_queue)
            if (s.key != current.key)
                others.append(s);
        for (int i = others.size() - 1; i > 0; --i) {
            int j = QRandomGenerator::global()->bounded(i + 1);
            std::swap(others[i], others[j]);
        }
        QVector<SongInfo> shuffled;
        if (!current.key.isEmpty())
            shuffled.append(current);
        shuffled += others;
        m_queue = shuffled;
        for (int i = 0; i < m_queue.size(); ++i)
            if (m_queue.at(i).key == current.key) { m_currentIndex = i; break; }
    } else {
        if (!m_baseQueueBeforeShuffle.isEmpty()) {
            m_queue = m_baseQueueBeforeShuffle;
            m_baseQueueBeforeShuffle.clear();
            for (int i = 0; i < m_queue.size(); ++i)
                if (m_queue.at(i).key == current.key) { m_currentIndex = i; break; }
        }
    }
    if (m_settings) {
        m_settings->setValue(QStringLiteral("is_shuffle_on"), m_shuffleOn);
        m_settings->sync();
    }
    emit shuffleChanged();
    emit queueChanged();
    saveState();
}

void QueueManager::toggleRepeat()
{
    if (m_repeatMode == RepeatOff)
        m_repeatMode = RepeatAll;
    else if (m_repeatMode == RepeatAll)
        m_repeatMode = RepeatOne;
    else
        m_repeatMode = RepeatOff;
    if (m_settings) {
        m_settings->setValue(QStringLiteral("repeat_mode"), m_repeatMode);
        m_settings->sync();
    }
    emit repeatModeChanged();
    saveState();
}

void QueueManager::togglePlayPause()
{
    if (m_currentKey.isEmpty())
        return;
    if (!m_hooks.paused || !m_hooks.play || !m_hooks.pause)
        return;
    if (m_hooks.paused()) {
        // cold-start: if no media loaded, load
        m_hooks.play();
    } else {
        m_hooks.pause();
    }
    emit isPlayingChanged(!m_hooks.paused());
}

void QueueManager::play()
{
    if (m_hooks.play)
        m_hooks.play();
}

void QueueManager::pause()
{
    if (m_hooks.pause)
        m_hooks.pause();
}

void QueueManager::seekToMs(qint64 ms)
{
    if (m_hooks.seekToMs)
        m_hooks.seekToMs(ms);
}

void QueueManager::setVolume(int percent)
{
    if (m_hooks.setVolume)
        m_hooks.setVolume(percent);
}

void QueueManager::stop()
{
    if (m_hooks.pause)
        m_hooks.pause();
    // keep current
}

void QueueManager::onTrackEnded()
{
    if (m_repeatMode == RepeatOne) {
        // restart current
        seekToMs(0);
        if (m_hooks.play)
            m_hooks.play();
        return;
    }
    playNext();
}

void QueueManager::onPlaybackError(const QString &message)
{
    m_continuousFailures++;
    if (m_continuousFailures < 3) {
        playNext();
    } else {
        m_continuousFailures = 0;
        if (m_hooks.pause)
            m_hooks.pause();
        emit playbackError(message);
    }
    emit isPlayingChanged(false);
}

void QueueManager::onPositionTick(qint64 positionMs, qint64 durationMs)
{
    Q_UNUSED(positionMs);
    Q_UNUSED(durationMs);
    prefetchNextIfNeeded();
}

void QueueManager::prefetchNextIfNeeded()
{
    // Skeleton hook (M2.1): stream resolution hadir M3. Untuk lagu lokal
    // tidak ada yang di-prefetch. Implementasi penuh: kalau next.isStream &&
    // needsResolution && (duration - position) <= 8000, resolve async.
    if (m_queue.isEmpty() || m_currentIndex < 0)
        return;
    const int nextIdx = m_currentIndex + 1;
    if (nextIdx >= m_queue.size())
        return;
    const SongInfo &next = m_queue.at(nextIdx);
    if (!next.isStream || !next.needsResolution)
        return;
    if (m_prefetchingKeys.contains(next.key))
        return;
    m_prefetchingKeys.insert(next.key);
    // Resolve via resolver in background? M2.1: synchronous best-effort.
    SongInfo resolved;
    if (m_resolver && m_resolver(next.key, resolved) && !resolved.playUrl.isEmpty()) {
        QVector<SongInfo> q = m_queue;
        q[nextIdx] = resolved;
        m_queue = q;
        emit queueChanged();
    }
    m_prefetchingKeys.remove(next.key);
}

void QueueManager::publishCurrentSong(const SongInfo &song, int index, bool fireSongStarted)
{
    m_current = song;
    m_currentKey = song.key;
    m_currentIndex = index;
    if (index >= 0 && index < m_queue.size() && m_queue.at(index).key != song.key) {
        // ensure
    }
    // keep queue entry in sync
    if (index >= 0 && index < m_queue.size()) {
        QVector<SongInfo> q = m_queue;
        q[index] = song;
        m_queue = q;
    }
    emit currentSongChanged();
    if (fireSongStarted)
        emit songStarted(song.key);
}

void QueueManager::loadCurrentAndPlay()
{
    if (m_currentKey.isEmpty())
        return;
    if (m_current.playUrl.isEmpty()) {
        // try resolve
        SongInfo resolved;
        if (m_resolver && m_resolver(m_currentKey, resolved) && !resolved.playUrl.isEmpty()) {
            m_current = resolved;
            if (m_currentIndex >= 0 && m_currentIndex < m_queue.size()) {
                QVector<SongInfo> q = m_queue;
                q[m_currentIndex] = resolved;
                m_queue = q;
            }
        }
    }
    if (m_hooks.load && !m_current.playUrl.isEmpty()) {
        const QUrl url = m_current.playUrl.startsWith(QStringLiteral("http"))
                             ? QUrl(m_current.playUrl)
                             : QUrl::fromLocalFile(m_current.playUrl);
        m_hooks.load(url);
    }
    if (m_hooks.play)
        m_hooks.play();
    // continuousFailures reset on actual playing; handled by caller/isPlayingChanged
}

QString QueueManager::queueKeyAt(int index) const
{
    if (index < 0 || index >= m_queue.size())
        return {};
    return m_queue.at(index).key;
}

void QueueManager::setPlaying(bool playing)
{
    if (m_isPlaying == playing)
        return;
    m_isPlaying = playing;
    emit isPlayingChanged(m_isPlaying);
}

qint64 QueueManager::positionMs() const
{
    if (!m_hooks.position)
        return 0;
    return static_cast<qint64>(m_hooks.position() * 1000.0);
}

QString QueueManager::queueTitleAt(int index) const
{
    if (index < 0 || index >= m_queue.size())
        return {};
    return m_queue.at(index).title;
}
