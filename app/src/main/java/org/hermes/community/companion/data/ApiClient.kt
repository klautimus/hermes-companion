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
    suspend fun chat(messages: List<Map<String, String>>): String = withContext(Dispatchers.IO) {
        // Build payload with kotlinx.serialization.json (proper escaping)
        val msgArray = kotlinx.serialization.json.JsonArray(
            messages.map { msg ->
                kotlinx.serialization.json.JsonObject(mapOf(
                    "role" to kotlinx.serialization.json.JsonPrimitive(msg["role"] ?: "user"),
                    "content" to kotlinx.serialization.json.JsonPrimitive(msg["content"] ?: ""),
                ))
            }
        )
        val payloadObj = kotlinx.serialization.json.JsonObject(mapOf(
            "model" to kotlinx.serialization.json.JsonPrimitive("hermes-agent"),
            "messages" to msgArray,
            "stream" to kotlinx.serialization.json.JsonPrimitive(false),
        ))
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
