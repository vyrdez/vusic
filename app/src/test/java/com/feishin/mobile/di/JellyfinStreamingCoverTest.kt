package com.feishin.mobile.di

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import coil.request.ImageRequest
import com.feishin.mobile.core.common.StableCacheKey
import com.feishin.mobile.core.network.CoverCacheKey
import com.feishin.mobile.core.network.ProfileIdTag
import com.feishin.mobile.data.auth.CredentialsStore
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.cache.CacheBytes
import com.feishin.mobile.data.cache.CachedTrackStore
import com.feishin.mobile.data.db.CachedCoverDao
import com.feishin.mobile.data.db.CachedCoverEntity
import com.feishin.mobile.data.db.CachedTrackDao
import com.feishin.mobile.data.db.CachedTrackEntity
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.domain.model.Track
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JellyfinStreamingCoverTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun stubContext(): Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private val stubLifecycle = object : Lifecycle() {
        override val currentState: State = State.RESUMED
        override fun addObserver(observer: LifecycleObserver) = Unit
        override fun removeObserver(observer: LifecycleObserver) = Unit
    }

    private fun newProfileStore(scope: CoroutineScope, profiles: List<ServerProfile>): ProfileStore {
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(tmp.root, "settings_${UUID.randomUUID()}.preferences_pb") },
            ),
        )
        val store = ProfileStore(
            prefs = InMemorySharedPreferences(),
            legacyCredentials = CredentialsStore(stubContext()),
            settings = settings,
        )
        store.saveAll(profiles)
        return store
    }

    private fun jellyfinProfile(id: String, url: String = "https://jf.example.com:8096") = ServerProfile(
        id = id,
        name = "Jellyfin $id",
        type = BackendType.JELLYFIN,
        url = url,
        userId = "user-$id",
        credentials = ProfileCredentials.Jellyfin(
            username = "user-$id",
            accessToken = "tok-$id",
            serverId = "srv-1",
            deviceId = "dev-1",
        ),
        priority = if (id == "jf1") 0 else 1,
    )

    private fun subsonicProfile(id: String, url: String = "https://sub.example.com:4533") = ServerProfile(
        id = id,
        name = "Subsonic $id",
        type = BackendType.NAVIDROME,
        url = url,
        userId = "alice",
        credentials = ProfileCredentials.Subsonic("alice", "pass"),
        priority = 2,
    )

    @Test
    fun offlineCoverInterceptor_prefersCoverCacheKeyThenProfileIdTagThenFindByHost() = runTest {
        val jf1 = jellyfinProfile("jf1")
        val jf2 = jellyfinProfile("jf2")
        val sub1 = subsonicProfile("p2")
        val store = newProfileStore(backgroundScope, listOf(jf1, jf2, sub1))
        val interceptor = OfflineCoverInterceptor(FakeCachedCoverDao(), store)

        val jfUrl = "https://jf.example.com:8096/Items/alb-9/Images/Primary?maxWidth=512&quality=90"
        val subUrl = "https://sub.example.com:4533/rest/getCoverArt.view?id=cov-5&size=512"

        // 1. CoverCacheKey wins over ProfileIdTag and host lookup.
        val withBothTags = ImageRequest.Builder(stubContext())
            .data(jfUrl)
            .lifecycle(stubLifecycle)
            .tag(CoverCacheKey::class.java, CoverCacheKey("jf2:custom-cover"))
            .tag(ProfileIdTag::class.java, ProfileIdTag("jf1"))
            .build()
        assertEquals("jf2:custom-cover", interceptor.resolveKey(withBothTags, jfUrl))

        // 2. Without CoverCacheKey, ProfileIdTag isolates same-host Jellyfin profiles before findByHost.
        val withProfileTag = ImageRequest.Builder(stubContext())
            .data(jfUrl)
            .lifecycle(stubLifecycle)
            .tag(ProfileIdTag::class.java, ProfileIdTag("jf2"))
            .build()
        assertEquals("jf2:alb-9", interceptor.resolveKey(withProfileTag, jfUrl))

        val subWithProfileTag = ImageRequest.Builder(stubContext())
            .data(subUrl)
            .lifecycle(stubLifecycle)
            .tag(ProfileIdTag::class.java, ProfileIdTag("p3"))
            .build()
        assertEquals("p3:cov-5", interceptor.resolveKey(subWithProfileTag, subUrl))

        // 3. Without tags, falls back to ProfileStore.findByHost for /Items/{id}/Images/Primary and getCoverArt.
        val untaggedJf = ImageRequest.Builder(stubContext())
            .data(jfUrl)
            .lifecycle(stubLifecycle)
            .build()
        assertEquals("jf1:alb-9", interceptor.resolveKey(untaggedJf, jfUrl))

        val untaggedSub = ImageRequest.Builder(stubContext())
            .data(subUrl)
            .lifecycle(stubLifecycle)
            .build()
        assertEquals("p2:cov-5", interceptor.resolveKey(untaggedSub, subUrl))
    }

    @Test
    fun stableCacheKey_handlesCaseInsensitiveJellyfinParamsAndExtractsProfileId() {
        val universal =
            "https://jf.example.com:8096/Audio/t-1/universal?UserId=u1&DeviceId=d1&MaxStreamingBitrate=192000&AudioCodec=opus&Container=opus&TranscodingProtocol=http&TranscodingContainer=opus&EnableRedirection=false&EnableRemoteMedia=false"
        val key = StableCacheKey.keyFor(universal, "jf2")
        assertEquals(
            "jf2:https://jf.example.com:8096/Audio/t-1/universal?AudioCodec=opus&Container=opus&MaxStreamingBitrate=192000",
            key,
        )
        assertEquals("jf2", StableCacheKey.extractProfileId(key))
        assertNull(StableCacheKey.extractProfileId("https://jf.example.com:8096/Audio/t-1/stream?static=true"))

        val staticStream = "https://jf.example.com:8096/Audio/t-1/stream?Static=true&MediaSourceId=ms1"
        assertEquals(
            "jf1:https://jf.example.com:8096/Audio/t-1/stream?MediaSourceId=ms1&Static=true",
            StableCacheKey.keyFor(staticStream, "jf1"),
        )
    }

    @Test
    fun cachedTrackStore_cachedQualityResolvesJellyfinUniversalAndStaticKeys() = runTest {
        val dao = FakeCachedTrackDao()
        val store = CachedTrackStore(dao, AlwaysCachedBytes)

        val t1 = Track(
            id = "jf2:t1", title = "T1", album = "A", albumId = "jf2:al1",
            artist = "Ar", artistId = "jf2:ar1", coverArt = null,
            durationSec = 180, trackNo = 1, year = 2024, genre = "Jazz",
            suffix = "mp3", starred = false,
        )
        val t2 = t1.copy(id = "jf2:t2", suffix = "flac")

        val universalKey = StableCacheKey.keyFor(
            "https://jf.example.com:8096/Audio/t1/universal?MaxStreamingBitrate=192000&AudioCodec=mp3&Container=mp3&TranscodingProtocol=http",
            "jf2",
        )
        val staticKey = StableCacheKey.keyFor(
            "https://jf.example.com:8096/Audio/t2/stream?static=true",
            "jf2",
        )

        store.recordCached(t1, universalKey, 1024L)
        store.recordCached(t2, staticKey, 2048L)

        assertEquals(192 to "mp3", store.cachedQuality("jf2:t1"))
        assertEquals(0 to "raw", store.cachedQuality("jf2:t2"))
    }

    private object AlwaysCachedBytes : CacheBytes {
        override fun isCached(cacheKey: String, sizeBytes: Long): Boolean = true
        override fun removeResource(cacheKey: String) = Unit
    }

    private class FakeCachedCoverDao : CachedCoverDao {
        private val paths = linkedMapOf<String, String>()
        override suspend fun path(id: String): String? = paths[id]
        override suspend fun upsert(cover: CachedCoverEntity) {
            paths[cover.coverArtId] = cover.filePath
        }
        override suspend fun allSizes(): List<Long> = emptyList()
        override suspend fun delete(id: String) {
            paths.remove(id)
        }
        override suspend fun deleteByPrefix(prefix: String) {
            paths.keys.filter { it.startsWith(prefix) }.forEach { paths.remove(it) }
        }
        override suspend fun clear() {
            paths.clear()
        }
    }

    private class FakeCachedTrackDao : CachedTrackDao {
        private val rows = linkedMapOf<String, CachedTrackEntity>()
        private val flow = MutableStateFlow<List<CachedTrackEntity>>(emptyList())
        override fun observeAll(): Flow<List<CachedTrackEntity>> = flow
        override suspend fun all(): List<CachedTrackEntity> = rows.values.toList()
        override suspend fun byProfile(profileId: String): List<CachedTrackEntity> =
            rows.values.filter { it.profileId == profileId }
        override suspend fun byId(id: String): CachedTrackEntity? = rows[id]
        override suspend fun upsert(track: CachedTrackEntity) {
            rows[track.songId] = track
            flow.value = rows.values.toList()
        }
        override suspend fun touchPlayed(id: String, now: Long) = Unit
        override suspend fun delete(id: String) {
            rows.remove(id)
            flow.value = rows.values.toList()
        }
        override suspend fun deleteByProfile(profileId: String) {
            rows.entries.removeAll { it.value.profileId == profileId }
            flow.value = rows.values.toList()
        }
        override suspend fun clear() {
            rows.clear()
            flow.value = emptyList()
        }
        override suspend fun count(): Int = rows.size
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val data = linkedMapOf<String, Any?>()
        override fun getAll(): MutableMap<String, *> = synchronized(data) { data.toMutableMap() }
        override fun getString(key: String?, defValue: String?): String? =
            synchronized(data) { (data[key] as? String) ?: defValue }
        @Suppress("UNCHECKED_CAST")
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
            synchronized(data) { (data[key] as? Set<String>)?.toMutableSet() ?: defValues }
        override fun getInt(key: String?, defValue: Int): Int =
            synchronized(data) { (data[key] as? Int) ?: defValue }
        override fun getLong(key: String?, defValue: Long): Long =
            synchronized(data) { (data[key] as? Long) ?: defValue }
        override fun getFloat(key: String?, defValue: Float): Float =
            synchronized(data) { (data[key] as? Float) ?: defValue }
        override fun getBoolean(key: String?, defValue: Boolean): Boolean =
            synchronized(data) { (data[key] as? Boolean) ?: defValue }
        override fun contains(key: String?): Boolean = synchronized(data) { data.containsKey(key) }
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val updates = linkedMapOf<String, Any?>()
            private val removals = linkedSetOf<String>()
            private var clearAll = false
            override fun putString(key: String?, value: String?) = apply { if (key != null) updates[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { if (key != null) updates[key] = values?.toSet() }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) updates[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) updates[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) updates[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) updates[key] = value }
            override fun remove(key: String?) = apply { if (key != null) removals.add(key) }
            override fun clear() = apply { clearAll = true }
            override fun commit(): Boolean {
                synchronized(data) {
                    if (clearAll) data.clear()
                    removals.forEach { data.remove(it) }
                    updates.forEach { (k, v) -> if (v == null) data.remove(k) else data[k] = v }
                }
                return true
            }
            override fun apply() {
                commit()
            }
        }
    }
}
