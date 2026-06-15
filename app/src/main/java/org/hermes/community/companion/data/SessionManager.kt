package org.hermes.community.companion.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "hermes_settings")

class SessionManager(private val context: Context) {
    companion object {
        // SETUP_COMPLETE flag
        const val SETUP_COMPLETE_KEY = "setup_complete"
        // Defaults for first-run (user must configure for their own server)
        const val DEFAULT_URL = ""
        const val DEFAULT_USERNAME = ""
        const val DEFAULT_PASSWORD = ""
        const val DEFAULT_BOARD = "default"
    }

    private val KEY_URL = stringPreferencesKey("base_url")
    private val KEY_USERNAME = stringPreferencesKey("username")
    private val KEY_PASSWORD = stringPreferencesKey("password")
    private val KEY_BOARD = stringPreferencesKey("board")
    private val KEY_SETUP_COMPLETE = booleanPreferencesKey(SETUP_COMPLETE_KEY)

    // Defaults for first-run (user must configure for their own server)
    val baseUrl: Flow<String> = context.dataStore.data.map { it[KEY_URL] ?: DEFAULT_URL }
    val username: Flow<String> = context.dataStore.data.map { it[KEY_USERNAME] ?: DEFAULT_USERNAME }
    val password: Flow<String> = context.dataStore.data.map { it[KEY_PASSWORD] ?: DEFAULT_PASSWORD }
    val board: Flow<String> = context.dataStore.data.map { it[KEY_BOARD] ?: DEFAULT_BOARD }
    val setupComplete: Flow<Boolean> = context.dataStore.data.map { it[KEY_SETUP_COMPLETE] ?: false }

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_URL] = url }
    }

    suspend fun setUsername(user: String) {
        context.dataStore.edit { it[KEY_USERNAME] = user }
    }

    suspend fun setPassword(pass: String) {
        context.dataStore.edit { it[KEY_PASSWORD] = pass }
    }

    suspend fun setBoard(board: String) {
        context.dataStore.edit { it[KEY_BOARD] = board }
    }

    suspend fun setSetupComplete() {
        context.dataStore.edit { it[KEY_SETUP_COMPLETE] = true }
    }

    suspend fun isConfigured(): Boolean {
        val prefs = context.dataStore.data.first()
        return !prefs[KEY_URL].isNullOrBlank() &&
               !prefs[KEY_USERNAME].isNullOrBlank() &&
               !prefs[KEY_PASSWORD].isNullOrBlank()
    }

    suspend fun getPasswordSnapshot(): String {
        return context.dataStore.data.first()[KEY_PASSWORD] ?: ""
    }

    // Test helper: clear all DataStore values
    suspend fun clearAll() {
        context.dataStore.edit {
            it[KEY_URL] = ""
            it[KEY_USERNAME] = ""
            it[KEY_PASSWORD] = ""
            it[KEY_BOARD] = "default"
            it[KEY_SETUP_COMPLETE] = false
        }
    }
}
