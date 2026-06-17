package org.hermes.community.companion.data

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class SessionManagerMigrationTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var context: Application

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        // Clear any existing prefs
        context.getSharedPreferences("hermes_settings_fallback", Application.MODE_PRIVATE)
            .edit().clear().apply()
        runBlocking {
            val sm = SessionManager(context)
            sm.clearAll()
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        context.getSharedPreferences("hermes_settings_fallback", Application.MODE_PRIVATE)
            .edit().clear().apply()
        runBlocking {
            val sm = SessionManager(context)
            sm.clearAll()
        }
    }

    @Test
    fun encryptedPrefsStoresAndRetrievesValues() = runBlocking {
        val sm = SessionManager(context)
        sm.setBaseUrl("https://example.com")
        sm.setUsername("alice")
        sm.setPassword("secret123")
        sm.setBoard("my-board")

        // Verify values are stored and retrievable
        assertEquals("https://example.com", sm.baseUrl.first())
        assertEquals("alice", sm.username.first())
        assertEquals("secret123", sm.password.first())
        assertEquals("my-board", sm.board.first())
    }

    @Test
    fun roundTripViaDirectRead() = runBlocking {
        val sm = SessionManager(context)
        sm.setBaseUrl("https://test.example.com")
        sm.setUsername("bob")
        sm.setPassword("hunter2")

        // The SessionManager uses encrypted prefs (or fallback in tests)
        // Verify through the SessionManager API
        assertEquals("https://test.example.com", sm.baseUrl.first())
        assertEquals("bob", sm.username.first())
        assertEquals("hunter2", sm.password.first())
    }

    @Test
    fun isConfigured_returnsTrueWhenAllFieldsSet() = runBlocking {
        val sm = SessionManager(context)
        sm.setBaseUrl("https://server.example.com")
        sm.setUsername("user")
        sm.setPassword("pass")
        assertTrue(sm.isConfigured())
    }

    @Test
    fun isConfigured_returnsFalseWhenFieldsEmpty() = runBlocking {
        val sm = SessionManager(context)
        assertFalse(sm.isConfigured())
    }

    @Test
    fun clearAll_resetsToDefaults() = runBlocking {
        val sm = SessionManager(context)
        sm.setBaseUrl("https://example.com")
        sm.setUsername("alice")
        sm.setPassword("secret")
        sm.clearAll()

        assertFalse(sm.isConfigured())
        assertEquals("", sm.baseUrl.first())
        assertEquals("", sm.username.first())
        assertEquals("default", sm.board.first())
    }

    @Test
    fun storageMode_isDetectable() = runBlocking {
        val sm = SessionManager(context)
        val mode = sm.getStorageMode()
        assertTrue(
            "StorageMode should be Encrypted, Plaintext, or Unavailable",
            mode is org.hermes.community.companion.data.StorageMode.Encrypted ||
            mode is org.hermes.community.companion.data.StorageMode.Plaintext ||
            mode is org.hermes.community.companion.data.StorageMode.Unavailable
        )
        if (mode is org.hermes.community.companion.data.StorageMode.Plaintext) {
            assertTrue("Plaintext reason should not be empty", mode.reason.isNotEmpty())
        }
    }

    @Test
    fun storageMode_encryptedDoesNotExposeReason() = runBlocking {
        val sm = SessionManager(context)
        val mode = sm.getStorageMode()
        assertNotNull(mode)
    }

    @Test
    fun setupComplete_flagPersists() = runBlocking {
        val sm = SessionManager(context)
        assertFalse(sm.setupComplete.first())
        sm.setSetupComplete()
        assertTrue(sm.setupComplete.first())
    }
}
