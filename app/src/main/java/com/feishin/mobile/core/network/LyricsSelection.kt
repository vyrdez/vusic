package com.feishin.mobile.core.network

/**
 * Pure selection among candidate [Lyrics] for one song.
 *
 * Order:
 *   1. Synced in the app/device language
 *   2. Any synced
 *   3. Unsynced in the app/device language
 *   4. First available
 *
 * A null/blank [deviceLang] or a candidate with lang "" / "xxx" (unknown)
 * is treated as matching any language.
 */
object LyricsSelection {
    fun choose(candidates: List<Lyrics>, deviceLang: String? = null): Lyrics {
        val lang = deviceLang?.lowercase()?.takeIf { it.isNotBlank() }
        val langMatch: (Lyrics) -> Boolean = { l ->
            val lLang = l.lang.lowercase()
            lang == null || lLang.isBlank() || lLang == "xxx" || lLang == lang
        }

        val syncedPreferred = candidates.firstOrNull { it.isSynced && langMatch(it) }
        if (syncedPreferred != null) return syncedPreferred

        val anySynced = candidates.firstOrNull { it.isSynced }
        if (anySynced != null) return anySynced

        val plainPreferred = candidates.firstOrNull { it.isPlain && langMatch(it) }
        if (plainPreferred != null) return plainPreferred

        val anyPlain = candidates.firstOrNull { it.isPlain }
        if (anyPlain != null) return anyPlain

        return candidates.firstOrNull() ?: Lyrics(emptyList(), 0, LyricsKind.NONE)
    }
}
