package com.atlas.hermescompanion.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "hermes_settings")

class SessionManager(private val context: Context) {
    companion object {
        const val DEFAULT_URL = "https://android.kevlarscreations.com"
        const val DEFAULT_USERNAME = "kevin"
        const val DEFAULT_PASSWORD="Kevi67n!1991!" // Must match companion daemon auth.json
        const val DEFAULT_BOARD = "default"
    }

    private val KEY_URL = stringPreferencesKey("base_url")
    private val KEY_USERNAME = stringPreferencesKey("username")
    private val KEY_PASSWORD = stringPreferencesKey("password")
    private val KEY_BOARD = stringPreferencesKey("board")

    val baseUrl: Flow<String> = context.dataStore.data.map { it[KEY_URL] ?: DEFAULT_URL }
    val username: Flow<String> = context.dataStore.data.map { it[KEY_USERNAME] ?: DEFAULT_USERNAME }
    val password: Flow<String> = context.dataStore.data.map { it[KEY_PASSWORD] ?: DEFAULT_PASSWORD }
    val board: Flow<String> = context.dataStore.data.map { it[KEY_BOARD] ?: DEFAULT_BOARD }

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

    // Simpler: expose latest value via a direct read
    suspend fun getPasswordSnapshot(): String {
        return context.dataStore.data.first()[KEY_PASSWORD] ?: DEFAULT_PASSWORD
    }
}
