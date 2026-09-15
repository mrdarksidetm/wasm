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
    private val PERSON_NAME_KEY = stringPreferencesKey("person_name")
    private val PROFILE_IMAGE_PATH_KEY = stringPreferencesKey("profile_image_path")

    // Flow that emits the current saved name (or empty string if not set)
    val userIdentity: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[USER_NAME_KEY] ?: ""
    }

    // Flow for personalized person name displayed on Home and Personalize screens
    val personName: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[PERSON_NAME_KEY] ?: (prefs[USER_NAME_KEY] ?: "")
    }

    // Flow for saved profile image local file path
    val profileImagePath: Flow<String?> = context.dataStore.data.map { prefs ->
        prefs[PROFILE_IMAGE_PATH_KEY]
    }

    // Function to save the selected "Who are you?" name
    suspend fun saveUserIdentity(name: String) {
        context.dataStore.edit { prefs ->
            prefs[USER_NAME_KEY] = name
            if (prefs[PERSON_NAME_KEY].isNullOrEmpty()) {
                prefs[PERSON_NAME_KEY] = name
            }
        }
    }

    // Function to save personalized person name
    suspend fun savePersonName(name: String) {
        context.dataStore.edit { prefs ->
            prefs[PERSON_NAME_KEY] = name
            if (prefs[USER_NAME_KEY].isNullOrEmpty()) {
                prefs[USER_NAME_KEY] = name
            }
        }
    }

    // Function to save or clear profile photo local file path
    suspend fun saveProfileImagePath(path: String?) {
        context.dataStore.edit { prefs ->
            if (path == null) {
                prefs.remove(PROFILE_IMAGE_PATH_KEY)
            } else {
                prefs[PROFILE_IMAGE_PATH_KEY] = path
            }
        }
    }
}

