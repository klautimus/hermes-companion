package org.hermes.community.companion

import android.app.Application
import android.util.Base64
import coil.ImageLoader
import coil.ImageLoaderFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Application class that provides Coil with auth-enabled ImageLoader.
 * Attachment URLs require Basic Auth, so Coil's default loader won't work.
 */
class CompanionApp : Application(), ImageLoaderFactory {
    private var authHeader: String = ""
    private var baseUrl: String = ""

    override fun newImageLoader(): ImageLoader {
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val req = chain.request().newBuilder().apply {
                    if (authHeader.isNotBlank()) {
                        header("Authorization", authHeader)
                    }
                }.build()
                chain.proceed(req)
            }
            .build()

        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            .build()
    }

    fun setAuth(username: String, password: String, url: String) {
        authHeader = "Basic ${Base64.encodeToString("$username:$password".toByteArray(), Base64.NO_WRAP)}"
        baseUrl = url
    }
}
