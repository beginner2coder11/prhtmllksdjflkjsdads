package com.web2app.data

import android.content.Context
import com.web2app.models.AppConfig
import com.web2app.models.appConfigToJson
import com.web2app.models.parseAppConfig
import org.json.JSONArray
import java.util.UUID

/**
 * Persists the list of apps built in the builder into SharedPreferences.
 * Everything is stored as a single JSON array string under [KEY_APPS].
 */
class AppRepository(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns all saved apps, newest last (insertion order). */
    fun getApps(): List<AppConfig> {
        val raw = prefs.getString(KEY_APPS, null) ?: return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.let { parseAppConfig(it) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /** Returns a single app by id, or null if not found. */
    fun getApp(id: String): AppConfig? = getApps().firstOrNull { it.id == id }

    /**
     * Inserts a new app (if [config.id] is blank) or updates the existing one.
     * Returns the saved config (with a generated id when new).
     */
    fun saveApp(config: AppConfig): AppConfig {
        val withId = if (config.id.isBlank()) {
            config.copy(id = UUID.randomUUID().toString())
        } else {
            config
        }
        val current = getApps().toMutableList()
        val index = current.indexOfFirst { it.id == withId.id }
        if (index >= 0) current[index] = withId else current.add(withId)
        persist(current)
        return withId
    }

    fun deleteApp(id: String) {
        persist(getApps().filterNot { it.id == id })
    }

    private fun persist(apps: List<AppConfig>) {
        val array = JSONArray()
        for (app in apps) array.put(appConfigToJson(app))
        prefs.edit().putString(KEY_APPS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "web2app_builder"
        private const val KEY_APPS = "apps"
    }
}
