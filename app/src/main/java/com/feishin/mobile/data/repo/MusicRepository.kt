package com.feishin.mobile.data.repo

import android.content.Context
import android.net.Uri
import com.feishin.mobile.data.model.Album
import com.feishin.mobile.data.model.Artist
import com.feishin.mobile.data.model.Genre
import com.feishin.mobile.data.model.Playlist
import com.feishin.mobile.data.model.Song
import com.feishin.mobile.data.remote.AuthInterceptor
import com.feishin.mobile.data.remote.SubsonicApi
import com.feishin.mobile.data.auth.CredentialsStore
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.SubsonicAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class MusicRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: SubsonicApi,
    private val creds: CredentialsStore,
    private val auth: AuthInterceptor,
) {
    private fun base(): String {
        val c = creds.state.value ?: throw IllegalStateException("Not logged in")
        return SubsonicAuth.normalizeServerUrl(c.serverUrl)
    }

    private fun <T> unwrap(status: String, error: String?, get: () -> T): Result<T> =
        if (status == "ok") Result.Ok(get())
        else Result.Err(error ?: "Server error")

    private suspend fun <T> call(block: suspend () -> Result<T>): Result<T> = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: Exception) {
            Result.Err(e.message ?: "Network error", e)
        }
    }

    suspend fun ping(): Result<Unit> = call {
        val r = api.ping().response
        unwrap(r.status, r.error?.message) { }
    }

    suspend fun albumList(type: String, size: Int = 50, offset: Int = 0): Result<List<Album>> = call {
        val r = api.getAlbumList(type, size, offset).response
        unwrap(r.status, r.error?.message) {
            val direct = r.albumList?.album.orEmpty()
            val fallback = r.albumList2?.album.orEmpty()
            (direct + fallback).distinctBy { it.id }
        }
    }

    suspend fun artists(): Result<List<Artist>> = call {
        val r = api.getArtists().response
        unwrap(r.status, r.error?.message) { r.artists?.index.orEmpty().flatMap { it.artist } }
    }

    suspend fun genres(): Result<List<Genre>> = call {
        val r = api.getGenres().response
        unwrap(r.status, r.error?.message) { r.genres?.genre.orEmpty() }
    }

    suspend fun playlists(): Result<List<Playlist>> = call {
        val r = api.getPlaylists().response
        unwrap(r.status, r.error?.message) { r.playlists?.playlist.orEmpty() }
    }

    suspend fun search(query: String): Result<Triple<List<Artist>, List<Album>, List<Song>>> = call {
        val r = api.search3(query).response
        unwrap(r.status, r.error?.message) {
            Triple(
                r.searchResult3?.artist.orEmpty(),
                r.searchResult3?.album.orEmpty(),
                r.searchResult3?.song.orEmpty(),
            )
        }
    }

    suspend fun album(id: String) = call {
        val r = api.getAlbum(id).response
        unwrap(r.status, r.error?.message) { requireNotNull(r.album) }
    }

    suspend fun artist(id: String) = call {
        val r = api.getArtist(id).response
        unwrap(r.status, r.error?.message) { requireNotNull(r.artist) }
    }

    suspend fun playlist(id: String) = call {
        val r = api.getPlaylist(id).response
        unwrap(r.status, r.error?.message) { requireNotNull(r.playlist) }
    }

    suspend fun starred(): Result<Triple<List<Artist>, List<Album>, List<Song>>> = call {
        val r = try {
            api.getStarred2().response
        } catch (_: Exception) {
            api.getStarred().response
        }
        unwrap(r.status, r.error?.message) {
            Triple(
                (r.starred2?.artist ?: r.starred?.artist).orEmpty(),
                (r.starred2?.album ?: r.starred?.album).orEmpty(),
                (r.starred2?.song ?: r.starred?.song).orEmpty(),
            )
        }
    }

    suspend fun starSong(id: String, starred: Boolean) = call {
        val r = if (starred) api.star(ids = listOf(id)).response else api.unstar(ids = listOf(id)).response
        unwrap(r.status, r.error?.message) { }
    }

    suspend fun starAlbum(id: String, starred: Boolean) = call {
        val r = if (starred) api.star(albumIds = listOf(id)).response else api.unstar(albumIds = listOf(id)).response
        unwrap(r.status, r.error?.message) { }
    }

    suspend fun starArtist(id: String, starred: Boolean) = call {
        val r = if (starred) api.star(artistIds = listOf(id)).response else api.unstar(artistIds = listOf(id)).response
        unwrap(r.status, r.error?.message) { }
    }

    suspend fun createPlaylist(name: String, songIds: List<String> = emptyList()) = call {
        val r = api.createPlaylist(name, songIds.ifEmpty { null }).response
        unwrap(r.status, r.error?.message) { }
    }

    suspend fun addToPlaylist(playlistId: String, songIds: List<String>) = call {
        val r = api.updatePlaylist(playlistId, songIdToAdd = songIds).response
        unwrap(r.status, r.error?.message) { }
    }

    suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>) = call {
        val r = api.updatePlaylist(playlistId, songIndexToRemove = indexes).response
        unwrap(r.status, r.error?.message) { }
    }

    suspend fun deletePlaylist(id: String) = call {
        val r = api.deletePlaylist(id).response
        unwrap(r.status, r.error?.message) { }
    }

    suspend fun scrobble(id: String, submission: Boolean) = call {
        val r = api.scrobble(id, time = System.currentTimeMillis(), submission = submission).response
        unwrap(r.status, r.error?.message) { }
    }

    suspend fun lyrics(artist: String, title: String): Result<String> = call {
        val r = api.getLyrics(artist, title).response
        unwrap(r.status, r.error?.message) { r.lyrics?.value ?: "" }
    }

    fun streamUrl(song: Song, maxBitrate: Int = 0, format: String = "raw"): String {
        val b = Uri.parse(base() + "stream.view").buildUpon()
            .appendQueryParameter("id", song.id)
        if (maxBitrate > 0) b.appendQueryParameter("maxBitRate", maxBitrate.toString())
        if (format != "raw") b.appendQueryParameter("format", format)
        auth.authQuery().forEach { (k, v) -> b.appendQueryParameter(k, v) }
        return b.build().toString()
    }

    fun coverArtUrl(coverArtId: String?, size: Int = 512): String? {
        if (coverArtId.isNullOrBlank()) return null
        val b = Uri.parse(base() + "getCoverArt.view").buildUpon()
            .appendQueryParameter("id", coverArtId)
            .appendQueryParameter("size", size.toString())
        auth.authQuery().forEach { (k, v) -> b.appendQueryParameter(k, v) }
        return b.build().toString()
    }

    fun downloadUrl(song: Song): String {
        val b = Uri.parse(base() + "download.view").buildUpon()
            .appendQueryParameter("id", song.id)
        auth.authQuery().forEach { (k, v) -> b.appendQueryParameter(k, v) }
        return b.build().toString()
    }
}
