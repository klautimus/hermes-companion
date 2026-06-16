package org.hermes.community.companion.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

internal val Context.legacyDataStore by preferencesDataStore(name = "hermes_settings")

sealed class StorageMode {
    object Encrypted : StorageMode()
    data class Plaintext(val reason: String) : StorageMode()
    data class Unavailable(val reason: String) : StorageMode()
}

class SessionManager(private val context: Context) {
    companion object {
        const val SETUP_COMPLETE_KEY = "setup_complete"
        const val DEFAULT_URL = ""
        const val DEFAULT_USERNAME = ""
        const val DEFAULT_PASSWORD = ""
        const val DEFAULT_BOARD = "default"

        val LEGACY_KEY_BASE_URL: Preferences.Key<String> = stringPreferencesKey("base_url")
        val LEGACY_KEY_USERNAME: Preferences.Key<String> = stringPreferencesKey("username")
        val LEGACY_KEY_PASSWORD: Preferences.Key<String> = stringPreferencesKey("password")
        val LEGACY_KEY_BOARD: Preferences.Key<String> = stringPreferencesKey("board")
    }

    private val _storageMode: StorageMode by lazy {
        try {
            SessionMigration.encryptedPrefs(context.applicationContext)
            StorageMode.Encrypted
        } catch (e: Exception) {
            Log.e("SessionManager", "EncryptedSharedPreferences unavailable, security degraded", e)
            StorageMode.Plaintext(reason = e.message ?: "Unknown error")
        }
    }

    private val prefs: SharedPreferences by lazy {
        when (val mode = _storageMode) {
            is StorageMode.Encrypted -> SessionMigration.encryptedPrefs(context.applicationContext)
            is StorageMode.Plaintext -> context.getSharedPreferences("hermes_settings_fallback", Context.MODE_PRIVATE)
            else -> error("unreachable")
        }
    }

    init {
        when (val mode = _storageMode) {
            is StorageMode.Encrypted -> Log.i("SessionManager", "Storage mode: Encrypted (Android Keystore)")
            is StorageMode.Plaintext -> Log.w("SessionManager", "Storage mode: Plaintext (Keystore unavailable: ${mode.reason})")
            is StorageMode.Unavailable -> Log.e("SessionManager", "Storage mode: Unavailable (${mode.reason})")
        }
    }

    fun getStorageMode(): StorageMode = _storageMode

    private val KEY_URL = "base_url"
    private val KEY_USERNAME = "username"
    private val KEY_PASSWORD = "password"
    private val KEY_BOARD = "board"
    private val KEY_SETUP_COMPLETE = SETUP_COMPLETE_KEY

    val baseUrl: Flow<String> = prefs.flowForKey(KEY_URL, DEFAULT_URL)
    val username: Flow<String> = prefs.flowForKey(KEY_USERNAME, DEFAULT_USERNAME)
    val password: Flow<String> = prefs.flowForKey(KEY_PASSWORD, DEFAULT_PASSWORD)
    val board: Flow<String> = prefs.flowForKey(KEY_BOARD, DEFAULT_BOARD)
    val setupComplete: Flow<Boolean> = prefs.flowForBooleanKey(KEY_SETUP_COMPLETE, false)

    suspend fun setBaseUrl(url: String) {
        prefs.edit().putString(KEY_URL, url).apply()
    }

    suspend fun setUsername(user: String) {
        prefs.edit().putString(KEY_USERNAME, user).apply()
    }

    suspend fun setPassword(pass: String) {
        prefs.edit().putString(KEY_PASSWORD, pass).apply()
    }

    suspend fun setBoard(board: String) {
        prefs.edit().putString(KEY_BOARD, board).apply()
    }

    suspend fun setSetupComplete() {
        prefs.edit().putBoolean(KEY_SETUP_COMPLETE, true).apply()
    }

    suspend fun isConfigured(): Boolean {
        return !prefs.getString(KEY_URL, null).isNullOrBlank() &&
               !prefs.getString(KEY_USERNAME, null).isNullOrBlank() &&
               !prefs.getString(KEY_PASSWORD, null).isNullOrBlank()
    }

    suspend fun getPasswordSnapshot(): String {
        return prefs.getString(KEY_PASSWORD, "") ?: ""
    }

    suspend fun clearAll() {
        prefs.edit().clear().apply()
    }
}

private fun SharedPreferences.flowForKey(key: String, default: String): Flow<String> = callbackFlow {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
        if (changedKey == key) {
            trySend(getString(key, default) ?: default)
        }
    }
    registerOnSharedPreferenceChangeListener(listener)
    trySend(getString(key, default) ?: default)
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}

private fun SharedPreferences.flowForBooleanKey(key: String, default: Boolean): Flow<Boolean> = callbackFlow {
    val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changedKey ->
        if (changedKey == key) {
            trySend(getBoolean(key, default))
        }
    }
    registerOnSharedPreferenceChangeListener(listener)
    trySend(getBoolean(key, default))
    awaitClose { unregisterOnSharedPreferenceChangeListener(listener) }
}
