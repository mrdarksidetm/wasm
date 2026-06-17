package com.mrdartsidetm.wasm.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extension property to create the DataStore instance
private val Context.dataStore by preferencesDataStore(name = "user_prefs")

class UserPreferencesRepository(private val context: Context) {
    private val USER_NAME_KEY = stringPreferencesKey("user_identity")

    // Flow that emits the current saved name (or empty string if not set)
    val userIdentity: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[USER_NAME_KEY] ?: ""
    }

    // Function to save the selected "Who are you?" name
    suspend fun saveUserIdentity(name: String) {
        context.dataStore.edit { prefs ->
            prefs[USER_NAME_KEY] = name
        }
    }
}
