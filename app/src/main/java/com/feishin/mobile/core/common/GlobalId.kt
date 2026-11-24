package com.feishin.mobile.core.common

/**
 * Colon-safe `{profileId}:{itemId}` global identifier helpers for federated multi-backend routing.
 */
object GlobalId {
    val PROFILE_ID_REGEX = Regex("^[A-Za-z0-9_-]+$")
    val RESERVED_PREFIXES = setOf("local:", "local_album:", "local_artist:", "local_pl:")
    val VIRTUAL_IDS = setOf("__favorites__", "__cached__")

    fun isDeviceLocal(id: String): Boolean =
        id.startsWith("local:") || id.startsWith("local_album:") || id.startsWith("local_artist:")

    fun isReserved(id: String): Boolean =
        id in VIRTUAL_IDS || RESERVED_PREFIXES.any { id.startsWith(it) } || id.startsWith("col_")

    fun format(profileId: String, rawId: String): String {
        require(PROFILE_ID_REGEX.matches(profileId)) { "Invalid profileId: $profileId" }
        if (isReserved(rawId)) return rawId
        return if (rawId.startsWith("$profileId:")) rawId else "$profileId:$rawId"
    }

    /** Returns `(profileId, rawItemId)`, or `null` for reserved/virtual/invalid/legacy-unprefixed IDs. */
    fun parse(globalId: String): Pair<String, String>? {
        if (isReserved(globalId)) return null
        val idx = globalId.indexOf(':')
        if (idx <= 0 || idx == globalId.lastIndex) return null
        val prefix = globalId.substring(0, idx)
        if (!PROFILE_ID_REGEX.matches(prefix)) return null
        return prefix to globalId.substring(idx + 1)
    }

    fun profileIdOrNull(globalId: String): String? = parse(globalId)?.first

    fun rawId(globalId: String): String = parse(globalId)?.second ?: globalId
}
