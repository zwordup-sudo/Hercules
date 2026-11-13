package com.rafa.hercules.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Apariencia de la app (v1.15): Claro / Oscuro / Sistema.
 * Es una preferencia del dispositivo, NO forma parte del respaldo de entrenamientos.
 */
enum class AppearanceMode(val storageValue: String, val label: String) {
    SYSTEM("system", "Sistema"),
    LIGHT("light", "Claro"),
    DARK("dark", "Oscuro");

    companion object {
        /** Valor inicial y de lectura: cualquier dato ausente o desconocido cae en Sistema. */
        fun fromStorage(raw: String?): AppearanceMode =
            entries.firstOrNull { it.storageValue == raw } ?: SYSTEM
    }
}

private val Context.appearanceDataStore by preferencesDataStore(name = "apariencia")

/** Lee y guarda la selección de apariencia en DataStore; el cambio se aplica de inmediato. */
class AppearancePreferences(private val dataStore: DataStore<Preferences>) {
    val mode: Flow<AppearanceMode> = dataStore.data.map { prefs ->
        AppearanceMode.fromStorage(prefs[KEY_MODE])
    }

    suspend fun setMode(mode: AppearanceMode) {
        dataStore.edit { it[KEY_MODE] = mode.storageValue }
    }

    companion object {
        internal val KEY_MODE = stringPreferencesKey("modo_apariencia")
    }
}

val Context.appearancePreferences: AppearancePreferences
    get() = AppearancePreferences(appearanceDataStore)
