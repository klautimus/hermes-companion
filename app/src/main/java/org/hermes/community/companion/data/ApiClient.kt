package org.hermes.community.companion.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.ConnectException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

/**
 * HTTP client to Hermes Companion (port 8777 or via Cloudflare).
 * Auth: HTTP Basic (we strip Authorization from outbound; Companion adds its own bearer).
 */
class ApiClient(
    private val baseUrl: String,
    private val username: String,
    private val password: String,
) {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val authHeader: String
        get() = "Basic ${Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)}"

    /** Build a request with our Companion auth header. */
    private fun request(path: String, method: String = "GET", body: String? = null): Request =
        Request.Builder()
            .url("$baseUrl$path")
            .method(method, body?.toRequestBody(jsonMediaType))
            .header("Authorization", authHeader)
            .header("Accept", "application/json")
            .build()

    /** Blocking GET returning body text. */
    suspend fun get(path: String): String = withContext(Dispatchers.IO) {
        suspendCoroutine { cont ->
            client.newCall(request(path)).enqueue(reusableCont(cont))
        }
    }

    /** Blocking POST returning body text. */
    suspend fun post(path: String, bodyJson: String = "{}"): String = withContext(Dispatchers.IO) {
        suspendCoroutine { cont ->
            client.newCall(request(path, "POST", bodyJson)).enqueue(reusableCont(cont))
        }
    }

    /** Blocking PATCH returning body text. */
    suspend fun patch(path: String, bodyJson: String = "{}"): String = withContext(Dispatchers.IO) {
        suspendCoroutine { cont ->
            client.newCall(request(path, "PATCH", bodyJson)).enqueue(reusableCont(cont))
        }
    }

    /** Blocking DELETE returning body text. */
    suspend fun delete(path: String): String = withContext(Dispatchers.IO) {
        suspendCoroutine { cont ->
            client.newCall(request(path, "DELETE")).enqueue(reusableCont(cont))
        }
    }

    private fun friendlyError(e: java.io.IOException): String = when {
        e is java.net.SocketTimeoutException -> "Request timed out — check your connection"
        e is java.net.ConnectException -> "Can't reach server — is Companion running?"
        e is java.net.UnknownHostException -> "Server address not found — check URL"
        e is java.net.SocketException -> "Connection lost — please try again"
        e is javax.net.ssl.SSLException -> "Secure connection failed — check your URL (https?)"
        else -> e.message ?: "Network error"
    }

    private fun reusableCont(cont: kotlin.coroutines.Continuation<String>) = object : Callback {
        override fun onFailure(call: Call, e: java.io.IOException) {
            cont.resumeWithException(ApiException(0, friendlyError(e)))
        }
        override fun onResponse(call: Call, response: Response) {
            val body = response.body?.string() ?: ""
            if (response.isSuccessful) cont.resume(body)
            else cont.resumeWithException(ApiException(response.code, parseErr(body)))
        }
    }

    private fun parseErr(body: String): String = try {
        json.parseToJsonElement(body).jsonObject["error"]?.jsonObject
            ?.get("message")?.toString()?.removeSurrounding("\"") ?: "Request failed"
    } catch (_: Exception) {
        android.util.Log.w("ApiClient", "Failed to parse error body: ${body.take(200)}")
        "Request failed"
    }

    // ── Chat: non-streaming ────────────────────────────────

    /** Send chat completion, returns full response text. */
    suspend fun chat(
        messages: List<Map<String, String>>,
        sessionId: String? = null,
        attachmentIds: List<String>? = null,
    ): String = withContext(Dispatchers.IO) {
        // Build payload with kotlinx.serialization.json (proper escaping)
        val msgArray = kotlinx.serialization.json.JsonArray(
            messages.map { msg ->
                kotlinx.serialization.json.JsonObject(mapOf(
                    "role" to kotlinx.serialization.json.JsonPrimitive(msg["role"] ?: "user"),
                    "content" to kotlinx.serialization.json.JsonPrimitive(msg["content"] ?: ""),
                ))
            }
        )
        val payloadBuilder = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
            "model" to kotlinx.serialization.json.JsonPrimitive("hermes-agent"),
            "messages" to msgArray,
            "stream" to kotlinx.serialization.json.JsonPrimitive(false),
        )
        if (sessionId != null) {
            payloadBuilder["session_id"] = kotlinx.serialization.json.JsonPrimitive(sessionId)
        }
        if (attachmentIds != null && attachmentIds.isNotEmpty()) {
            payloadBuilder["attachment_ids"] = kotlinx.serialization.json.JsonArray(
                attachmentIds.map { kotlinx.serialization.json.JsonPrimitive(it) }
            )
        }
        val payloadObj = kotlinx.serialization.json.JsonObject(payloadBuilder)
        val payload = payloadObj.toString()

        suspendCoroutine { cont ->
            client.newCall(request("/v1/chat/completions", "POST", payload)).enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) {
                    cont.resumeWithException(ApiException(0, friendlyError(e)))
                }
                override fun onResponse(call: Call, response: Response) {
                    val body = response.body?.string() ?: ""
                    if (response.isSuccessful) {
                        try {
                            val obj = json.parseToJsonElement(body).jsonObject
                            val choices = obj["choices"]?.jsonArray
                            val message = choices?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
                            val content = message?.get("content")
                            cont.resume(content?.toString()?.removeSurrounding("\"") ?: "")
                        } catch (e: Exception) {
                            android.util.Log.e("ApiClient", "Failed to parse chat response", e)
                            cont.resumeWithException(ApiException(response.code, "Failed to parse response"))
                        }
                    } else {
                        cont.resumeWithException(ApiException(response.code, parseErr(body)))
                    }
                }
            })
        }
    }
    // ── Chat: SSE streaming ──────────────────────────────────

    /**
     * Stream a chat completion via SSE.
     *
     * Opens a connection to `/v1/chat/completions/stream` and reads the
     * response line-by-line, parsing SSE `data:` lines.  Each content
     * delta is delivered via [onChunk].  The callback runs on the
     * calling coroutine context (typically Dispatchers.Main via
     * viewModelScope).
     *
     * @param messages  chat history as list of {role, content} maps
     * @param sessionId optional session id for attachment tracking
     * @param attachmentIds optional attachment ids
     * @param onChunk   callback invoked for each content delta string
     * @return the full accumulated assistant response text
     */
    suspend fun chatStream(
        messages: List<Map<String, String>>,
        sessionId: String? = null,
        attachmentIds: List<String>? = null,
        onChunk: (String) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val msgArray = kotlinx.serialization.json.JsonArray(
            messages.map { msg ->
                kotlinx.serialization.json.JsonObject(mapOf(
                    "role" to kotlinx.serialization.json.JsonPrimitive(msg["role"] ?: "user"),
                    "content" to kotlinx.serialization.json.JsonPrimitive(msg["content"] ?: ""),
                ))
            }
        )
        val payloadBuilder = mutableMapOf<String, kotlinx.serialization.json.JsonElement>(
            "model" to kotlinx.serialization.json.JsonPrimitive("hermes-agent"),
            "messages" to msgArray,
            "stream" to kotlinx.serialization.json.JsonPrimitive(true),
        )
        if (sessionId != null) {
            payloadBuilder["session_id"] = kotlinx.serialization.json.JsonPrimitive(sessionId)
        }
        if (attachmentIds != null && attachmentIds.isNotEmpty()) {
            payloadBuilder["attachment_ids"] = kotlinx.serialization.json.JsonArray(
                attachmentIds.map { kotlinx.serialization.json.JsonPrimitive(it) }
            )
        }
        val payloadObj = kotlinx.serialization.json.JsonObject(payloadBuilder)
        val payload = payloadObj.toString()

        val req = Request.Builder()
            .url("$baseUrl/v1/chat/completions/stream")
            .header("Authorization", authHeader)
            .header("Accept", "text/event-stream")
            .method("POST", payload.toRequestBody(jsonMediaType))
            .build()

        // Use a dedicated client with a long read timeout for streaming
        val streamClient = client.newBuilder()
            .readTimeout(5, TimeUnit.MINUTES)
            .build()

        val response = streamClient.newCall(req).execute()
        if (!response.isSuccessful) {
            val errBody = response.body?.string() ?: ""
            throw ApiException(response.code, parseErr(errBody).ifEmpty { "Stream request failed" })
        }

        val body = response.body ?: throw ApiException(0, "Empty stream response")
        val source = body.source()
        val fullText = StringBuilder()

        while (!source.exhausted()) {
            val line = source.readUtf8Line() ?: break
            if (!line.startsWith("data: ")) continue
            val data = line.removePrefix("data: ").trim()
            if (data == "[DONE]") break
            if (data.isEmpty()) continue
            try {
                val obj = json.parseToJsonElement(data).jsonObject
                val choices = obj["choices"]?.jsonArray ?: continue
                for (choice in choices) {
                    val delta = choice.jsonObject["delta"]?.jsonObject ?: continue
                    val content = delta["content"]?.jsonPrimitive?.content ?: continue
                    if (content.isNotEmpty()) {
                        fullText.append(content)
                        // Deliver chunk on the main dispatcher
                        withContext(Dispatchers.Main) {
                            onChunk(content)
                        }
                    }
                    // Check for finish_reason
                    val finishReason = choice.jsonObject["finish_reason"]?.jsonPrimitive?.content
                    if (finishReason != null && finishReason != "null") break
                }
            } catch (_: Exception) {
                // Skip malformed SSE lines
            }
        }
        fullText.toString()
    }

    // ── Attachments: multipart upload ───────────────────────

    /** Upload a file via multipart POST /api/attachments. Returns JSON response. */
    suspend fun uploadAttachment(data: ByteArray, fileName: String, mimeType: String): String =
        withContext(Dispatchers.IO) {
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", fileName,
                    data.toRequestBody(mimeType.toMediaType()))
                .build()

            suspendCoroutine { cont ->
                val req = Request.Builder()
                    .url("$baseUrl/api/attachments")
                    .header("Authorization", authHeader)
                    .method("POST", body)
                    .build()
                client.newCall(req).enqueue(reusableCont(cont))
            }
        }
}

class ApiException(val code: Int, override val message: String) : Exception(message)

// ── Setup Token Redemption ──

data class RedeemResponse(
    val username: String,
    val password: String,
    val host: String,
    val port: Int,
    val board: String,
)

/**
 * Redeem a setup token for actual credentials.
 * This call is UNAUTHENTICATED — the endpoint has no auth requirement.
 * We make a raw OkHttp request to avoid ApiClient's automatic auth header.
 */
suspend fun redeemSetupToken(baseUrl: String, token: String): Result<RedeemResponse> = withContext(Dispatchers.IO) {
    runCatching {
        val url = baseUrl.removeSuffix("/") + "/api/setup/redeem"
        val jsonBody = """{"token":"$token"}"""
        val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("Accept", "application/json")
            .build()
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ApiException(response.code, "Setup redeem failed: ${response.code}")
            }
            val respBody = response.body?.string() ?: throw ApiException(0, "Empty response")
            val json = Json { ignoreUnknownKeys = true }
            val obj = json.parseToJsonElement(respBody).jsonObject
            RedeemResponse(
                username = obj["username"]?.jsonPrimitive?.content ?: "",
                password = obj["password"]?.jsonPrimitive?.content ?: "",
                host = obj["host"]?.jsonPrimitive?.content ?: "",
                port = obj["port"]?.jsonPrimitive?.content?.toIntOrNull() ?: 8777,
                board = obj["board"]?.jsonPrimitive?.content ?: "default",
            )
        }
    }
}

/**
 * Check server health WITHOUT authentication.
 * Used by the setup wizard to test connectivity before credentials are known.
 */
suspend fun checkServerHealth(baseUrl: String): CompanionHealth = withContext(Dispatchers.IO) {
    val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    val request = Request.Builder()
        .url("${baseUrl.removeSuffix("/")}/health")
        .header("Accept", "application/json")
        .build()

    client.newCall(request).execute().use { response ->
        if (!response.isSuccessful) {
            throw ApiException(response.code, "Server returned ${response.code}")
        }
        val body = response.body?.string() ?: throw ApiException(0, "Empty response")
        val json = Json { ignoreUnknownKeys = true }
        json.decodeFromString<CompanionHealth>(body)
    }
}

/**
 * Register the first user account on a fresh daemon.
 * This call is UNAUTHENTICATED.
 */
suspend fun registerUser(baseUrl: String, username: String, password: String): Boolean = withContext(Dispatchers.IO) {
    val url = baseUrl.removeSuffix("/") + "/api/setup/register"
    val jsonBody = """{"username":"$username","password":"$password"}"""
    val body = jsonBody.toRequestBody("application/json; charset=utf-8".toMediaType())
    val request = Request.Builder()
        .url(url)
        .post(body)
        .header("Accept", "application/json")
        .build()
    val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    client.newCall(request).execute().use { response ->
        if (response.code == 201) return@use true
        throw ApiException(response.code, "Registration failed (${response.code})")
    }
}
