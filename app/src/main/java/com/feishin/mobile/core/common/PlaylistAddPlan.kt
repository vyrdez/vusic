package com.feishin.mobile.core.common

import com.feishin.mobile.domain.model.Track

sealed interface PlaylistAddResolution {
    object NoOp : PlaylistAddResolution

    data class AddImmediately(
        val songsToAdd: List<Track>,
        val toastMessage: String,
    ) : PlaylistAddResolution

    data class AddFilteredImmediately(
        val songsToAdd: List<Track>,
        val skippedCount: Int,
        val toastMessage: String,
    ) : PlaylistAddResolution

    data class RequireConfirmation(
        val songsToAdd: List<Track>,
        val dialogMessage: String,
    ) : PlaylistAddResolution
}

object PlaylistAddPlanner {
    fun plan(
        candidateSongs: List<Track>,
        targetPlaylistName: String,
        existingSongIds: Set<String>,
    ): PlaylistAddResolution {
        val seen = mutableSetOf<String>()
        val deduplicated = candidateSongs.filter { seen.add(it.id) }
        if (deduplicated.isEmpty()) return PlaylistAddResolution.NoOp

        val newSongs = deduplicated.filter { it.id !in existingSongIds }
        val duplicateCount = deduplicated.size - newSongs.size

        return if (deduplicated.size == 1) {
            val song = deduplicated.first()
            if (duplicateCount == 0) {
                PlaylistAddResolution.AddImmediately(
                    songsToAdd = newSongs,
                    toastMessage = "Added to $targetPlaylistName",
                )
            } else {
                PlaylistAddResolution.RequireConfirmation(
                    songsToAdd = deduplicated,
                    dialogMessage = "\"${song.title}\" is already in \"$targetPlaylistName\". Add anyway?",
                )
            }
        } else {
            if (duplicateCount == 0) {
                PlaylistAddResolution.AddImmediately(
                    songsToAdd = newSongs,
                    toastMessage = "Added ${newSongs.size} songs to $targetPlaylistName",
                )
            } else if (newSongs.isNotEmpty()) {
                PlaylistAddResolution.AddFilteredImmediately(
                    songsToAdd = newSongs,
                    skippedCount = duplicateCount,
                    toastMessage = "Added ${newSongs.size} songs to $targetPlaylistName ($duplicateCount already there)",
                )
            } else {
                PlaylistAddResolution.RequireConfirmation(
                    songsToAdd = deduplicated,
                    dialogMessage = "All $duplicateCount songs are already in \"$targetPlaylistName\". Add anyway?",
                )
            }
        }
    }
}
