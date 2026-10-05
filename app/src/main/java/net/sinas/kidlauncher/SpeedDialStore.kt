package net.sinas.kidlauncher

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class SpeedDialContact(val label: String, val number: String, val photoUri: String? = null)

object SpeedDialStore {
    private const val PREFS_NAME = "speed_dial"
    private const val KEY_CONTACTS = "contacts"

    fun load(context: Context): List<SpeedDialContact> {
        val json = prefs(context).getString(KEY_CONTACTS, null) ?: return emptyList()
        val array = JSONArray(json)
        return (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            SpeedDialContact(
                obj.getString("label"),
                obj.getString("number"),
                if (obj.isNull("photoUri")) null else obj.optString("photoUri", null)
            )
        }
    }

    fun save(context: Context, contacts: List<SpeedDialContact>) {
        val array = JSONArray()
        contacts.forEach { contact ->
            array.put(
                JSONObject().apply {
                    put("label", contact.label)
                    put("number", contact.number)
                    put("photoUri", contact.photoUri)
                }
            )
        }
        prefs(context).edit().putString(KEY_CONTACTS, array.toString()).apply()
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}
