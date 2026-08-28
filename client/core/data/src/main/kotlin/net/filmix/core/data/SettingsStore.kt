package net.filmix.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
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

    /** Package of the chosen external player, or null for the built-in one. */
    val externalPlayerPackage: Flow<String?> = context.settingsDataStore.data
        .map { it[KEY_EXTERNAL_PLAYER]?.takeIf(String::isNotBlank) }

    suspend fun externalPlayerPackage(): String? = externalPlayerPackage.first()

    suspend fun setExternalPlayerPackage(packageName: String?) {
        context.settingsDataStore.edit { prefs ->
            if (packageName == null) prefs.remove(KEY_EXTERNAL_PLAYER) else prefs[KEY_EXTERNAL_PLAYER] = packageName
        }
    }

    /** Catalog sort, stored as the raw API value so new options stay readable. */
    suspend fun catalogSort(): String? =
        context.settingsDataStore.data.first()[KEY_SORT]

    suspend fun setCatalogSort(apiValue: String) {
        context.settingsDataStore.edit { it[KEY_SORT] = apiValue }
    }

    suspend fun catalogAscending(): Boolean =
        context.settingsDataStore.data.first()[KEY_SORT_ASC] ?: false

    suspend fun setCatalogAscending(ascending: Boolean) {
        context.settingsDataStore.edit { it[KEY_SORT_ASC] = ascending }
    }

    private companion object {
        val KEY_QUALITY = intPreferencesKey("preferred_quality")
        val KEY_EXTERNAL_PLAYER = stringPreferencesKey("external_player_package")
        val KEY_SORT = stringPreferencesKey("catalog_sort")
        val KEY_SORT_ASC = booleanPreferencesKey("catalog_sort_asc")
    }
}
