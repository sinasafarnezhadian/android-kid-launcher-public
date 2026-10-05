package net.sinas.kidlauncher

import android.content.Context

object ParentPinStore {
    private const val PREFS_NAME = "parent_settings"
    private const val KEY_PIN = "pin"
    private const val DEFAULT_PIN = "1234"

    fun getPin(context: Context): String =
        prefs(context).getString(KEY_PIN, DEFAULT_PIN) ?: DEFAULT_PIN

    fun setPin(context: Context, pin: String) {
        prefs(context).edit().putString(KEY_PIN, pin).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
