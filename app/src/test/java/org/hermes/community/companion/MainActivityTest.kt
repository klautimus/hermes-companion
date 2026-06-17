package org.hermes.community.companion

import android.content.Intent
import android.net.Uri
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

    private fun createIntent(uri: String): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(uri))
    }

    private fun parseDeepLink(intent: Intent): org.hermes.community.companion.data.DeepLinkConfig? {
        val data = intent.data ?: return null
        if (data.scheme != "hermescompanion") return null
        if (data.host != "configure") return null
        return org.hermes.community.companion.data.DeepLinkConfig(
            serverUrl = data.getQueryParameter("url") ?: "",
            username = data.getQueryParameter("user") ?: "",
            password = data.getQueryParameter("pass") ?: "",
            token = data.getQueryParameter("token"),
            board = data.getQueryParameter("board") ?: "",
        )
    }

    @Test
    fun parseDeepLinkIntent_extractsToken() {
        val intent = createIntent(
            "hermescompanion://configure?host=192.168.1.1&port=8777&user=kevin&token=abc123&board=default"
        )
        val config = parseDeepLink(intent)
        assertNotNull(config)
        assertEquals("abc123", config?.token)
    }

    @Test
    fun parseDeepLinkIntent_keepsPasswordForBackcompat() {
        val intent = createIntent(
            "hermescompanion://configure?host=192.168.1.1&port=8777&user=kevin&pass=plaintextpw&board=default"
        )
        val config = parseDeepLink(intent)
        assertNotNull(config)
        assertEquals("plaintextpw", config?.password)
        assertNull(config?.token)
    }

    @Test
    fun parseDeepLinkIntent_returnsNullForWrongScheme() {
        val intent = createIntent(
            "https://example.com/configure?host=192.168.1.1&port=8777&user=kevin&token=abc123"
        )
        val config = parseDeepLink(intent)
        assertNull(config)
    }

    @Test
    fun parseDeepLinkIntent_returnsNullForNullData() {
        val intent = Intent(Intent.ACTION_MAIN)
        val config = parseDeepLink(intent)
        assertNull(config)
    }

    @Test
    fun parseDeepLinkIntent_fullUriWithAllFields() {
        val intent = createIntent(
            "hermescompanion://configure?url=https://android.kevlarscreations.com&user=kevin&token=securetoken456&board=main"
        )
        val config = parseDeepLink(intent)
        assertNotNull(config)
        assertEquals("https://android.kevlarscreations.com", config?.serverUrl)
        assertEquals("kevin", config?.username)
        assertEquals("securetoken456", config?.token)
        assertEquals("main", config?.board)
    }

    @Test
    fun parseDeepLinkIntent_tokenWithPassword_prefersBoth() {
        val intent = createIntent(
            "hermescompanion://configure?url=https://example.com&user=kevin&pass=oldpw&token=newtoken&board=default"
        )
        val config = parseDeepLink(intent)
        assertNotNull(config)
        assertEquals("newtoken", config?.token)
        assertEquals("oldpw", config?.password)
    }
}
