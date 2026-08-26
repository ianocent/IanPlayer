package com.ianocent.musicplayer.data

import android.net.Uri
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakeLyricCacheDao : LyricCacheDao {
    val store = mutableMapOf<String, LyricCacheEntry>()
    override suspend fun getByKey(key: String): LyricCacheEntry? = store[key]
    override suspend fun upsert(entry: LyricCacheEntry) {
        store[entry.key] = entry
    }
}

/**
 * LyricRepository cache-expiry policy through its interface, with the injected
 * clock — no network is touched: every case resolves from the fake cache.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LyricRepositoryTest {

    private val dayMs = 24L * 60 * 60 * 1000

    private fun syncedJson() =
        org.json.JSONArray().put(org.json.JSONObject().put("t", 1000L).put("s", "hello")).toString()

    private fun song() = Song(1, "Title", "Artist", 0L, Uri.parse("content://t/1"))

    @Test
    fun freshSyncedCacheIsServedWithoutRefetching() = runBlocking {
        val dao = FakeLyricCacheDao()
        var now = 10_000_000L
        val repo = LyricRepository(dao) { now }
        dao.upsert(LyricCacheEntry("title|artist", syncedJson(), null, now))

        val result = repo.fetchSyncedLyric(song())
        assertEquals(listOf(LyricLine(1000L, "hello")), result)
    }

    @Test
    fun clockJustBeforeExpiryStillHitsTheCache() = runBlocking {
        val dao = FakeLyricCacheDao()
        val cachedAt = 0L
        var now = 7 * dayMs - 1 // one ms inside the TTL window
        val repo = LyricRepository(dao) { now }
        dao.upsert(LyricCacheEntry("title|artist", syncedJson(), null, cachedAt))

        assertEquals(listOf(LyricLine(1000L, "hello")), repo.fetchSyncedLyric(song()))
    }

    @Test
    fun freshNoneMarkerSuppressesLookup() = runBlocking {
        val dao = FakeLyricCacheDao()
        var now = 5 * dayMs
        val repo = LyricRepository(dao) { now }
        dao.upsert(LyricCacheEntry("title|artist", "NONE", null, now - 2 * dayMs))

        assertNull(repo.fetchSyncedLyric(song()))
    }

    @Test
    fun freshPlainCacheIsServed() = runBlocking {
        val dao = FakeLyricCacheDao()
        var now = 1_000_000L
        val repo = LyricRepository(dao) { now }
        dao.upsert(LyricCacheEntry("title|artist", null, "plain words", now))

        assertEquals("plain words", repo.fetchPlainLyric(song()))
    }
}
