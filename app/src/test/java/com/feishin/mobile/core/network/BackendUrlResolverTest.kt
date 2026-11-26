package com.feishin.mobile.core.network

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import coil.request.ImageRequest
import com.feishin.mobile.data.auth.CredentialsStore
import com.feishin.mobile.data.auth.ProfileStore
import com.feishin.mobile.data.settings.SettingsStore
import com.feishin.mobile.domain.model.BackendType
import com.feishin.mobile.domain.model.ProfileCredentials
import com.feishin.mobile.domain.model.ServerProfile
import com.feishin.mobile.domain.model.Track
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackendUrlResolverTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun stubContext(): Context = object : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this
    }

    private fun newProfileStore(scope: CoroutineScope, profiles: List<ServerProfile>): ProfileStore {
        val settings = SettingsStore(
            PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { File(tmp.root, "settings_${UUID.randomUUID()}.preferences_pb") },
            ),
        )
        val ctx = stubContext()
        val store = ProfileStore(
            prefs = InMemorySharedPreferences(),
            legacyCredentials = CredentialsStore(ctx),
            settings = settings,
        )
        store.saveAll(profiles)
        return store
    }

    private fun subsonicProfile(
        id: String,
        url: String,
        username: String,
        password: String,
        type: BackendType = BackendType.NAVIDROME,
    ) = ServerProfile(
        id = id,
        name = id,
        type = type,
        url = url,
        userId = username,
        credentials = ProfileCredentials.Subsonic(username = username, password = password),
        priority = 0,
    )

    private fun jellyfinProfile(
        id: String = "jf1",
        url: String = "https://jf.example.com:8096",
    ) = ServerProfile(
        id = id,
        name = "Jellyfin",
        type = BackendType.JELLYFIN,
        url = url,
        userId = "user-42",
        credentials = ProfileCredentials.Jellyfin(
            username = "jfuser",
            accessToken = "tok-secret",
            serverId = "srv-1",
            deviceId = "dev-9",
        ),
        priority = 1,
    )

    private fun driveProfile(id: String = "gd1") = ServerProfile(
        id = id,
        name = "Google Drive",
        type = BackendType.GOOGLE_DRIVE,
        url = "https://www.googleapis.com",
        userId = "me@example.com",
        credentials = ProfileCredentials.GoogleDrive(
            accountEmail = "me@example.com",
            accessToken = "ya29.token",
        ),
        priority = 2,
    )

    private fun sampleTrack(id: String, localUri: String? = null) = Track(
        id = id,
        title = "Track",
        album = "Album",
        albumId = "al-1",
        artist = "Artist",
        artistId = "ar-1",
        coverArt = id,
        durationSec = 180,
        trackNo = 1,
        year = 2024,
        genre = "Rock",
        suffix = "flac",
        starred = false,
        localUri = localUri,
    )

    @Test
    fun streamUrlRoutesPerProfileAndPassesThroughLocalUri() = runTest {
        val p1 = subsonicProfile("p1", "https://nav.example.com/music", "alice", "pass1")
        val p2 = subsonicProfile("p2", "https://sub.example.com:4533", "bob", "pass2", BackendType.OPENSUBSONIC)
        val resolver = BackendUrlResolver(newProfileStore(backgroundScope, listOf(p1, p2, jellyfinProfile(), driveProfile())))

        // Local URI passthrough
        val localTrack = sampleTrack("local:42", localUri = "content://media/external/audio/media/42")
        assertEquals("content://media/external/audio/media/42", resolver.streamUrl(localTrack, 320, "mp3"))

        // p1 Subsonic raw stream (no maxBitRate/format params)
        val p1Url = resolver.streamUrl(sampleTrack("p1:song-1"))!!.toHttpUrl()
        assertEquals("nav.example.com", p1Url.host)
        assertEquals("/music/rest/stream.view", p1Url.encodedPath)
        assertEquals("song-1", p1Url.queryParameter("id"))
        assertEquals("alice", p1Url.queryParameter("u"))
        assertNotNull(p1Url.queryParameter("t"))
        assertNotNull(p1Url.queryParameter("s"))
        assertNull(p1Url.queryParameter("maxBitRate"))
        assertNull(p1Url.queryParameter("format"))

        // p2 OpenSubsonic transcoded stream
        val p2Url = resolver.streamUrl(sampleTrack("p2:song-2"), maxBitrate = 192, format = "opus")!!.toHttpUrl()
        assertEquals("sub.example.com", p2Url.host)
        assertEquals(4533, p2Url.port)
        assertEquals("song-2", p2Url.queryParameter("id"))
        assertEquals("bob", p2Url.queryParameter("u"))
        assertEquals("192", p2Url.queryParameter("maxBitRate"))
        assertEquals("opus", p2Url.queryParameter("format"))

        // Legacy bare ID resolves to p1
        val legacyUrl = resolver.streamUrl(sampleTrack("bare-99"))!!.toHttpUrl()
        assertEquals("nav.example.com", legacyUrl.host)
        assertEquals("bare-99", legacyUrl.queryParameter("id"))

        // Jellyfin direct + progressive universal transcoded hooks (including /web trim)
        val jfDirect = resolver.streamUrl(sampleTrack("jf1:jf-10"))!!.toHttpUrl()
        assertEquals("/Audio/jf-10/stream", jfDirect.encodedPath)
        assertEquals("true", jfDirect.queryParameter("static"))

        val jfTranscode = resolver.streamUrl(sampleTrack("jf1:jf-10"), maxBitrate = 192, format = "opus")!!.toHttpUrl()
        assertEquals("/Audio/jf-10/universal", jfTranscode.encodedPath)
        assertEquals("user-42", jfTranscode.queryParameter("UserId"))
        assertEquals("dev-9", jfTranscode.queryParameter("DeviceId"))
        assertEquals("192000", jfTranscode.queryParameter("MaxStreamingBitrate"))
        assertEquals("opus", jfTranscode.queryParameter("AudioCodec"))
        assertEquals("opus", jfTranscode.queryParameter("Container"))
        assertEquals("http", jfTranscode.queryParameter("TranscodingProtocol"))
        assertEquals("opus", jfTranscode.queryParameter("TranscodingContainer"))
        assertEquals("false", jfTranscode.queryParameter("EnableRedirection"))
        assertEquals("false", jfTranscode.queryParameter("EnableRemoteMedia"))

        val jfRawBitrateFallback = resolver.streamUrl(sampleTrack("jf1:jf-10"), maxBitrate = 128, format = "raw")!!.toHttpUrl()
        assertEquals("/Audio/jf-10/universal", jfRawBitrateFallback.encodedPath)
        assertEquals("128000", jfRawBitrateFallback.queryParameter("MaxStreamingBitrate"))
        assertEquals("mp3", jfRawBitrateFallback.queryParameter("AudioCodec"))
        assertEquals("mp3", jfRawBitrateFallback.queryParameter("Container"))
        assertEquals("http", jfRawBitrateFallback.queryParameter("TranscodingProtocol"))
        assertEquals("mp3", jfRawBitrateFallback.queryParameter("TranscodingContainer"))
        assertEquals("false", jfRawBitrateFallback.queryParameter("EnableRemoteMedia"))

        val jfWebTrim = BackendUrlResolver(
            newProfileStore(backgroundScope, listOf(jellyfinProfile("jf2", "https://jf.example.com:8096/web/"))),
        )
        assertEquals(
            "https://jf.example.com:8096/Audio/jf-10/stream?static=true",
            jfWebTrim.streamUrl(sampleTrack("jf2:jf-10")),
        )
        assertEquals(
            "https://jf.example.com:8096/Items/alb-1/Images/Primary?maxWidth=512&quality=90",
            jfWebTrim.coverArtUrl("jf2:alb-1", 512),
        )

        // Google Drive hook
        assertEquals(
            "https://www.googleapis.com/drive/v3/files/drv-7?alt=media",
            resolver.streamUrl(sampleTrack("gd1:drv-7")),
        )
    }

    @Test
    fun missingOrWrongProfileReturnsNullSafely() = runTest {
        val p1 = subsonicProfile("p1", "https://nav.example.com", "alice", "pass1")
        val resolver = BackendUrlResolver(newProfileStore(backgroundScope, listOf(p1)))

        assertNull(resolver.streamUrl(sampleTrack("p2:song-1")))
        assertNull(resolver.coverArtUrl("p2:al-1", 512))
        assertNull(resolver.downloadUrl(sampleTrack("p2:song-1")))
        assertNull(resolver.streamUrl(sampleTrack("local:99", localUri = null)))
        assertNull(resolver.coverArtUrl("local_album:99", 512))
        assertNull(resolver.coverArtUrl("__favorites__", 512))
    }

    @Test
    fun coverArtUrlUsesStableSaltAndPassesThroughLocalUris() = runTest {
        val p1 = subsonicProfile("p1", "https://nav.example.com", "alice", "pass1")
        val p2 = subsonicProfile("p2", "https://sub.example.com:8443", "bob", "pass2")
        val resolver = BackendUrlResolver(newProfileStore(backgroundScope, listOf(p1, p2, jellyfinProfile(), driveProfile())))

        assertNull(resolver.coverArtUrl(null))
        assertNull(resolver.coverArtUrl("  "))
        assertEquals("content://media/art/1", resolver.coverArtUrl("content://media/art/1"))
        assertEquals("file:///tmp/cover.jpg", resolver.coverArtUrl("file:///tmp/cover.jpg"))

        val first = resolver.coverArtUrl("p1:al-5", 512)!!
        val second = resolver.coverArtUrl("p1:al-5", 512)!!
        assertEquals(first, second)
        val parsedP1 = first.toHttpUrl()
        assertEquals("al-5", parsedP1.queryParameter("id"))
        assertEquals("512", parsedP1.queryParameter("size"))
        assertEquals("alice", parsedP1.queryParameter("u"))

        val parsedP2 = resolver.coverArtUrl("p2:al-8", 256)!!.toHttpUrl()
        assertEquals("sub.example.com", parsedP2.host)
        assertEquals(8443, parsedP2.port)
        assertEquals("al-8", parsedP2.queryParameter("id"))
        assertEquals("256", parsedP2.queryParameter("size"))
        assertEquals("bob", parsedP2.queryParameter("u"))

        assertEquals(
            "https://jf.example.com:8096/Items/item-3/Images/Primary?maxWidth=512&quality=90",
            resolver.coverArtUrl("jf1:item-3", 512),
        )
        assertEquals("drive-cover://gd1/f-9", resolver.coverArtUrl("gd1:cov:f-9", 512))
        assertEquals(
            "https://www.googleapis.com/drive/v3/files/folder-art?alt=media",
            resolver.coverArtUrl("gd1:folder-art", 512),
        )
    }

    @Test
    fun downloadUrlRoutesPerBackend() = runTest {
        val p1 = subsonicProfile("p1", "https://nav.example.com", "alice", "pass1")
        val resolver = BackendUrlResolver(newProfileStore(backgroundScope, listOf(p1, jellyfinProfile(), driveProfile())))

        assertEquals(
            "file:///music/song.flac",
            resolver.downloadUrl(sampleTrack("local:1", localUri = "file:///music/song.flac")),
        )

        val subDl = resolver.downloadUrl(sampleTrack("p1:song-7"))!!.toHttpUrl()
        assertEquals("/rest/download.view", subDl.encodedPath)
        assertEquals("song-7", subDl.queryParameter("id"))
        assertEquals("alice", subDl.queryParameter("u"))

        assertEquals(
            "https://jf.example.com:8096/Audio/jf-7/stream?static=true",
            resolver.downloadUrl(sampleTrack("jf1:jf-7")),
        )
        assertEquals(
            "https://www.googleapis.com/drive/v3/files/drv-8?alt=media",
            resolver.downloadUrl(sampleTrack("gd1:drv-8")),
        )
    }

    @Test
    fun customCacheKeyPreservesP1V6CompatibilityAndNamespacesP2WithPort() {
        val p1 = subsonicProfile("p1", "https://nav.example.com:8443/music", "alice", "pass1")
        val p2 = subsonicProfile("p2", "https://sub.example.com:8443/music", "bob", "pass2")
        val store = ProfileStore(
            prefs = InMemorySharedPreferences(),
            legacyCredentials = CredentialsStore(stubContext()),
            settings = SettingsStore(
                PreferenceDataStoreFactory.create(
                    produceFile = { File(tmp.root, "settings_${UUID.randomUUID()}.preferences_pb") },
                ),
            ),
        )
        store.saveAll(listOf(p1, p2))
        val resolver = BackendUrlResolver(store)

        val p1Stream = "https://nav.example.com:8443/music/rest/stream.view?id=song-1&maxBitRate=320&format=mp3&u=alice&t=abc&s=xyz"
        val p1Key = resolver.customCacheKey(p1Stream, "p1:song-1")
        assertEquals(
            "https://nav.example.com/music/rest/stream.view?format=mp3&id=song-1&maxBitRate=320",
            p1Key,
        )

        // Legacy bare mediaId also produces v6-compatible key
        assertEquals(p1Key, resolver.customCacheKey(p1Stream, "song-1"))

        val p2Stream = "https://sub.example.com:8443/music/rest/stream.view?id=song-1&maxBitRate=320&format=mp3&u=bob&t=def&s=123"
        val p2Key = resolver.customCacheKey(p2Stream, "p2:song-1")
        assertEquals(
            "p2:https://sub.example.com:8443/music/rest/stream.view?format=mp3&id=song-1&maxBitRate=320",
            p2Key,
        )
        assertTrue(p2Key.startsWith("p2:"))
    }

    @Test
    fun coilImageRequestCarriesCoverCacheKeyTag() {
        val stubLifecycle = object : Lifecycle() {
            override val currentState: State = State.RESUMED
            override fun addObserver(observer: LifecycleObserver) = Unit
            override fun removeObserver(observer: LifecycleObserver) = Unit
        }
        val req = ImageRequest.Builder(stubContext())
            .data("https://sub.example.com:8443/rest/getCoverArt.view?id=al-20&size=256")
            .size(256)
            .lifecycle(stubLifecycle)
            .tag(CoverCacheKey::class.java, CoverCacheKey("p2:al-20"))
            .build()

        assertEquals("p2:al-20", req.tags.tag(CoverCacheKey::class.java)?.key)
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
