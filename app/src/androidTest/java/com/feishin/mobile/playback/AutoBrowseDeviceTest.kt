package com.feishin.mobile.playback

import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Part B verification: drives the Auto browse tree the same way a head unit
 * would — root, the 4 tabs, one level deeper, search round-trip — against the
 * real service on the device.
 *
 * Every MediaBrowser call is issued on the app thread (Media3 requirement);
 * only the future waits happen on the test thread. Assertions are structural
 * (ids, paging works, calls succeed), never tied to particular server data, so
 * the test is stable with or without connectivity.
 */
@RunWith(AndroidJUnit4::class)
class AutoBrowseDeviceTest {

    /** Runs [block] on the app thread and returns its value to the test thread. */
    private fun <T> onMain(block: () -> T): T {
        var result: Any? = null
        var error: Throwable? = null
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            try {
                result = block()
            } catch (t: Throwable) {
                error = t
            } finally {
                latch.countDown()
            }
        }
        assertTrue("main thread call timed out", latch.await(20, TimeUnit.SECONDS))
        error?.let { throw it }
        @Suppress("UNCHECKED_CAST")
        return result as T
    }

    private fun connect(): MediaBrowser {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        // Explicit app component (exactly what a head unit binds to): never
        // the test package, which shares the process but owns no service.
        val token = SessionToken(
            context,
            ComponentName("com.feishin.mobile", "com.feishin.mobile.playback.PlayerService"),
        )
        val future = onMain {
            MediaBrowser.Builder(context, token)
                .setListener(BrowserListener())
                .buildAsync()
        }
        return future.get(30, TimeUnit.SECONDS)
    }

    private class BrowserListener : MediaBrowser.Listener

    /** MediaController.release must run on the app thread, not the test thread. */
    private fun release(browser: MediaBrowser) {
        onMain { runCatching { browser.release() } }
    }

    private fun children(browser: MediaBrowser, parentId: String, page: Int = 0, pageSize: Int = 50) =
        onMain { browser.getChildren(parentId, page, pageSize, null) }
            .get(20, TimeUnit.SECONDS).value?.toList().orEmpty()

    @Test
    fun auto_root_hasExactlyFourTabs() {
        val browser = connect()
        try {
            val root = onMain { browser.getLibraryRoot(null) }.get(20, TimeUnit.SECONDS).value
            assertTrue(root != null)
            val tabs = children(browser, root!!.mediaId)
            assertEquals(4, tabs.size)
            assertEquals(
                listOf("tab_home", "tab_library", "tab_favorites", "tab_downloads"),
                tabs.map { it.mediaId },
            )
            assertTrue(tabs.all { it.mediaMetadata.isBrowsable == true })
        } finally {
            release(browser)
        }
    }

    @Test
    fun auto_secondLevel_fetchesAndPages() {
        val browser = connect()
        try {
            val root = onMain { browser.getLibraryRoot(null) }.get(20, TimeUnit.SECONDS).value!!
            val tabs = children(browser, root.mediaId)
            // Every tab opens; paging a second page never fails (may be empty).
            for (tab in tabs) {
                val first = children(browser, tab.mediaId, 0, 10)
                val second = children(browser, tab.mediaId, 1, 10)
                assertTrue(first.size <= 10)
                assertTrue(second.size <= 10)
                android.util.Log.d("AutoTest", "tab=${tab.mediaId} firstPage=${first.size}")
                // Drill one level deeper where there are folders.
                val folder = first.firstOrNull { it.mediaMetadata.isBrowsable == true }
                if (folder != null) {
                    val deep = children(browser, folder.mediaId, 0, 10)
                    android.util.Log.d(
                        "AutoTest",
                        "node=${folder.mediaId} children=${deep.size} " +
                            "first=${deep.firstOrNull()?.mediaMetadata?.title}",
                    )
                }
            }
            // Pinned collections are always present under Playlists (offline-safe).
            val playlists = children(browser, "lib_playlists", 0, 50)
            val titles = playlists.map { it.mediaMetadata.title?.toString() }
            assertTrue(titles.contains("Favorite songs"))
            assertTrue(titles.contains("Cached"))
        } finally {
            release(browser)
        }
    }

    @Test
    fun auto_search_roundTripSucceeds() {
        val browser = connect()
        try {
            onMain { browser.search("Rokudenashi", null) }.get(25, TimeUnit.SECONDS)
            val results = onMain { browser.getSearchResult("Rokudenashi", 0, 10, null) }
                .get(25, TimeUnit.SECONDS)
            assertTrue((results.value?.size ?: 0) <= 10)
        } finally {
            release(browser)
        }
    }

    @Test
    fun auto_playFromBrowse_startsPlaybackWithRealUri() {
        val browser = connect()
        try {
            val root = onMain { browser.getLibraryRoot(null) }.get(20, TimeUnit.SECONDS).value!!
            val tabs = children(browser, root.mediaId)
            val library = tabs.first { it.mediaId == "tab_library" }
            val libChildren = children(browser, library.mediaId)
            val albumsFolder = libChildren.first { it.mediaId == "lib_albums" }
            val albums = children(browser, albumsFolder.mediaId, 0, 10)
                .filter { it.mediaMetadata.isBrowsable == true }
            assertTrue("expected server albums", albums.isNotEmpty())
            val tracks = children(browser, albums.first().mediaId, 0, 50)
                .filter { it.mediaMetadata.isPlayable == true }
            assertTrue("expected album tracks", tracks.isNotEmpty())
            // Placeholders carry no URI; the service resolves on set.
            assertTrue(tracks.all { it.localConfiguration?.uri == null })
            onMain { browser.setMediaItems(tracks, 0, 0) }
            // setMediaItems is fire-and-forget here; playback readiness is the signal.
            onMain { browser.play() }
            var playing = false
            repeat(40) {
                if (onMain { browser.isPlaying }) {
                    playing = true
                    return@repeat
                }
                Thread.sleep(500)
            }
            assertTrue("expected playback to start from Auto browse", playing)
            val current = onMain { browser.currentMediaItem }
            val uri = current?.localConfiguration?.uri?.toString().orEmpty()
            assertTrue("expected a resolved stream URI, got: $uri", uri.isNotEmpty())
        } finally {
            onMain {
                runCatching { browser.stop() }
                runCatching { browser.clearMediaItems() }
            }
            release(browser)
        }
    }
}
