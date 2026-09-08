package com.aarush.cpm.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.authDataStore by preferencesDataStore(name = "auth_prefs")

/**
 * Fixes the "Remember me" bug: previously the checkbox only flipped a flag on the User row
 * in Room and nothing ever read it back, so the app never actually remembered anyone. This
 * repository is the missing persistence layer — DataStore survives process death and app
 * restarts (Room alone doesn't help here since nothing re-read it on launch), so it's the
 * right place to remember *who* was logged in between sessions.
 */
class AuthPreferencesRepository(private val context: Context) {
    private val rememberedEmailKey = stringPreferencesKey("remembered_email")

    val rememberedEmail: Flow<String?> = context.authDataStore.data.map { it[rememberedEmailKey] }

    suspend fun setRememberedEmail(email: String) {
        context.authDataStore.edit { it[rememberedEmailKey] = email }
    }

    suspend fun clearRememberedEmail() {
        context.authDataStore.edit { it.remove(rememberedEmailKey) }
    }
}
