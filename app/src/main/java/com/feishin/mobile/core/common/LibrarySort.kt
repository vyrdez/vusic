package com.feishin.mobile.core.common

/**
 * A persisted Library sort: which option, and which direction.
 * Stored per tab in DataStore as `"optionId|asc"`.
 */
data class SortChoice(val option: String, val ascending: Boolean) {
    fun serialize(): String = "$option|${if (ascending) "asc" else "desc"}"
    fun migrate(): SortChoice = if (option == "name") copy(option = "title") else this

    companion object {
        fun parse(raw: String?, fallbackOption: String): SortChoice {
            val parts = (raw ?: "").split("|")
            val option = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: fallbackOption
            val ascending = parts.getOrNull(1) != "desc"
            return SortChoice(option, ascending)
        }
    }
}

/**
 * Readable sort options per Library tab. Only options backed by real model
 * fields are offered: there is no server-provided added/updated date on
 * tracks, albums or playlists, so "recently added/updated" sorts are
 * deliberately absent (year covers "newest" for albums and songs).
 */
object LibrarySorts {
    data class Option(val id: String, val label: String)

    fun options(tab: String): List<Option> = when (tab) {
        "ALBUMS" -> listOf(
            Option("name", "Name"),
            Option("artist", "Artist"),
            Option("year", "Year"),
            Option("plays", "Most played"),
        )
        "ARTISTS" -> listOf(
            Option("name", "Name"),
            Option("albums", "Most albums"),
        )
        "SONGS" -> listOf(
            Option("title", "Title"),
            Option("artist", "Artist"),
            Option("duration", "Duration"),
            Option("year", "Year"),
        )
        "PLAYLISTS" -> listOf(
            Option("name", "Name"),
            Option("songs", "Most songs"),
        )
        "DOWNLOADS" -> listOf(
            Option("title", "Title"),
            Option("artist", "Artist"),
            Option("date_added", "Date added"),
            Option("duration", "Duration"),
        )
        else -> listOf(Option("name", "Name"))
    }

    fun default(tab: String): SortChoice = when (tab) {
        "ALBUMS", "ARTISTS", "PLAYLISTS" -> SortChoice("name", true)
        "SONGS", "DOWNLOADS" -> SortChoice("title", true)
        else -> SortChoice("name", true)
    }

    fun migrate(choice: SortChoice): SortChoice =
        if (choice.option == "name") choice.copy(option = "title") else choice

    fun migrate(tab: String, choice: SortChoice): SortChoice =
        if (tab == "DOWNLOADS" && choice.option == "name") choice.copy(option = "title") else choice

    fun label(tab: String, choice: SortChoice): String {
        val effectiveChoice = migrate(tab, choice)
        val base = options(tab).firstOrNull { it.id == effectiveChoice.option }?.label ?: "Name"
        val dir = if (effectiveChoice.ascending) "↑" else "↓"
        return "$base $dir"
    }
}
