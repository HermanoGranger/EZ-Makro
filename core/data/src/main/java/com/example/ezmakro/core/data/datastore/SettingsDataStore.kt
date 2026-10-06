package com.example.ezmakro.core.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * Generic, typed wrapper over DataStore Preferences. Feature code should not
 * depend on DataStore directly; instead they either use this wrapper or define
 * a small repository backed by it. Only primitives/strings are stored here —
 * no domain entities (those belong in Room, see core:database, Phase 3).
 */
class SettingsDataStore(
    private val dataStore: DataStore<Preferences>,
) {
    fun getString(key: String): Flow<String?> = dataStore.data.map { it[stringPreferencesKey(key)] }

    suspend fun setString(
        key: String,
        value: String,
    ) {
        dataStore.edit { it[stringPreferencesKey(key)] = value }
    }

    suspend fun remove(key: String) {
        dataStore.edit { it.remove(stringPreferencesKey(key)) }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    /**
     * Snapshot of every stored **string** value, keyed by preference name (A10).
     *
     * Deliberately generic: new settings are exported automatically without a
     * hard-coded key list. Non-string values are skipped — today none exist,
     * and a future typed preference must opt in explicitly.
     */
    suspend fun snapshot(): Map<String, String> =
        dataStore
            .data
            .first()
            .asMap()
            .filterValues { it is String }
            .mapKeys { it.key.name }
            .mapValues { it.value as String }

    /**
     * Writes [values] back without deleting anything that is already stored (A10/A6):
     * an import must never drop a local setting that the archive does not know about.
     * Empty input is a no-op, so restoring an archive without settings leaves the store untouched.
     */
    suspend fun restore(values: Map<String, String>) {
        if (values.isEmpty()) return
        dataStore.edit { prefs ->
            values.forEach { (key, value) -> prefs[stringPreferencesKey(key)] = value }
        }
    }
}

private val Context.dataStore by preferencesDataStore(name = "ezmakro_settings")

fun createSettingsDataStore(context: Context): SettingsDataStore = SettingsDataStore(context.dataStore)
