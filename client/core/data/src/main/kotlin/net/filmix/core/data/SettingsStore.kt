package net.filmix.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "filmix_settings")

/**
 * User preferences. Mirrors the handful the reference app keeps in
 * SharedPreferences; only the ones the client actually honours live here.
 */
class SettingsStore(private val context: Context) {

    /** Preferred stream height, or null to always take the best available. */
    val preferredQuality: Flow<Int?> = context.settingsDataStore.data
        .map { it[KEY_QUALITY]?.takeIf { value -> value > 0 } }

    suspend fun preferredQuality(): Int? = preferredQuality.first()

    suspend fun setPreferredQuality(quality: Int?) {
        context.settingsDataStore.edit { prefs ->
            if (quality == null) prefs.remove(KEY_QUALITY) else prefs[KEY_QUALITY] = quality
        }
    }

    private companion object {
        val KEY_QUALITY = intPreferencesKey("preferred_quality")
    }
}
