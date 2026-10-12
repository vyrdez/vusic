package com.feishin.mobile.data.repo

import android.content.Context
import android.net.Uri
import com.feishin.mobile.core.common.Result
import com.feishin.mobile.core.network.CoverArtUrlProvider
import com.feishin.mobile.core.network.SubsonicAuth
import com.feishin.mobile.data.auth.CredentialsStore
import com.feishin.mobile.data.mapper.toDomain
import com.feishin.mobile.data.remote.AuthInterceptor
import com.feishin.mobile.data.remote.SubsonicApi
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.AlbumDetail
import com.feishin.mobile.domain.model.Artist
import com.feishin.mobile.domain.model.ArtistDetail
import com.feishin.mobile.domain.model.Genre
import com.feishin.mobile.domain.model.Playlist
import com.feishin.mobile.domain.model.PlaylistDetail
import com.feishin.mobile.domain.model.SearchResults
import com.feishin.mobile.domain.model.StarredCollection
import com.feishin.mobile.domain.model.Track
import com.feishin.mobile.domain.repo.MusicDataRepository
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
) : MusicDataRepository {
    private fun base(): String {
        val c = creds.state.value ?: throw IllegalStateException("Not logged in")
        return SubsonicAuth.normalizeServerUrl(c.serverUrl)
    }

    private fun <T> unwrap(status: String, error: String?, get: () -> T): Result<T> =
        if (status == "ok") Result.Ok(get())
        else Result.Err(SubsonicAuth.userMessage(
            null,
            error,
            null,
        ).ifBlank { "Server error" })

    private suspend fun <T> call(block: suspend () -> Result<T>): Result<T> = withContext(Dispatchers.IO) {
        try {
            block()
        } catch (e: Exception) {
            Result.Err(SubsonicAuth.userMessage(null, null, e), e)
        }
    }

    override suspend fun ping(): Result<Unit> = call {
        val r = api.ping().response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun albumList(type: String, size: Int, offset: Int, genre: String?): Result<List<Album>> = call {
        val r = api.getAlbumList(type, size, offset, genre).response
        unwrap(r.status, r.error?.message) {
            val direct = r.albumList?.album.orEmpty()
            val fallback = r.albumList2?.album.orEmpty()
            (direct + fallback).distinctBy { it.id }.map { it.toDomain() }
        }
    }

    override suspend fun artists(): Result<List<Artist>> = call {
        val r = api.getArtists().response
        unwrap(r.status, r.error?.message) {
            r.artists?.index.orEmpty().flatMap { it.artist }.map { it.toDomain() }
        }
    }

    override suspend fun genres(): Result<List<Genre>> = call {
        val r = api.getGenres().response
        unwrap(r.status, r.error?.message) { r.genres?.genre.orEmpty().map { it.toDomain() } }
    }

    override suspend fun playlists(): Result<List<Playlist>> = call {
        val r = api.getPlaylists().response
        unwrap(r.status, r.error?.message) { r.playlists?.playlist.orEmpty().map { it.toDomain() } }
    }

    override suspend fun search(query: String): Result<SearchResults> = call {
        val r = api.search3(query).response
        unwrap(r.status, r.error?.message) {
            SearchResults(
                artists = r.searchResult3?.artist.orEmpty().map { it.toDomain() },
                albums = r.searchResult3?.album.orEmpty().map { it.toDomain() },
                tracks = r.searchResult3?.song.orEmpty().map { it.toDomain() },
            )
        }
    }

    override suspend fun album(id: String): Result<AlbumDetail> = call {
        val r = api.getAlbum(id).response
        unwrap(r.status, r.error?.message) { requireNotNull(r.album).toDomain() }
    }

    override suspend fun artist(id: String): Result<ArtistDetail> = call {
        val r = api.getArtist(id).response
        unwrap(r.status, r.error?.message) { requireNotNull(r.artist).toDomain() }
    }

    override suspend fun playlist(id: String): Result<PlaylistDetail> = call {
        val r = api.getPlaylist(id).response
        unwrap(r.status, r.error?.message) { requireNotNull(r.playlist).toDomain() }
    }

    override suspend fun starred(): Result<StarredCollection> = call {
        val r = try {
            api.getStarred2().response
        } catch (_: Exception) {
            api.getStarred().response
        }
        unwrap(r.status, r.error?.message) {
            StarredCollection(
                artists = ((r.starred2?.artist ?: r.starred?.artist).orEmpty()).map { it.toDomain() },
                albums = ((r.starred2?.album ?: r.starred?.album).orEmpty()).map { it.toDomain() },
                tracks = ((r.starred2?.song ?: r.starred?.song).orEmpty()).map { it.toDomain() },
            )
        }
    }

    override suspend fun starTrack(id: String, starred: Boolean): Result<Unit> = call {
        val r = if (starred) api.star(ids = listOf(id)).response else api.unstar(ids = listOf(id)).response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun starAlbum(id: String, starred: Boolean): Result<Unit> = call {
        val r = if (starred) api.star(albumIds = listOf(id)).response else api.unstar(albumIds = listOf(id)).response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun starArtist(id: String, starred: Boolean): Result<Unit> = call {
        val r = if (starred) api.star(artistIds = listOf(id)).response else api.unstar(artistIds = listOf(id)).response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun createPlaylist(name: String, trackIds: List<String>): Result<Unit> = call {
        val r = api.createPlaylist(name, trackIds.ifEmpty { null }).response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun addToPlaylist(playlistId: String, trackIds: List<String>): Result<Unit> = call {
        val r = api.updatePlaylist(playlistId, songIdToAdd = trackIds).response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun removeFromPlaylist(playlistId: String, indexes: List<Int>): Result<Unit> = call {
        val r = api.updatePlaylist(playlistId, songIndexToRemove = indexes).response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun deletePlaylist(id: String): Result<Unit> = call {
        val r = api.deletePlaylist(id).response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun scrobble(id: String, submission: Boolean): Result<Unit> = call {
        val r = api.scrobble(id, time = System.currentTimeMillis(), submission = submission).response
        unwrap(r.status, r.error?.message) { }
    }

    override suspend fun lyrics(artist: String, title: String): Result<String> = call {
        val r = api.getLyrics(artist, title).response
        unwrap(r.status, r.error?.message) { r.lyrics?.value ?: "" }
    }

    override fun streamUrl(track: Track, maxBitrate: Int, format: String): String {
        val b = Uri.parse(base() + "stream.view").buildUpon()
            .appendQueryParameter("id", track.id)
        if (maxBitrate > 0) b.appendQueryParameter("maxBitRate", maxBitrate.toString())
        if (format != "raw") b.appendQueryParameter("format", format)
        auth.authQuery().forEach { (k, v) -> b.appendQueryParameter(k, v) }
        return b.build().toString()
    }

    override fun coverArtUrl(coverArtId: String?, size: Int): String? {
        val c = creds.state.value ?: return null
        // Stable salt per cover so Coil's disk/memory cache can hit.
        return CoverArtUrlProvider.build(c.serverUrl, c.username, c.password, coverArtId, size)
    }

    override fun downloadUrl(track: Track): String {
        val b = Uri.parse(base() + "download.view").buildUpon()
            .appendQueryParameter("id", track.id)
        auth.authQuery().forEach { (k, v) -> b.appendQueryParameter(k, v) }
        return b.build().toString()
    }
}
