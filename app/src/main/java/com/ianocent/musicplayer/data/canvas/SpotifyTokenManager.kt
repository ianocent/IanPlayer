package com.ianocent.musicplayer.data.canvas

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Anonymous Spotify web-player access token, no user login needed.
 *
 * Spotify blocked the legacy `get_access_token` endpoint, so the token is now
 * requested through `open.spotify.com/api/token` which requires a generated
 * TOTP challenge:
 *   1. Fetch the latest secret tuple from the public `spot-secrets-go` dict.
 *   2. XOR-map its values ((i % 33) + 9) and join them as DECIMAL strings
 *      (matching the reference JS implementation).
 *   3. HMAC-SHA1 the joined string's UTF-8 bytes, hex-encode them, and parse
 *      that hex back into the raw secret bytes (reference: OTPAuth.Secret.fromHex).
 *   4. Build a 6-digit TOTP (period 30s) for both local clock and Spotify's
 *      server clock, then send `?reason=init&productType=mobile-web-player&...`.
 *
 * The resulting token is `isAnonymous:true`, short-lived (~1 hour), and is
 * only used in-process — never persisted, never sent to the canvas proxy.
 */
object SpotifyTokenManager {
    private const val TAG = "SpotifyToken"

    private const val SECRETS_URL =
        "https://raw.githubusercontent.com/xyloflake/spot-secrets-go/refs/heads/main/secrets/secretDict.json"
    private const val SERVER_TIME_URL = "https://open.spotify.com/api/server-time"
    private const val TOKEN_URL = "https://open.spotify.com/api/token"

    private const val TOKEN_PERIOD_MS = 30_000L

    @Volatile private var cachedToken: String? = null
    @Volatile private var cachedExpiry: Long = 0L
    @Volatile private var cachedTokenSpDc: String? = null
    @Volatile private var cachedSecrets: List<Int>? = null
    @Volatile private var cachedSecretsVersion: String? = null
    @Volatile private var secretsFetchedAt: Long = 0L

    /**
     * Returns a bearer token, refreshing when the cached one is stale.
     * [spDc] null/blank → anonymous token (search only). With a user's
     * sp_dc cookie the token gains full canvaz access.
     */
    @Synchronized
    fun getAccessToken(spDc: String? = null): String? {
        val now = System.currentTimeMillis()
        val dc = spDc?.takeIf { it.isNotBlank() }
        cachedToken?.takeIf { now + 60_000L < cachedExpiry }?.takeIf {
            // cached token must match the credential scope it was minted with
            (dc == null) == (cachedTokenSpDc == null) && (dc == null || dc == cachedTokenSpDc)
        }?.let { return it }
        return fetchToken(dc)?.let { (token, expiryMs) ->
            cachedToken = token
            cachedExpiry = expiryMs
            cachedTokenSpDc = dc
            token
        }
    }

    /** Best-effort cached token even if slightly expired (used as a last-resort fallback). */
    fun peekToken(): String? = cachedToken

    private fun fetchToken(spDc: String?): Pair<String, Long>? {
        return try {
            val nowMs = System.currentTimeMillis()
            val secrets = obtainSecrets(nowMs) ?: run {
                CanvasLog.e("TOKEN: no secrets available, skipping token fetch")
                return null
            }

            val totpLocal = computeTotp(nowMs, secrets)
            val serverMs = runCatching { fetchServerTimeMs() }.onFailure {
                CanvasLog.e("TOKEN: server-time fetch failed, using local clock: ${it.message}")
            }.getOrNull() ?: nowMs
            // Reference implementation truncates the server timestamp by 30 before
            // computing the TOTP; replicate exactly.
            val totpServer = computeTotp(serverMs / 30, secrets)
            val version = cachedSecretsVersion ?: "19"
            CanvasLog.d("TOKEN: requesting token ver=$version totp=$totpLocal totpServer=$totpServer")

            val url = URL("$TOKEN_URL?reason=init&productType=mobile-web-player" +
                "&totp=$totpLocal&totpVer=$version&totpServer=$totpServer")
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", BROWSER_UA)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Origin", "https://open.spotify.com")
            conn.setRequestProperty("Referer", "https://open.spotify.com")
            if (spDc != null) {
                conn.setRequestProperty("Cookie", "sp_dc=$spDc")
                CanvasLog.d("TOKEN: using sp_dc cookie (authed token)")
            }
            val body = readBody(conn)
            CanvasLog.d("TOKEN: http=${conn.responseCode} bodyLen=${body?.length ?: -1}")
            if (conn.responseCode !in 200..299 || body.isNullOrBlank()) {
                CanvasLog.e("TOKEN: rejected http=${conn.responseCode} body=${body?.take(200)}")
                return null
            }

            val json = JSONObject(body)
            val token = json.optString("accessToken")
            if (token.isBlank()) {
                CanvasLog.e("TOKEN: 200 but accessToken empty: ${body.take(200)}")
                return null
            }
            if (spDc != null && json.optBoolean("isAnonymous", false)) {
                CanvasLog.e(
                    "TOKEN: sp_dc INVALID/rotated (server minted anonymous token) — " +
                        "paste a fresh sp_dc in Settings"
                )
            }
            val expiry = json.optLong("accessTokenExpirationTimestampMs", nowMs + 3600_000L)
            CanvasLog.d("TOKEN: ok prefix=${token.take(10)}... validForMs=${expiry - nowMs}")
            token to expiry
        } catch (e: Exception) {
            CanvasLog.e("TOKEN: fetchToken failed", e)
            null
        }
    }

    private fun obtainSecrets(nowMs: Long): List<Int>? {
        val cached = cachedSecrets
        if (cached != null && nowMs - secretsFetchedAt < 3600_000L) return cached
        return try {
            val conn = URL(SECRETS_URL).openConnection() as HttpURLConnection
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            val body = readBody(conn)
            if (conn.responseCode !in 200..299 || body.isNullOrBlank()) {
                cached
            } else {
                val json = JSONObject(body)
                val versionKey = json.keys().asSequence()
                    .mapNotNull { it.toIntOrNull() }
                    .maxOrNull()
                if (versionKey == null) {
                    CanvasLog.e("SECRETS: dict has no numeric versions: ${body.take(200)}")
                    cached
                } else {
                    val arr = json.getJSONArray(versionKey.toString())
                    val list = (0 until arr.length()).map { arr.getInt(it) }
                    CanvasLog.d("SECRETS: loaded version=$versionKey len=${list.size}")
                    cachedSecrets = list
                    cachedSecretsVersion = versionKey.toString()
                    secretsFetchedAt = nowMs
                    list
                }
            }
        } catch (e: Exception) {
            CanvasLog.e("SECRETS: fetch failed (will use cached if any)", e)
            cached
        }
    }

    private fun fetchServerTimeMs(): Long {
        val conn = URL(SERVER_TIME_URL).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.setRequestProperty("User-Agent", BROWSER_UA)
        val body = readBody(conn)
        if (conn.responseCode !in 200..299 || body.isNullOrBlank()) {
            throw IllegalStateException("server-time http ${conn.responseCode}")
        }
        val serverTimeSeconds = JSONObject(body).optDouble("serverTime", -1.0)
        if (serverTimeSeconds <= 0) throw IllegalStateException("bad server time")
        return (serverTimeSeconds * 1000).toLong()
    }

    /** TOTP with reference XOR-mapping + decimal-join semantics. */
    internal fun computeTotp(timestampMs: Long, secrets: List<Int>): String {
        val joined = buildString {
            secrets.forEachIndexed { i, v -> append((v xor ((i % 33) + 9)).toString()) }
        }
        val utf8Bytes = joined.toByteArray(StandardCharsets.UTF_8)
        val hex = utf8Bytes.joinToString("") { "%02x".format(it) }
        val secretBytes = hex.chunked(2).map { it.toInt(16).toByte() }

        val counter = timestampMs / TOKEN_PERIOD_MS
        val msg = ByteArray(8)
        var c = counter
        // big-endian 8-byte counter
        for (k in 7 downTo 0) {
            msg[k] = (c and 0xFF).toByte()
            c = c ushr 8
        }
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(secretBytes.toByteArray(), "HmacSHA1"))
        val hash = mac.doFinal(msg)
        val offset = (hash[hash.size - 1].toInt() and 0x0F)
        val code = ((hash[offset].toInt() and 0x7F) shl 24) or
            ((hash[offset + 1].toInt() and 0xFF) shl 16) or
            ((hash[offset + 2].toInt() and 0xFF) shl 8) or
            (hash[offset + 3].toInt() and 0xFF)
        return (code % 1_000_000).toString().padStart(6, '0')
    }

    private fun readBody(conn: HttpURLConnection): String? = try {
        val stream = if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream
        stream?.bufferedReader()?.use { it.readText() }
    } catch (_: Exception) {
        null
    }

    fun invalidate() {
        cachedToken = null
    }

    private const val BROWSER_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36"
}
