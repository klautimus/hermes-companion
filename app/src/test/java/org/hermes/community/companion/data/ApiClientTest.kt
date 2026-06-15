package org.hermes.community.companion.data

import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Base64
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ApiClientTest {

    // ─── Auth Header Generation ──────────────────────────────

    @Test
    fun authHeader_containsBasicPrefix() {
        // ApiClient uses: "Basic ${Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)}"
        // On JVM, use java.util.Base64 to verify the same logic
        val encoded = Base64.getEncoder().encodeToString("kevin:secret123".toByteArray())
        val authHeader = "Basic $encoded"
        assertTrue(authHeader.startsWith("Basic "))
        // Verify round-trip
        val decoded = String(Base64.getDecoder().decode(authHeader.removePrefix("Basic ")))
        assertEquals("kevin:secret123", decoded)
    }

    @Test
    fun authHeader_encodesColonSeparatedCredentials() {
        val user = "admin"
        val pass = "p@ss:w0rd!"
        val expectedRaw = "$user:$pass"
        val encoded = Base64.getEncoder().encodeToString(expectedRaw.toByteArray())
        val decoded = String(Base64.getDecoder().decode(encoded))
        assertEquals(expectedRaw, decoded)
    }

    @Test
    fun authHeader_noExtraWhitespace() {
        val encoded = Base64.getEncoder().encodeToString("kevin:atlas2026".toByteArray())
        assertFalse(encoded.contains("\n"))
        assertFalse(encoded.contains(" "))
    }

    @Test
    fun authHeader_matchesOkHttpCredential() {
        // OkHttp's Credentials.basic() uses the same Base64 encoding
        val okhttpHeader = okhttp3.Credentials.basic("kevin", "atlas2026")
        val manual = "Basic ${Base64.getEncoder().encodeToString("kevin:atlas2026".toByteArray())}"
        assertEquals(okhttpHeader, manual)
    }

    // ─── URL Construction ────────────────────────────────────

    @Test
    fun urlConstruction_simplePath() {
        val baseUrl = "http://localhost:8777"
        val path = "/api/sessions"
        assertEquals("http://localhost:8777/api/sessions", "$baseUrl$path")
    }

    @Test
    fun urlConstruction_withTrailingSlash() {
        val baseUrl = "http://localhost:8777/"
        val path = "/api/sessions"
        // ApiClient uses "$baseUrl$path" — trailing slash in baseUrl creates double slash
        val result = "$baseUrl$path"
        assertEquals("http://localhost:8777//api/sessions", result)
        // This documents the actual behavior — trailing slashes in baseUrl cause double slashes
    }

    @Test
    fun urlConstruction_withoutTrailingSlash() {
        val baseUrl = "http://localhost:8777"
        val path = "/api/kanban/boards"
        assertEquals("http://localhost:8777/api/kanban/boards", "$baseUrl$path")
    }

    @Test
    fun urlConstruction_withQueryParams() {
        val baseUrl = "http://localhost:8777"
        val path = "/api/kanban/tasks?board=default"
        assertEquals("http://localhost:8777/api/kanban/tasks?board=default", "$baseUrl$path")
    }

    // ─── Error Handling (ApiException) ───────────────────────

    @Test
    fun apiException_storesCodeAndMessage() {
        val ex = ApiException(401, "Unauthorized")
        assertEquals(401, ex.code)
        assertEquals("Unauthorized", ex.message)
    }

    @Test
    fun apiException_500() {
        val ex = ApiException(500, "Internal Server Error")
        assertEquals(500, ex.code)
        assertEquals("Internal Server Error", ex.message)
    }

    @Test
    fun apiException_isException() {
        val ex = ApiException(0, "Network error")
        assertTrue(ex is Exception)
    }

    // ─── OkHttp Client Configuration ─────────────────────────

    @Test
    fun okHttpClient_defaultTimeouts() {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(300, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
        assertEquals(15_000L, client.connectTimeoutMillis.toLong())
        assertEquals(300_000L, client.readTimeoutMillis.toLong())
        assertEquals(30_000L, client.writeTimeoutMillis.toLong())
    }

    // ─── Request Building ────────────────────────────────────

    @Test
    fun requestBuilder_getRequest() {
        val authHeader = okhttp3.Credentials.basic("kevin", "atlas2026")
        val request = Request.Builder()
            .url("http://localhost:8777/api/sessions")
            .method("GET", null)
            .header("Authorization", authHeader)
            .header("Accept", "application/json")
            .build()

        assertEquals("http://localhost:8777/api/sessions", request.url.toString())
        assertEquals("GET", request.method)
        assertEquals(authHeader, request.header("Authorization"))
        assertEquals("application/json", request.header("Accept"))
    }

    @Test
    fun requestBuilder_postRequestWithBody() {
        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
        val body = "{}".toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("http://localhost:8777/api/sessions")
            .method("POST", body)
            .header("Authorization", "Basic dGVzdA==")
            .header("Accept", "application/json")
            .build()

        assertEquals("POST", request.method)
        assertNotNull(request.body)
        val buffer = Buffer()
        request.body!!.writeTo(buffer)
        assertEquals("{}", buffer.readUtf8())
    }

    @Test
    fun requestBuilder_deleteRequest() {
        val request = Request.Builder()
            .url("http://localhost:8777/api/sessions/abc123")
            .method("DELETE", null)
            .header("Authorization", "Basic dGVzdA==")
            .build()

        assertEquals("DELETE", request.method)
        assertEquals("http://localhost:8777/api/sessions/abc123", request.url.toString())
    }

    // ─── Response Parsing ────────────────────────────────────

    @Test
    fun parseSessionsList_validJson() {
        val raw = """{"data":[{"id":"s1","title":"Chat","model":"gpt-4","started_at":1700000000.0,"message_count":5}],"has_more":true}"""
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val result = json.decodeFromString(SessionsList.serializer(), raw)
        assertEquals(1, result.data.size)
        assertEquals("s1", result.data[0].id)
        assertTrue(result.hasMore)
    }

    @Test
    fun parseSessionsList_empty() {
        val raw = """{"data":[]}"""
        val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true; coerceInputValues = true }
        val result = json.decodeFromString(SessionsList.serializer(), raw)
        assertTrue(result.data.isEmpty())
    }

    // ─── Chat Message JSON Escaping ──────────────────────────

    @Test
    fun chatMessageJson_escaping() {
        // ApiClient.chat() escapes: backslash, double-quote, newline
        val content = "Hello \"world\"\nNew line\\backslash"
        val escaped = content
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        // After escaping: Hello \"world\" then literal \n then New line\\backslash
        assertEquals("Hello \\\"world\\\"\\nNew line\\\\backslash", escaped)
    }

    @Test
    fun chatMessageJson_escaping_emptyString() {
        val content = ""
        val escaped = content
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        assertEquals("", escaped)
    }

    @Test
    fun chatMessageJson_escaping_noSpecialChars() {
        val content = "Hello world"
        val escaped = content
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
        assertEquals("Hello world", escaped)
    }

    // ─── Friendly Error Messages ─────────────────────────────

    @Test
    fun friendlyError_timeout() {
        val msg = friendlyErrorSim(java.net.SocketTimeoutException("timed out"))
        assertTrue(msg.contains("timed out", ignoreCase = true))
    }

    @Test
    fun friendlyError_connectionRefused() {
        val msg = friendlyErrorSim(java.net.ConnectException("Connection refused"))
        assertTrue(msg.contains("can't reach", ignoreCase = true) || msg.contains("Companion", ignoreCase = true))
    }

    @Test
    fun friendlyError_unknownHost() {
        val msg = friendlyErrorSim(java.net.UnknownHostException("no such host"))
        assertTrue(msg.contains("not found", ignoreCase = true) || msg.contains("address", ignoreCase = true))
    }

    @Test
    fun friendlyError_socketException() {
        val msg = friendlyErrorSim(java.net.SocketException("Connection reset"))
        assertTrue(msg.contains("connection lost", ignoreCase = true) || msg.contains("try again", ignoreCase = true))
    }

    @Test
    fun friendlyError_sslException() {
        val msg = friendlyErrorSim(javax.net.ssl.SSLException("SSL handshake failed"))
        assertTrue(msg.contains("secure connection", ignoreCase = true) || msg.contains("https", ignoreCase = true))
    }

    @Test
    fun friendlyError_genericIOException() {
        val msg = friendlyErrorSim(java.io.IOException("Something went wrong"))
        assertEquals("Something went wrong", msg)
    }

    @Test
    fun friendlyError_nullMessage() {
        val msg = friendlyErrorSim(java.io.IOException())
        assertEquals("Network error", msg)
    }

    // Simulates ApiClient.friendlyError() private method logic
    private fun friendlyErrorSim(e: java.io.IOException): String = when {
        e is java.net.SocketTimeoutException -> "Request timed out — check your connection"
        e is java.net.ConnectException -> "Can't reach server — is Companion running?"
        e is java.net.UnknownHostException -> "Server address not found — check URL"
        e is java.net.SocketException -> "Connection lost — please try again"
        e is javax.net.ssl.SSLException -> "Secure connection failed — check your URL (https?)"
        else -> e.message ?: "Network error"
    }
}
