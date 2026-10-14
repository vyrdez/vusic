package com.feishin.mobile.core.common

/**
 * Pure LRU bookkeeping for the stream cache: tracks cached keys with their last
 * touch time and size, and computes what to drop when the limit shrinks.
 *
 * Kept free of Media3 types so the live-resize behaviour is unit-testable.
 */
class LruSizer {
    private data class Entry(val key: String, val length: Long, val touch: Long)

    private val byKey = HashMap<String, Entry>()
    private val ordered = java.util.TreeSet<Entry>(
        compareBy({ it.touch }, { it.key }),
    )

    val size: Long get() = byKey.values.sumOf { it.length }

    val count: Int get() = byKey.size

    fun put(key: String, length: Long, touch: Long) {
        byKey.remove(key)?.let { ordered.remove(it) }
        val e = Entry(key, length, touch)
        byKey[key] = e
        ordered.add(e)
    }

    fun remove(key: String) {
        byKey.remove(key)?.let { ordered.remove(it) }
    }

    fun contains(key: String): Boolean = byKey.containsKey(key)

    /**
     * Applies a new maximum and returns the keys to evict, least-recently-touched
     * first. Entries are removed from the sizer, so the caller only has to delete
     * the reported keys from the real cache.
     */
    fun evictTo(maxBytes: Long): List<String> {
        if (maxBytes <= 0) return emptyList()
        val out = ArrayList<String>()
        while (size > maxBytes) {
            val oldest = ordered.firstOrNull() ?: break
            ordered.remove(oldest)
            byKey.remove(oldest.key)
            out += oldest.key
        }
        return out
    }
}
