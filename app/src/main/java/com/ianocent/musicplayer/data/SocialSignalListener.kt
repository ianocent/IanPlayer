package com.ianocent.musicplayer.data

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.util.regex.Pattern

/**
 * Privacy-first feed-signal collector for the "For You" stream section.
 *
 * Indicator guarantees:
 * - Runs as a system-bound NotificationListenerService: NO foreground service, NO
 *   persistent notification, NO mic/camera/location indicator on any Android version.
 * - No RECORD_AUDIO, no screen capture, no new runtime permission dialog.
 *
 * Data guarantees:
 * - Only parses notifications from an explicit social/music app allowlist.
 * - Messaging apps (WhatsApp/Telegram) are gated on music keywords so private chat
 *   content is ignored unless it is clearly a song link/mention.
 * - Extracts only a short "artist - title" signal string; raw notification text is
 *   never stored. Signals live in SharedPreferences on this device only and are
 *   pruned after 72 hours.
 * - Feature is opt-in (pref "social_signals_enabled", default off) and the system
 *   grant can be revoked anytime from the notification access settings screen.
 */
class SocialSignalListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!SettingsStore.from(this).isSocialSignalsEnabled) return
        val pkg = sbn.packageName
        if (pkg !in ALLOWED_PACKAGES) return
        val notification = sbn.notification ?: return
        val text = buildText(notification) ?: return
        // Messaging apps: require an explicit music keyword so private chats are ignored.
        if (pkg in MESSAGING_PACKAGES && !MUSIC_KEYWORDS.matcher(text).find()) return
        val signal = extractSignal(text) ?: return
        storeSignal(signal)
    }

    override fun onListenerConnected() {
        // System re-binds us on demand; nothing to do here.
    }

    private fun prefs(): SharedPreferences =
        getSharedPreferences(SongStore.PREFS_NAME, Context.MODE_PRIVATE)

    private fun buildText(notification: Notification): String? {
        val title = notification.extras.getString(Notification.EXTRA_TITLE) ?: return null
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        return "$title $text $big".trim().ifBlank { null }
    }

    // Extract a compact "artist - title" style signal. Never stores the raw notification.
    private fun extractSignal(text: String): String? {
        val m1 = ARTIST_TITLE.matcher(text)
        if (m1.find()) {
            val a = m1.group(1).trim()
            val b = m1.group(2).trim()
            if (a.length >= 2 && b.length >= 2) return "$a - $b"
        }
        val m2 = LYRICS.matcher(text)
        if (m2.find()) {
            val a = m2.group(1).trim()
            if (a.length >= 3) return a
        }
        val m3 = SONG_BY.matcher(text)
        if (m3.find()) {
            val a = m3.group(1).trim()
            if (a.length >= 2) return a
        }
        return null
    }

    // Clock seam: the system instantiates this service itself, so tests subclass
    // and override instead of injecting a constructor dependency.
    protected open fun nowMs(): Long = System.currentTimeMillis()

    private fun storeSignal(signal: String) {
        val prefs = prefs()
        val now = nowMs()
        val cutoff = now - SongStore.SOCIAL_SIGNAL_WINDOW_MS
        val arr = SongStore.readSocialSignals(prefs).filter { it.second > cutoff }.toMutableList()
        // Dedupe: skip if the same signal was seen within the last hour.
        if (arr.any { it.first == signal && now - it.second < 60 * 60 * 1000L }) return
        arr.add(0, Pair(signal, now))
        if (arr.size > MAX_SIGNALS) arr.subList(MAX_SIGNALS, arr.size).clear()
        SongStore.writeSocialSignals(prefs, arr)
    }

    companion object {
        private const val MAX_SIGNALS = 60

        private val ALLOWED_PACKAGES = setOf(
            "com.facebook.katana", "com.facebook.orca",
            "com.instagram.android", "com.ss.android.ugc.aweme",
            "com.zhiliaoapp.musically", "com.twitter.android",
            "com.google.android.youtube", "com.spotify.music",
            "com.soundcloud.android", "com.pinterest", "com.reddit",
            "com.whatsapp", "org.telegram.messenger"
        )

        // Private chat apps: only react when the text clearly mentions music.
        private val MESSAGING_PACKAGES = setOf("com.whatsapp", "org.telegram.messenger")

        private val MUSIC_KEYWORDS = Pattern.compile(
            "\\b(song|songs|music|listen|lagu|musik|youtube|youtu\\.be|spotify|soundcloud|lirik|lyrics?|audio|cover|remix|feat\\.?)\\b",
            Pattern.CASE_INSENSITIVE
        )

        private val ARTIST_TITLE = Pattern.compile("([A-Za-z0-9][A-Za-z0-9 .&'!?()]{1,40})\\s*-\\s*([A-Za-z0-9][A-Za-z0-9 .&'!?()]{1,40})")
        private val LYRICS = Pattern.compile("([A-Za-z0-9][A-Za-z0-9 .&'!?()]{1,40})\\s*(lyrics|lirik|lyric)\\b", Pattern.CASE_INSENSITIVE)
        private val SONG_BY = Pattern.compile("(?:song|track|lagu)\\s+by\\s+([A-Za-z0-9][A-Za-z0-9 .&']{1,40})", Pattern.CASE_INSENSITIVE)

        fun isAccessGranted(context: Context): Boolean {
            val flat = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val component = ComponentName(context, SocialSignalListener::class.java).flattenToString()
            return flat.split(":").any { it.equals(component, ignoreCase = true) }
        }
    }
}
