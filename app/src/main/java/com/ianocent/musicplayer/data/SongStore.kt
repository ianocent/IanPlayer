package com.ianocent.musicplayer.data

import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single owner of everything persisted about Songs.
 *
 * Owns prefs keys, the Song<->JSON codec, and write timing. Callers never touch
 * pref keys or JSON field names. Storage swap (e.g. Room) is an internal change.
 *
 * Wire formats are identical to the legacy hand-rolled codecs this module replaces,
 * so existing installs read their data unchanged.
 */
class SongStore(
    private val prefs: SharedPreferences,
    private val scope: CoroutineScope
) {

    // == Stream songs cache ==
    //
    // Metadata for every stream song seen (search results, trending, queue members).
    // Loaded once, synchronously, at construction: id-keyed lookups below depend on
    // it, and callers no longer need to know about any load ordering.
    private val _streamCache = MutableStateFlow(loadStreamSongs())
    val streamSongsCache: StateFlow<Map<Long, Song>> = _streamCache

    val cachedSongs: Map<Long, Song> get() = _streamCache.value

    fun findSongById(id: Long): Song? = _streamCache.value[id]

    fun addToStreamSongsCache(songs: List<Song>) {
        val updated = _streamCache.value.toMutableMap()
        var changed = false
        for (s in songs) {
            if (s.id !in updated) {
                updated[s.id] = s
                changed = true
            }
        }
        if (!changed) return
        _streamCache.value = updated
        persistStreamSongs()
    }

    private fun persistStreamSongs() {
        val snapshot = _streamCache.value
        scope.launch(Dispatchers.IO) {
            val arr = JSONArray()
            snapshot.forEach { (_, song) -> arr.put(songToJson(song)) }
            prefs.edit().putString(KEY_STREAM_SONGS_CACHE, arr.toString()).apply()
        }
    }

    private fun loadStreamSongs(): Map<Long, Song> {
        val raw = prefs.getString(KEY_STREAM_SONGS_CACHE, null) ?: return emptyMap()
        return try {
            val arr = JSONArray(raw)
            val map = mutableMapOf<Long, Song>()
            for (i in 0 until arr.length()) {
                jsonToSong(arr.getJSONObject(i))?.let { map[it.id] = it }
            }
            map
        } catch (_: Exception) { emptyMap() }
    }

    // == Persisted song-id lists (trending / For You / genre caches) ==
    //
    // A cache entry stores song IDs; Songs themselves resolve against the stream
    // cache owned above. Saving also merges the songs into the cache, matching
    // legacy behaviour.

    fun saveCachedSongIds(key: String, songs: List<Song>) {
        addToStreamSongsCache(songs)
        scope.launch(Dispatchers.IO) {
            val arr = JSONArray()
            songs.forEach { arr.put(it.id) }
            prefs.edit().putString(key, arr.toString()).apply()
        }
    }

    fun loadCachedSongs(key: String): List<Song> {
        val raw = prefs.getString(key, null) ?: return emptyList()
        val cache = _streamCache.value
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<Song>()
            for (i in 0 until arr.length()) {
                cache[arr.getLong(i)]?.let { list.add(it) }
            }
            list
        } catch (_: Exception) { emptyList() }
    }

    // == Favorites ==

    fun loadFavoriteIds(): Set<Long> {
        val raw = prefs.getString(KEY_FAVORITE_IDS, null) ?: return emptySet()
        return raw.split(",").mapNotNull { it.toLongOrNull() }.toSet()
    }

    fun saveFavoriteIds(ids: Set<Long>) {
        doPut(KEY_FAVORITE_IDS, ids.joinToString(","))
    }

    // == Play counts ==

    fun loadPlayCounts(): Map<Long, Int> {
        val json = prefs.getString(KEY_PLAY_COUNTS, null) ?: return emptyMap()
        return try {
            val obj = JSONObject(json)
            val map = mutableMapOf<Long, Int>()
            obj.keys().forEach { key -> map[key.toLong()] = obj.getInt(key) }
            map
        } catch (_: Exception) { emptyMap() }
    }

    fun savePlayCounts(counts: Map<Long, Int>) {
        scope.launch(Dispatchers.IO) {
            val obj = JSONObject()
            counts.forEach { (id, count) -> obj.put(id.toString(), count) }
            prefs.edit().putString(KEY_PLAY_COUNTS, obj.toString()).apply()
        }
    }

    // == Play history (90-day window) ==

    fun loadPlayHistory(): List<Pair<Long, Long>> {
        val json = prefs.getString(KEY_PLAY_HISTORY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            val list = mutableListOf<Pair<Long, Long>>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(Pair(obj.getLong("id"), obj.getLong("ts")))
            }
            list
        } catch (_: Exception) { emptyList() }
    }

    /** Records a play event and trims the history to its 90-day window. */
    fun recordPlayed(songId: Long, now: Long = System.currentTimeMillis()) {
        val history = loadPlayHistory().toMutableList()
        history.add(Pair(songId, now))
        val cutoff = now - HISTORY_WINDOW_MS
        savePlayHistoryInternal(history.filter { it.second > cutoff })
    }

    private fun savePlayHistoryInternal(history: List<Pair<Long, Long>>) {
        scope.launch(Dispatchers.IO) {
            val arr = JSONArray()
            history.forEach { (id, ts) ->
                arr.put(JSONObject().put("id", id).put("ts", ts))
            }
            prefs.edit().putString(KEY_PLAY_HISTORY, arr.toString()).apply()
        }
    }

    // == Playlists ==

    fun loadPlaylists(): List<Playlist> {
        val jsonString = prefs.getString(KEY_PLAYLISTS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonString)
            val loaded = mutableListOf<Playlist>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val idsArray = obj.getJSONArray("songIds")
                val ids = mutableListOf<Long>()
                for (j in 0 until idsArray.length()) ids.add(idsArray.getLong(j))
                val imageUri = if (obj.has("imageUri")) obj.getString("imageUri") else null
                loaded.add(Playlist(id = obj.getLong("id"), name = obj.getString("name"), songIds = ids, imageUri = imageUri))
            }
            loaded
        } catch (_: Exception) { emptyList() }
    }

    fun savePlaylists(playlists: List<Playlist>) {
        scope.launch(Dispatchers.IO) {
            val jsonArray = JSONArray()
            playlists.forEach { playlist ->
                val obj = JSONObject()
                obj.put("id", playlist.id)
                obj.put("name", playlist.name)
                obj.put("songIds", JSONArray(playlist.songIds))
                playlist.imageUri?.let { obj.put("imageUri", it) }
                jsonArray.put(obj)
            }
            prefs.edit().putString(KEY_PLAYLISTS, jsonArray.toString()).apply()
        }
    }

    // == Social signals ==
    //
    // Schema shared with SocialSignalListener (NotificationListenerService, cannot
    // receive constructor injection). Both sides go through the companion functions
    // below so the {"s","t"} JSON contract has exactly one definition.

    /** Signals within the 72-hour window, newest first. */
    fun loadSocialSignals(): List<Pair<String, Long>> {
        val cutoff = System.currentTimeMillis() - SOCIAL_SIGNAL_WINDOW_MS
        return readSocialSignals(prefs).filter { it.second > cutoff }.sortedByDescending { it.second }
    }

    fun clearSocialSignals() {
        prefs.edit().remove(KEY_SOCIAL_SIGNALS).apply()
    }

    companion object {
        const val PREFS_NAME = "ian_player_prefs"

        private const val KEY_STREAM_SONGS_CACHE = "stream_songs_cache"
        private const val KEY_FAVORITE_IDS = "favorite_ids"
        private const val KEY_PLAY_COUNTS = "play_counts"
        private const val KEY_PLAY_HISTORY = "play_history"
        private const val KEY_PLAYLISTS = "playlists"
        private const val KEY_SOCIAL_SIGNALS = "social_signals"

        const val KEY_LAST_QUEUE = "last_queue"
        const val KEY_LAST_SONG_ID = "last_song_id"
        const val KEY_LAST_INDEX = "last_index"

        private const val HISTORY_WINDOW_MS = 90L * 24 * 60 * 60 * 1000
        const val SOCIAL_SIGNAL_WINDOW_MS = 3L * 24 * 60 * 60 * 1000 // 72h

        // == Queue state (written by the playback module, restored at startup) ==

        fun saveQueueState(prefs: SharedPreferences, queue: List<Song>, currentSongId: Long, index: Int) {
            val arr = JSONArray()
            queue.forEach { s -> arr.put(songToJson(s)) }
            prefs.edit()
                .putString(KEY_LAST_QUEUE, arr.toString())
                .putLong(KEY_LAST_SONG_ID, currentSongId)
                .putInt(KEY_LAST_INDEX, index)
                .apply()
        }

        data class RestoredQueue(val queue: List<Song>, val currentSongId: Long, val index: Int)

        fun restoreQueueState(prefs: SharedPreferences): RestoredQueue? {
            val queueJson = prefs.getString(KEY_LAST_QUEUE, null) ?: return null
            val savedSongId = prefs.getLong(KEY_LAST_SONG_ID, -1L)
            val savedIndex = prefs.getInt(KEY_LAST_INDEX, -1)
            if (savedSongId < 0 || savedIndex < 0) return null
            return try {
                val arr = JSONArray(queueJson)
                val restoredQueue = mutableListOf<Song>()
                for (i in 0 until arr.length()) {
                    jsonToSong(arr.getJSONObject(i))?.let { restoredQueue.add(it) }
                }
                if (restoredQueue.isNotEmpty()) {
                    RestoredQueue(restoredQueue, savedSongId, savedIndex.coerceIn(0, restoredQueue.size - 1))
                } else null
            } catch (_: Exception) { null }
        }

        // == Shared social-signal schema (see loadSocialSignals) ==

        fun readSocialSignals(prefs: SharedPreferences): List<Pair<String, Long>> {
            val raw = prefs.getString(KEY_SOCIAL_SIGNALS, null) ?: return emptyList()
            return try {
                val arr = JSONArray(raw)
                val list = mutableListOf<Pair<String, Long>>()
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val s = obj.optString("s", "")
                    if (s.isNotBlank()) list.add(Pair(s, obj.optLong("t", 0L)))
                }
                list
            } catch (_: Exception) { emptyList() }
        }

        fun writeSocialSignals(prefs: SharedPreferences, signals: List<Pair<String, Long>>) {
            val arr = JSONArray()
            signals.forEach { (s, t) ->
                arr.put(JSONObject().put("s", s).put("t", t))
            }
            prefs.edit().putString(KEY_SOCIAL_SIGNALS, arr.toString()).apply()
        }

        // == The one Song codec ==

        fun songToJson(song: Song): JSONObject {
            val obj = JSONObject()
            obj.put("id", song.id)
            obj.put("title", song.title)
            obj.put("artist", song.artist)
            obj.put("album", song.album)
            obj.put("duration", song.duration)
            obj.put("uri", song.uri.toString())
            obj.put("isStream", song.isStream)
            song.remoteArtUrl?.let { obj.put("remoteArtUrl", it) }
            song.remoteId?.let { obj.put("remoteId", it) }
            song.source?.let { obj.put("source", it) }
            obj.put("dateAdded", song.dateAdded)
            return obj
        }

        fun jsonToSong(obj: JSONObject): Song? {
            return try {
                Song(
                    id = obj.getLong("id"),
                    title = obj.getString("title"),
                    artist = obj.optString("artist", "Unknown Artist"),
                    duration = obj.optLong("duration", 0L),
                    uri = android.net.Uri.parse(obj.getString("uri")),
                    album = obj.optString("album", "Unknown Album"),
                    isStream = obj.optBoolean("isStream", false),
                    remoteArtUrl = if (obj.has("remoteArtUrl")) obj.optString("remoteArtUrl", null) else null,
                    dateAdded = obj.optLong("dateAdded", 0L),
                    remoteId = if (obj.has("remoteId")) obj.optString("remoteId", null) else null,
                    source = if (obj.has("source")) obj.optString("source", null) else null
                )
            } catch (_: Exception) { null }
        }

        private fun doPut(prefs: SharedPreferences, key: String, value: String) {
            prefs.edit().putString(key, value).apply()
        }
    }

    private fun doPut(key: String, value: String) = Companion.doPut(prefs, key, value)
}
