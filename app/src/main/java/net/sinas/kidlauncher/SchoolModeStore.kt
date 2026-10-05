package net.sinas.kidlauncher

import android.content.Context

object SchoolModeStore {
    private const val PREFS_NAME = "parent_settings"
    private const val KEY_ENABLED = "school_mode_enabled"
    private const val KEY_SCHEDULE_ENABLED = "school_mode_schedule_enabled"
    private const val KEY_START_MINUTES = "school_mode_start_minutes"
    private const val KEY_END_MINUTES = "school_mode_end_minutes"
    private const val DEFAULT_START_MINUTES = 8 * 60
    private const val DEFAULT_END_MINUTES = 13 * 60

    fun isEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun isScheduleEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SCHEDULE_ENABLED, false)

    fun setScheduleEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SCHEDULE_ENABLED, enabled).apply()
    }

    // Minuten seit Mitternacht (0-1439), damit Stunde und Minute nicht getrennt gespeichert werden müssen.
    fun getStartMinutes(context: Context): Int =
        prefs(context).getInt(KEY_START_MINUTES, DEFAULT_START_MINUTES)

    fun setStartMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_START_MINUTES, minutes).apply()
    }

    fun getEndMinutes(context: Context): Int =
        prefs(context).getInt(KEY_END_MINUTES, DEFAULT_END_MINUTES)

    fun setEndMinutes(context: Context, minutes: Int) {
        prefs(context).edit().putInt(KEY_END_MINUTES, minutes).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
