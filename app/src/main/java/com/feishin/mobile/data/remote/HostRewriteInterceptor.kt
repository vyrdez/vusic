package com.feishin.mobile.data.remote

import com.feishin.mobile.data.store.CredentialsStore
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Rewrites every request to the currently configured server (scheme/host/port/path
 * prefix) so login/server changes apply without rebuilding Retrofit or restarting.
 */
@Singleton
class HostRewriteInterceptor @Inject constructor(
    private val creds: CredentialsStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val server = creds.state.value?.serverUrl?.trim()?.trimEnd('/') ?: return chain.proceed(request)
        val serverUrl = server.toHttpUrlOrNull() ?: return chain.proceed(request)
        val orig = request.url
        val mergedSegments = serverUrl.pathSegments.filter { it.isNotEmpty() } +
            orig.pathSegments.filter { it.isNotEmpty() }
        val out = orig.newBuilder()
            .scheme(serverUrl.scheme)
            .host(serverUrl.host)
            .port(serverUrl.port)
            .encodedPath("/" + mergedSegments.joinToString("/"))
            .build()
        return chain.proceed(request.newBuilder().url(out).build())
    }
}
