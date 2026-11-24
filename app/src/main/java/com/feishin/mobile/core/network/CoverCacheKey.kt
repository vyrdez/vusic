package com.feishin.mobile.core.network

/**
 * Explicit cover-art cache key attached to Coil [coil.request.ImageRequest] tags so
 * [com.feishin.mobile.di.OfflineCoverInterceptor] can resolve offline covers by exact GlobalId.
 */
data class CoverCacheKey(val key: String)
