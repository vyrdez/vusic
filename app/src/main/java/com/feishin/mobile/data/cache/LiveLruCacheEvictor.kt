@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.feishin.mobile.data.cache

import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.SimpleCache
import com.feishin.mobile.core.common.LruSizer

/**
 * LRU evictor whose maximum is re-read from settings on demand, so shrinking the
 * limit in Settings takes effect **immediately** (the excess is trimmed on the
 * spot) instead of "next launch".
 *
 * Mirrors Media3's `LeastRecentlyUsedCacheEvictor` ordering (oldest touch first)
 * but with a mutable ceiling, since that class has no setter. The ordering logic
 * lives in [LruSizer] so it is unit-testable without Media3.
 */
class LiveLruCacheEvictor(
    private val maxBytesProvider: () -> Long,
) : CacheEvictor {

    private var cache: SimpleCache? = null
    private val sizer = LruSizer()

    fun maxBytes(): Long = maxBytesProvider().coerceAtLeast(0L)

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() {
        val c = cache ?: return
        for (key in c.keys) {
            c.getCachedSpans(key).forEach { addSpan(it) }
        }
    }

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        this.cache = cache as? SimpleCache
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        this.cache = cache as? SimpleCache
        addSpan(span)
        evictToLimit()
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        if (span.key.isNotEmpty()) sizer.remove(span.key)
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        addSpan(newSpan)
        evictToLimit()
    }

    /** Re-applies the current limit (called right after the setting changes). */
    fun trimNow() = evictToLimit()

    /** Cached bytes accounted for (usage readouts). */
    fun usedBytes(): Long = sizer.size

    private fun addSpan(span: CacheSpan) {
        if (!span.isCached) return
        sizer.put(span.key, span.length, span.lastTouchTimestamp)
    }

    private fun evictToLimit() {
        val c = cache ?: return
        val max = maxBytes()
        if (max <= 0) return
        for (key in sizer.evictTo(max)) {
            runCatching { c.removeResource(key) }
        }
    }
}
