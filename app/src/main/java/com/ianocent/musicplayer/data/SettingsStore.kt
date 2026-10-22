package com.ianocent.musicplayer.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Single owner of user-facing settings: keys, defaults, and read/write timing
 * live here. Callers see typed accessors only; renaming or re-typing a setting
 * is an internal change to this module.
 */
class SettingsStore(private val prefs: SharedPreferences) {

    var isDarkMode: Boolean
        get() = prefs.getBoolean(KEY_DARK_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_DARK_MODE, value).apply()

    var isVoiceAssistantEnabled: Boolean
        get() = prefs.getBoolean(KEY_VOICE_ASSISTANT, false)
        set(value) = prefs.edit().putBoolean(KEY_VOICE_ASSISTANT, value).apply()

    var isPillAtBottom: Boolean
        get() = prefs.getBoolean(KEY_PILL_AT_BOTTOM, false)
        set(value) = prefs.edit().putBoolean(KEY_PILL_AT_BOTTOM, value).apply()

    var miniLayoutIndex: Int
        get() = prefs.getInt(KEY_MINI_LAYOUT_INDEX, 0)
        set(value) = prefs.edit().putInt(KEY_MINI_LAYOUT_INDEX, value).apply()

    var sortMode: Int
        get() = prefs.getInt(KEY_SORT_MODE, 0)
        set(value) = prefs.edit().putInt(KEY_SORT_MODE, value).apply()

    var lastRecapCheckTs: Long
        get() = prefs.getLong(KEY_LAST_RECAP_CHECK_TS, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_RECAP_CHECK_TS, value).apply()

    var userName: String
        get() = prefs.getString(KEY_USER_NAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USER_NAME, value).apply()

    var avatarUri: String?
        get() = prefs.getString(KEY_AVATAR_URI, null)
        set(value) = prefs.edit().putString(KEY_AVATAR_URI, value).apply()

    var bannerUri: String?
        get() = prefs.getString(KEY_BANNER_URI, null)
        set(value) = prefs.edit().putString(KEY_BANNER_URI, value).apply()

    var tabOrder: String
        get() = prefs.getString(KEY_TAB_ORDER, DEFAULT_TAB_ORDER) ?: DEFAULT_TAB_ORDER
        set(value) = prefs.edit().putString(KEY_TAB_ORDER, value).apply()

    var dontShowUpdate: Boolean
        get() = prefs.getBoolean(KEY_DONT_SHOW_UPDATE, false)
        set(value) = prefs.edit().putBoolean(KEY_DONT_SHOW_UPDATE, value).apply()

    var canvasMode: Int
        get() = prefs.getInt(KEY_CANVAS_MODE, 0)
        set(value) = prefs.edit().putInt(KEY_CANVAS_MODE, value).apply()

    /**
     * The user's Spotify `sp_dc` session cookie. Optional; blank means canvas
     * lookups stay disabled (anonymous tokens can search but cannot fetch
     * canvaz video URLs).
     *
     * A dev-default cookie is checked in so canvas works out of the box on
     * personal builds. It is a live session credential — it will INVALIDATE
     * when the owning web session logs out/rotates, and it must never ship in
     * a publicly distributed APK.
     */
    var spotifySpDc: String
        get() = prefs.getString(KEY_SPOTIFY_SP_DC, "")?.trim().takeUnless { it.isNullOrEmpty() }
            ?: DEFAULT_SPOTIFY_SP_DC
        set(value) = prefs.edit().putString(KEY_SPOTIFY_SP_DC, value.trim()).apply()

    /** Optimization phase 0 stubs — typed accessors, unused by UI until wired. */

    var miniPlayerStyle: Int
        get() = prefs.getInt(KEY_MINI_PLAYER_STYLE, 0)
        set(value) = prefs.edit().putInt(KEY_MINI_PLAYER_STYLE, value).apply()

    var canvasSource: Int
        get() = prefs.getInt(KEY_CANVAS_SOURCE, CANVAS_SOURCE_AUTO)
        set(value) = prefs.edit().putInt(KEY_CANVAS_SOURCE, value).apply()

    var canvasEnabled: Boolean
        get() = prefs.getBoolean(KEY_CANVAS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_CANVAS_ENABLED, value).apply()

    var canvasIntensity: Float
        get() = prefs.getFloat(KEY_CANVAS_INTENSITY, 0.6f)
        set(value) = prefs.edit().putFloat(KEY_CANVAS_INTENSITY, value).apply()

    var canvasBlur: Float
        get() = prefs.getFloat(KEY_CANVAS_BLUR, 16f)
        set(value) = prefs.edit().putFloat(KEY_CANVAS_BLUR, value).apply()

    var canvasDim: Float
        get() = prefs.getFloat(KEY_CANVAS_DIM, 0f)
        set(value) = prefs.edit().putFloat(KEY_CANVAS_DIM, value).apply()

    var motionScale: Float
        get() = prefs.getFloat(KEY_MOTION_SCALE, 1f)
        set(value) = prefs.edit().putFloat(KEY_MOTION_SCALE, value).apply()

    var reduceMotion: Boolean
        get() = prefs.getBoolean(KEY_REDUCE_MOTION, false)
        set(value) = prefs.edit().putBoolean(KEY_REDUCE_MOTION, value).apply()

    var glassEnabled: Boolean
        get() = prefs.getBoolean(KEY_GLASS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_GLASS_ENABLED, value).apply()

    /**
     * When true, the NowPlaying canvas (generative/video/ken-burns) is only
     * drawn behind the album-art + song-info + lyric section, fading to
     * transparent (revealing the plain ambient gradient) before the Up Next
     * list — instead of covering the entire player screen. Off by default so
     * behavior doesn't change for people who already like the full-screen look.
     */
    var canvasFadeBeforeUpnext: Boolean
        get() = prefs.getBoolean(KEY_CANVAS_FADE_BEFORE_UPNEXT, false)
        set(value) = prefs.edit().putBoolean(KEY_CANVAS_FADE_BEFORE_UPNEXT, value).apply()

    var glassBlur: Float
        get() = prefs.getFloat(KEY_GLASS_BLUR, 16f)
        set(value) = prefs.edit().putFloat(KEY_GLASS_BLUR, value).apply()

    var glassVibrancy: Float
        get() = prefs.getFloat(KEY_GLASS_VIBRANCY, 0.3f)
        set(value) = prefs.edit().putFloat(KEY_GLASS_VIBRANCY, value).apply()

    var glassLens: Float
        get() = prefs.getFloat(KEY_GLASS_LENS, 0f)
        set(value) = prefs.edit().putFloat(KEY_GLASS_LENS, value).apply()

    var glassHighlight: Float
        get() = prefs.getFloat(KEY_GLASS_HIGHLIGHT, 0.1f)
        set(value) = prefs.edit().putFloat(KEY_GLASS_HIGHLIGHT, value).apply()

    var seekBarStyle: Int
        get() = prefs.getInt(KEY_SEEK_BAR_STYLE, SEEK_BAR_CLASSIC)
        set(value) = prefs.edit().putInt(KEY_SEEK_BAR_STYLE, value).apply()

    var lyricsStyle: Int
        get() = prefs.getInt(KEY_LYRICS_STYLE, LYRICS_STYLE_DEFAULT)
        set(value) = prefs.edit().putInt(KEY_LYRICS_STYLE, value).apply()

    var crossfadeDurationMs: Int
        get() = prefs.getInt(KEY_CROSSFADE_DURATION_MS, CROSSFADE_OFF)
        set(value) = prefs.edit().putInt(KEY_CROSSFADE_DURATION_MS, value).apply()

    companion object {
        private const val KEY_DARK_MODE = "is_dark_mode"
        private const val KEY_VOICE_ASSISTANT = "voice_assistant_enabled"
        private const val KEY_PILL_AT_BOTTOM = "is_pill_at_bottom"
        private const val KEY_MINI_LAYOUT_INDEX = "mini_layout_index"
        private const val KEY_SORT_MODE = "sort_mode"
        private const val KEY_LAST_RECAP_CHECK_TS = "last_recap_check_ts"
        private const val KEY_USER_NAME = "user_name"
        private const val KEY_AVATAR_URI = "avatar_uri"
        private const val KEY_BANNER_URI = "banner_uri"
        private const val KEY_TAB_ORDER = "tab_order"
        private const val KEY_DONT_SHOW_UPDATE = "dont_show_update"
        private const val KEY_CANVAS_MODE = "canvas_mode"
        private const val KEY_SPOTIFY_SP_DC = "spotify_sp_dc"

        /**
         * Session cookie used as working default. Rotate by logging a fresh
         * web session and pasting the new value in Settings.
         */
        const val DEFAULT_SPOTIFY_SP_DC =
            "AQCJ-abAxY8u2rVF4udmx67Wy1AfKseEH_L0NgbGGBkFJCTojrPWctZWUijC8geFN8sU_3Ibtrjuossv4XXbG2ziR_NBov7y9qxHK02YmQcgibYzl_E7liArb4aXz0uFjqTIL0q_7C7tUA8X9p9XZdmHGUm9lSKHuTIs8Byj1Xvk1zBIRVyejDYZ5qHoK-ljzDA6DkY-kR4O90yNd9w"
        private const val KEY_MINI_PLAYER_STYLE = "mini_player_style"
        private const val KEY_CANVAS_SOURCE = "canvas_source"
        private const val KEY_CANVAS_ENABLED = "canvas_enabled"
        private const val KEY_CANVAS_INTENSITY = "canvas_intensity"
        private const val KEY_CANVAS_BLUR = "canvas_blur"
        private const val KEY_CANVAS_DIM = "canvas_dim"
        private const val KEY_MOTION_SCALE = "motion_scale"
        private const val KEY_REDUCE_MOTION = "reduce_motion"
        private const val KEY_GLASS_ENABLED = "glass_enabled"
        private const val KEY_CANVAS_FADE_BEFORE_UPNEXT = "canvas_fade_before_upnext"
        private const val KEY_GLASS_BLUR = "glass_blur"
        private const val KEY_GLASS_VIBRANCY = "glass_vibrancy"
        private const val KEY_GLASS_LENS = "glass_lens"
        private const val KEY_GLASS_HIGHLIGHT = "glass_highlight"
        private const val KEY_SEEK_BAR_STYLE = "seek_bar_style"
        private const val KEY_LYRICS_STYLE = "lyrics_style"
        private const val KEY_CROSSFADE_DURATION_MS = "crossfade_duration_ms"
        const val DEFAULT_TAB_ORDER = "songs,albums,stream,playlists"

        const val CROSSFADE_OFF = 0
        const val CROSSFADE_100 = 100
        const val CROSSFADE_300 = 300
        const val CROSSFADE_700 = 700
        const val CROSSFADE_1200 = 1200

        const val CANVAS_SOURCE_AUTO = 0

        const val SEEK_BAR_CLASSIC = 0
        const val SEEK_BAR_WAVE = 1
        const val SEEK_BAR_SQUIGGLE = 2
        const val SEEK_BAR_MINIMAL = 3

        const val LYRICS_STYLE_DEFAULT = 0

        const val CANVAS_OFF = 0
        const val CANVAS_GENERATIVE = 1
        const val CANVAS_VIDEO = 2
        const val CANVAS_KEN_BURNS = 3

        /** For receivers/listener services the system instantiates itself. */
        fun from(context: Context): SettingsStore =
            SettingsStore(context.getSharedPreferences(SongStore.PREFS_NAME, Context.MODE_PRIVATE))
    }
}