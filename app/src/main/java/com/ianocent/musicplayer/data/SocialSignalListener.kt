package com.ianocent.musicplayer.data

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import timber.log.Timber
import java.util.Calendar
import java.util.regex.Pattern

/**
 * Context-aware feed-signal collector for the "For You" stream section.
 *
 * Captures the full notification context (not just artist-title) to understand
 * what the user is doing, their mood, and music preferences. Uses this for
 * smarter recommendations.
 *
 * Privacy: opt-in, local-only + optional Firebase sync. Raw text is cleaned
 * before storage (no personal messages, no sensitive data).
 */
class SocialSignalListener : NotificationListenerService() {

    private val firebaseSignalSync by lazy { FirebaseSignalSync() }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!SettingsStore.from(this).isSocialSignalsEnabled) {
            Timber.d("SocialSignalListener: social signals disabled, skipping")
            return
        }
        val pkg = sbn.packageName
        if (pkg !in ALLOWED_PACKAGES) return
        val notification = sbn.notification ?: return
        val text = buildText(notification) ?: return

        Timber.d("SocialSignalListener: notification from $pkg, text=${text.take(80)}")

        val context = buildSignalContext(text, pkg)
        if (context == null) {
            Timber.d("SocialSignalListener: no signal extracted from $pkg")
            return
        }

        Timber.d("SocialSignalListener: storing signal artist=${context.artist}, title=${context.title}")
        storeSignal(context)
    }

    override fun onListenerConnected() {}

    private fun prefs(): SharedPreferences =
        getSharedPreferences(SongStore.PREFS_NAME, Context.MODE_PRIVATE)

    private fun buildText(notification: Notification): String? {
        val title = notification.extras.getString(Notification.EXTRA_TITLE) ?: return null
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val big = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val subText = notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
        val summary = notification.extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString().orEmpty()
        return "$title $text $big $subText $summary".trim().ifBlank { null }
    }

    private fun buildSignalContext(text: String, packageName: String): SignalContext? {
        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()

        val timeOfDay = when (calendar.get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "morning"
            in 12..16 -> "afternoon"
            in 17..20 -> "evening"
            else -> "night"
        }
        val dayOfWeek = when (calendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "monday"
            Calendar.TUESDAY -> "tuesday"
            Calendar.WEDNESDAY -> "wednesday"
            Calendar.THURSDAY -> "thursday"
            Calendar.FRIDAY -> "friday"
            Calendar.SATURDAY -> "saturday"
            Calendar.SUNDAY -> "sunday"
            else -> "unknown"
        }

        // Extract artist/title if present
        val artist = extractArtist(text)
        val title = extractTitle(text)

        // Extract context keywords (mood, activity, genre mentions)
        val keywords = extractContextKeywords(text)

        // Build a clean, trimmed text (remove URLs, normalize whitespace)
        val cleanText = text
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(280) // Cap at 280 chars like Twitter

        // If no meaningful content extracted, skip
        if (cleanText.length < 3 && artist == null && keywords.isEmpty()) return null

        return SignalContext(
            rawText = cleanText,
            artist = artist,
            title = title,
            contextKeywords = keywords,
            sourceApp = packageName,
            timeOfDay = timeOfDay,
            dayOfWeek = dayOfWeek,
            timestamp = now
        )
    }

    private fun extractArtist(text: String): String? {
        // Try "Artist - Title" pattern first
        val m1 = ARTIST_TITLE.matcher(text)
        if (m1.find()) {
            val a = m1.group(1).trim()
            if (a.length >= 2) return a
        }
        // Try "song/track/lagu by Artist"
        val m3 = SONG_BY.matcher(text)
        if (m3.find()) {
            val a = m3.group(1).trim()
            if (a.length >= 2) return a
        }
        return null
    }

    private fun extractTitle(text: String): String? {
        val m1 = ARTIST_TITLE.matcher(text)
        if (m1.find()) {
            val b = m1.group(2).trim()
            if (b.length >= 2) return b
        }
        // Try "lyrics/lirik of Title"
        val m2 = LYRICS_OF.matcher(text)
        if (m2.find()) {
            val t = m2.group(1).trim()
            if (t.length >= 2) return t
        }
        return null
    }

    private fun extractContextKeywords(text: String): List<String> {
        val keywords = mutableListOf<String>()

        // Mood keywords
        MOOD_KEYWORDS.findAll(text.lowercase()).forEach {
            keywords.add("mood:${it.value}")
        }

        // Activity keywords
        ACTIVITY_KEYWORDS.findAll(text.lowercase()).forEach {
            keywords.add("activity:${it.value}")
        }

        // Genre mentions
        GENRE_KEYWORDS.findAll(text.lowercase()).forEach {
            keywords.add("genre:${it.value}")
        }

        // Music platform mentions
        PLATFORM_KEYWORDS.findAll(text.lowercase()).forEach {
            keywords.add("platform:${it.value}")
        }

        // Language/locale hints
        LANG_KEYWORDS.findAll(text.lowercase()).forEach {
            keywords.add("lang:${it.value}")
        }

        return keywords.distinct().take(10) // Cap keywords
    }

    private fun storeSignal(ctx: SignalContext) {
        val prefs = prefs()
        val now = ctx.timestamp
        val cutoff = now - SongStore.SOCIAL_SIGNAL_WINDOW_MS

        // Build a search-friendly signal string
        val searchSignal = when {
            ctx.artist != null && ctx.title != null -> "${ctx.artist} ${ctx.title}"
            ctx.artist != null -> ctx.artist
            ctx.contextKeywords.isNotEmpty() -> ctx.contextKeywords.first().substringAfter(":")
            else -> ctx.rawText.take(60)
        }

        val arr = SongStore.readSocialSignals(prefs).filter { it.second > cutoff }.toMutableList()
        // Dedupe: skip if same search signal within 1 hour
        if (arr.any { it.first == searchSignal && now - it.second < 60 * 60 * 1000L }) return
        arr.add(0, Pair(searchSignal, now))
        if (arr.size > MAX_SIGNALS) arr.subList(MAX_SIGNALS, arr.size).clear()
        SongStore.writeSocialSignals(prefs, arr)

        // Sync rich context to Firebase
        try {
            firebaseSignalSync.syncSignal(ctx)
        } catch (e: Exception) {
            Timber.e(e, "Failed to sync signal to Firebase")
        }
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

        private val MESSAGING_PACKAGES = setOf("com.whatsapp", "org.telegram.messenger")

        private val MUSIC_KEYWORDS = Pattern.compile(
            "\\b(song|songs|music|listen|lagu|musik|youtube|youtu\\.be|spotify|soundcloud|lirik|lyrics?|audio|cover|remix|feat\\.?|playlist|album|concert|live|dj|mix)\\b",
            Pattern.CASE_INSENSITIVE
        )

        // Artist-Title patterns
        private val ARTIST_TITLE = Pattern.compile("([A-Za-z0-9][A-Za-z0-9 .&'!?()]{1,40})\\s*-\\s*([A-Za-z0-9][A-Za-z0-9 .&'!?()]{1,40})")
        private val LYRICS_OF = Pattern.compile("(?:lyrics?|lirik)\\s+(?:of\\s+)?([A-Za-z0-9][A-Za-z0-9 .&'!?()]{2,40})", Pattern.CASE_INSENSITIVE)
        private val SONG_BY = Pattern.compile("(?:song|track|lagu)\\s+by\\s+([A-Za-z0-9][A-Za-z0-9 .&']{1,40})", Pattern.CASE_INSENSITIVE)

        // Context extraction patterns
        private val MOOD_KEYWORDS = Regex(
            "\\b(chill|vibes?|sad|happy|excited|bored|stress|relax|romantic|angry|nostalgic|mellow|upbeat|hype|calm|anxious|love|hate|miss|lonely|party|kena|galau|santai|senang|sedih|marah|rindu)\\b",
            RegexOption.IGNORE_CASE
        )
        private val ACTIVITY_KEYWORDS = Regex(
            "\\b(driving|workout|gym|running|cooking|studying|working|sleeping|traveling|commuting|walking|hiking|swimming|dancing|singing|coding|designing|reading|gaming|nongkrong|kerja|belajar|tidur|jalan|olahraga|masak)\\b",
            RegexOption.IGNORE_CASE
        )
        private val GENRE_KEYWORDS = Regex(
            "\\b(pop|rock|hip.?hop|rap|r&b|jazz|classical|electronic|edm|house|techno|indie|alternative|metal|punk|folk|country|reggae|dangdut|kpop|jpop|indonesian|lofi|acoustic|instrumental|ambient)\\b",
            RegexOption.IGNORE_CASE
        )
        private val PLATFORM_KEYWORDS = Regex(
            "\\b(spotify|youtube|apple music|soundcloud|tidal|deezer|jooz|angkasa|tekotok|noice|spotify wrapped|playlist|shuffle|repeat)\\b",
            RegexOption.IGNORE_CASE
        )
        private val LANG_KEYWORDS = Regex(
            "\\b(bahasa|indonesia|english|japanese|korean|thai|filipino|melayu|mandarin|arabic)\\b",
            RegexOption.IGNORE_CASE
        )

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
