package com.hozinking.hozintv.ui

import android.content.Context

/**
 * Penyimpanan kecil untuk toggle "Pengaturan Fitur" (SharedPreferences).
 * Default: semua mati — PiP, gesture, dan FLAG_SECURE opt-in oleh user.
 */
class SettingsStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var pipEnabled: Boolean
        get() = prefs.getBoolean(KEY_PIP, false)
        set(v) = prefs.edit().putBoolean(KEY_PIP, v).apply()

    var gesturesEnabled: Boolean
        get() = prefs.getBoolean(KEY_GESTURES, false)
        set(v) = prefs.edit().putBoolean(KEY_GESTURES, v).apply()

    var secureFlag: Boolean
        get() = prefs.getBoolean(KEY_SECURE, false)
        set(v) = prefs.edit().putBoolean(KEY_SECURE, v).apply()

    companion object {
        private const val PREFS = "hozintv_settings"
        private const val KEY_PIP = "pip_enabled"
        private const val KEY_GESTURES = "gestures_enabled"
        private const val KEY_SECURE = "secure_flag"
    }
}
