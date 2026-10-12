package com.feishin.mobile.data.remote

import com.feishin.mobile.data.store.CredentialsStore
import com.feishin.mobile.util.md5Hex
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Response

@Singleton
class AuthInterceptor @Inject constructor(
    private val credentialsStore: CredentialsStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val creds = credentialsStore.state.value
        val original = chain.request()
        if (creds == null) return chain.proceed(original)
        val salt = UUID.randomUUID().toString().replace("-", "")
        val token = md5Hex(creds.password + salt)
        val url = original.url.newBuilder()
            .addQueryParameter("u", creds.username)
            .addQueryParameter("t", token)
            .addQueryParameter("s", salt)
            .addQueryParameter("v", "1.16.1")
            .addQueryParameter("c", "FeishinMobile")
            .addQueryParameter("f", "json")
            .build()
        return chain.proceed(original.newBuilder().url(url).build())
    }

    /** Auth params for URLs built outside Retrofit (stream / cover art / download). */
    fun authQuery(): Map<String, String> {
        val creds = credentialsStore.state.value ?: return mapOf(
            "v" to "1.16.1", "c" to "FeishinMobile", "f" to "json",
        )
        val salt = UUID.randomUUID().toString().replace("-", "")
        return mapOf(
            "u" to creds.username,
            "t" to md5Hex(creds.password + salt),
            "s" to salt,
            "v" to "1.16.1",
            "c" to "FeishinMobile",
            "f" to "json",
        )
    }
}
