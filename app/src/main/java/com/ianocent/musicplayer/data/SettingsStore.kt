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

    var isSocialSignalsEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOCIAL_SIGNALS_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SOCIAL_SIGNALS_ENABLED, value).apply()

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

    companion object {
        private const val KEY_DARK_MODE = "is_dark_mode"
        private const val KEY_VOICE_ASSISTANT = "voice_assistant_enabled"
        private const val KEY_SOCIAL_SIGNALS_ENABLED = "social_signals_enabled"
        private const val KEY_PILL_AT_BOTTOM = "is_pill_at_bottom"
        private const val KEY_MINI_LAYOUT_INDEX = "mini_layout_index"
        private const val KEY_SORT_MODE = "sort_mode"
        private const val KEY_LAST_RECAP_CHECK_TS = "last_recap_check_ts"
        private const val KEY_USER_NAME = "user_name"

        /** For receivers/listener services the system instantiates itself. */
        fun from(context: Context): SettingsStore =
            SettingsStore(context.getSharedPreferences(SongStore.PREFS_NAME, Context.MODE_PRIVATE))
    }
}
