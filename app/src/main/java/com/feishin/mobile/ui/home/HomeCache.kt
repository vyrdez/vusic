package com.feishin.mobile.ui.home

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.Album
import com.feishin.mobile.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
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
class DiskHomeCache private constructor(
    private val storeProvider: () -> DataStore<Preferences>,
    private val settings: SettingsStore?,
) : HomeCache {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        settings: SettingsStore,
    ) : this(
        storeProvider = { context.homeCacheStore },
        settings = settings,
    )

    constructor(context: Context) : this(
        storeProvider = { context.homeCacheStore },
        settings = null,
    )

    internal constructor(
        store: DataStore<Preferences>,
        settings: SettingsStore? = null,
    ) : this(
        storeProvider = { store },
        settings = settings,
    )

    private val store: DataStore<Preferences> by lazy { storeProvider() }
    private val json = Json { ignoreUnknownKeys = true }
    private val memory = ConcurrentHashMap<String, HomeUi>()

    private fun syncFilter(): String =
        normalizeFilter(settings?.latestSettings?.librarySourceFilter)

    private suspend fun currentFilter(): String =
        normalizeFilter(settings?.settingsNow()?.librarySourceFilter)

    override fun get(genre: String): HomeUi? = memory[memoryKey(syncFilter(), genre)]

    override fun put(genre: String, ui: HomeUi) {
        memory[memoryKey(syncFilter(), genre)] = ui
    }

    override suspend fun loadDisk(genre: String): HomeUi? = withContext(Dispatchers.IO) {
        val filter = currentFilter()
        val memKey = memoryKey(filter, genre)
        memory[memKey] ?: runCatching {
            val raw = store.data.first()[snapshotKeyFor(filter)] ?: return@runCatching null
            val snap = json.decodeFromString(HomeSnapshot.serializer(), raw)
            if (snap.genre != genre) return@runCatching null
            snap.toUi().also { memory[memKey] = it }
        }.getOrNull()
    }

    override suspend fun saveDisk(genre: String, ui: HomeUi) {
        withContext(Dispatchers.IO) {
            val filter = currentFilter()
            memory[memoryKey(filter, genre)] = ui
            runCatching {
                val snap = HomeSnapshot.from(genre, ui)
                store.edit { it[snapshotKeyFor(filter)] = json.encodeToString(HomeSnapshot.serializer(), snap) }
            }
        }
    }

    companion object {
        internal fun normalizeFilter(raw: String?): String {
            val trimmed = raw?.trim().orEmpty()
            return if (trimmed.isEmpty() || trimmed == "DEVICE") "ALL" else trimmed
        }

        internal fun snapshotKeyFor(filter: String): Preferences.Key<String> =
            stringPreferencesKey("home_snapshot_v2_${normalizeFilter(filter)}")

        internal fun memoryKey(filter: String, genre: String): String =
            "${normalizeFilter(filter)}:$genre"
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
