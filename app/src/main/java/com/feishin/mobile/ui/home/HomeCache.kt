package com.feishin.mobile.ui.home

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.homeCacheStore by preferencesDataStore(name = "feishin_home_cache")

/**
 * Stale-while-revalidate cache for Home: an in-memory map serves repeated tab
 * switches instantly, and (for the disk implementation) a small snapshot
 * survives process death so a cold start can paint the last known Home before
 * the network answers.
 */
interface HomeCache {
    fun get(genre: String): HomeUi?
    fun put(genre: String, ui: HomeUi)
    suspend fun loadDisk(genre: String): HomeUi?
    suspend fun saveDisk(genre: String, ui: HomeUi)
}

/** Plain in-memory cache; used by tests and as the fallback implementation. */
class InMemoryHomeCache : HomeCache {
    private val memory = HashMap<String, HomeUi>()
    override fun get(genre: String): HomeUi? = memory[genre]
    override fun put(genre: String, ui: HomeUi) {
        memory[genre] = ui
    }
    override suspend fun loadDisk(genre: String): HomeUi? = memory[genre]
    override suspend fun saveDisk(genre: String, ui: HomeUi) {
        memory[genre] = ui
    }
}

@Singleton
class DiskHomeCache @Inject constructor(
    @ApplicationContext private val context: Context,
) : HomeCache {
    private val json = Json { ignoreUnknownKeys = true }
    private val key: Preferences.Key<String> = stringPreferencesKey("home_snapshot_v1")
    private val memory = HashMap<String, HomeUi>()

    override fun get(genre: String): HomeUi? = memory[genre]

    override fun put(genre: String, ui: HomeUi) {
        memory[genre] = ui
    }

    override suspend fun loadDisk(genre: String): HomeUi? = withContext(Dispatchers.IO) {
        memory[genre] ?: runCatching {
            val raw = context.homeCacheStore.data.first()[key] ?: return@runCatching null
            val snap = json.decodeFromString(HomeSnapshot.serializer(), raw)
            if (snap.genre != genre) return@runCatching null
            snap.toUi().also { memory[genre] = it }
        }.getOrNull()
    }

    override suspend fun saveDisk(genre: String, ui: HomeUi) {
        withContext(Dispatchers.IO) {
            put(genre, ui)
            runCatching {
                val snap = HomeSnapshot.from(genre, ui)
                context.homeCacheStore.edit { it[key] = json.encodeToString(HomeSnapshot.serializer(), snap) }
            }
        }
    }
}

/** Serialized subset of HomeUi worth keeping on disk (small; no signed URLs). */
@Serializable
internal data class HomeSnapshot(
    val genre: String,
    val albums: List<AlbumSnap> = emptyList(),
    val songs: List<TrackSnap> = emptyList(),
) {
    @Serializable
    internal data class AlbumSnap(
        val id: String, val name: String, val artist: String, val artistId: String? = null,
        val coverArt: String? = null, val songCount: Int = 0, val durationSec: Int = 0,
        val playCount: Long = 0, val year: Int? = null, val genre: String? = null, val starred: Boolean = false,
    )

    @Serializable
    internal data class TrackSnap(
        val id: String, val title: String, val album: String, val albumId: String? = null,
        val artist: String, val artistId: String? = null, val coverArt: String? = null,
        val durationSec: Int = 0, val suffix: String? = null,
    )

    fun toUi(): HomeUi {
        val restored = albums.map {
            Album(it.id, it.name, it.artist, it.artistId, it.coverArt, it.songCount, it.durationSec, it.playCount, it.year, it.genre, it.starred)
        }
        return HomeUi(
            loading = true,
            selectedGenre = genre,
            speedDial = restored.take(9).map {
                SpeedTile("album:${it.id}", it.name, it.artist, it.coverArt, target = SpeedTarget.AlbumTarget(it))
            },
            recent = restored,
            quickPicks = songs.map {
                Track(it.id, it.title, it.album, it.albumId, it.artist, it.artistId, it.coverArt, it.durationSec, null, null, null, it.suffix, false)
            },
        )
    }

    companion object {
        fun from(genre: String, ui: HomeUi) = HomeSnapshot(
            genre = genre,
            albums = ui.recent.take(20).map {
                AlbumSnap(it.id, it.name, it.artist, it.artistId, it.coverArt, it.songCount, it.durationSec, it.playCount, it.year, it.genre, it.starred)
            },
            songs = ui.quickPicks.take(24).map {
                TrackSnap(it.id, it.title, it.album, it.albumId, it.artist, it.artistId, it.coverArt, it.durationSec, it.suffix)
            },
        )
    }
}
