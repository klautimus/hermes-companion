package org.hermes.community.companion.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import kotlinx.coroutines.flow.first

/**
 * Migrate credentials from plaintext DataStore to EncryptedSharedPreferences.
 *
 * Idempotent: only runs if the legacy datastore has values and encrypted store is empty.
 * After successful migration, the legacy datastore file is cleared.
 */
object SessionMigration {
    private const val TAG = "SessionMigration"
    private const val PREFS_NAME = "hermes_settings_secure"
    private const val MIGRATION_FLAG = "migration_complete_v1"

    private val Context.migrationLegacyDataStore by preferencesDataStore(name = "hermes_settings")

    suspend fun migrateIfNeeded(context: Context) {
        val securePrefs = encryptedPrefs(context)
        if (securePrefs.getBoolean(MIGRATION_FLAG, false)) return

        val appContext = context.applicationContext
        val legacy = appContext.migrationLegacyDataStore
        val legacyPrefs = legacy.data.first()

        val url = legacyPrefs[SessionManager.LEGACY_KEY_BASE_URL] ?: return  // nothing to migrate
        val username = legacyPrefs[SessionManager.LEGACY_KEY_USERNAME] ?: ""
        val password = legacyPrefs[SessionManager.LEGACY_KEY_PASSWORD] ?: ""
        val board = legacyPrefs[SessionManager.LEGACY_KEY_BOARD] ?: "default"

        securePrefs.edit()
            .putString("base_url", url)
            .putString("username", username)
            .putString("password", password)
            .putString("board", board)
            .putBoolean(MIGRATION_FLAG, true)
            .apply()

        // Clear legacy
        legacy.edit { it.clear() }
        Log.i(TAG, "Migrated credentials from DataStore to EncryptedSharedPreferences")
    }

    fun encryptedPrefs(context: Context): SharedPreferences {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            PREFS_NAME,
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }
}
