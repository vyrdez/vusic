package com.feishin.mobile.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P8-E: playlist sort + filter mapping. Pure logic, so no Android needed. */
class PlaylistOrderingTest {

    // Two artists, deliberately unsorted, with a stable tie between r2 and r5.
    private val titles = listOf(
        "Zebra", "alpha", "Mango", "Zebra", "beta", "Yak",
    )
    private val artists = listOf(
        "Radiohead", "YOASOBI", "Aimer", "Radiohead", "Bump of Chicken", "Yoasobi",
    )
    private val albums = listOf(
        "OK Computer", "A", "Ref:rain", "In Rainbows", "B", "C",
    )
    private val durations = listOf(300, 60, 200, 310, 120, 200)
    private val size = titles.size

    private fun order(sort: PlaylistSort) = PlaylistOrdering.order(
        size, sort, { titles[it] }, { artists[it] }, { durations[it] },
    )

    private fun visible(sort: PlaylistSort, query: String = "") = PlaylistOrdering.visibleIndices(
        size, sort, query, { titles[it] }, { artists[it] }, { albums[it] }, { durations[it] },
    )

    private fun render(indices: List<Int>) = indices.map { titles[it] }

    // ---- toggle semantics ---------------------------------------------------

    @Test
    fun toggle_sameKey_flipsDirection() {
        val a = PlaylistSort(PlaylistSortKey.TITLE, descending = false)
        val b = PlaylistOrdering.toggle(a, PlaylistSortKey.TITLE)
        assertEquals(PlaylistSortKey.TITLE, b.key)
        assertTrue(b.descending)
        assertFalse(PlaylistOrdering.toggle(b, PlaylistSortKey.TITLE).descending)
    }

    @Test
    fun toggle_newKey_startsAscending() {
        val a = PlaylistSort(PlaylistSortKey.TITLE, descending = true)
        val b = PlaylistOrdering.toggle(a, PlaylistSortKey.ARTIST)
        assertEquals(PlaylistSortKey.ARTIST, b.key)
        assertFalse(b.descending)
    }

    @Test
    fun defaultIsTheStoredOrderAscending() {
        assertTrue(PlaylistOrdering.DEFAULT.isDefault)
        assertTrue(PlaylistSort().isDefault)
        assertFalse(PlaylistSort(PlaylistSortKey.TITLE).isDefault)
        assertFalse(PlaylistSort(PlaylistSortKey.PLAYLIST_ORDER, descending = true).isDefault)
    }

    // ---- ordering -----------------------------------------------------------

    @Test
    fun playlistOrderIsIdentity() {
        assertEquals(List(size) { it }, order(PlaylistSort()))
    }

    @Test
    fun playlistOrderDescendingReverses() {
        assertEquals((size - 1 downTo 0).toList(), order(PlaylistSort(PlaylistSortKey.PLAYLIST_ORDER, true)))
    }

    @Test
    fun titleSortIsCaseInsensitive() {
        // "alpha" must sort before "Mango", which only holds if case is folded.
        assertEquals(
            listOf("alpha", "beta", "Mango", "Yak", "Zebra", "Zebra"),
            render(order(PlaylistSort(PlaylistSortKey.TITLE))),
        )
    }

    @Test
    fun titleSortDescending() {
        assertEquals(
            listOf("Zebra", "Zebra", "Yak", "Mango", "beta", "alpha"),
            render(order(PlaylistSort(PlaylistSortKey.TITLE, descending = true))),
        )
    }

    @Test
    fun equalKeysKeepStoredOrder() {
        // Two "Zebra" rows: ascending must keep index 0 before index 3.
        val asc = order(PlaylistSort(PlaylistSortKey.TITLE))
        assertTrue(asc.indexOf(0) < asc.indexOf(3))
        // Reversing the DIRECTION reverses the comparison, but a stable sort
        // still keeps ties in stored order, so the two Zebras do not swap.
        // This is deliberate: ties must not shuffle between reloads.
        val desc = order(PlaylistSort(PlaylistSortKey.TITLE, descending = true))
        assertTrue(desc.indexOf(0) < desc.indexOf(3))
        assertEquals(listOf(0, 3), desc.take(2))
    }

    @Test
    fun artistSort() {
        assertEquals(
            listOf("Aimer", "Bump of Chicken", "Radiohead", "Radiohead", "YOASOBI", "Yoasobi"),
            order(PlaylistSort(PlaylistSortKey.ARTIST)).map { artists[it] },
        )
    }

    @Test
    fun durationSortIsNumeric() {
        // 60,120,200,200,300,310 - string sorting would put 120 before 60.
        assertEquals(listOf(60, 120, 200, 200, 300, 310), order(PlaylistSort(PlaylistSortKey.DURATION)).map { durations[it] })
    }

    @Test
    fun durationSortDescending() {
        assertEquals(listOf(310, 300, 200, 200, 120, 60), order(PlaylistSort(PlaylistSortKey.DURATION, true)).map { durations[it] })
    }

    @Test
    fun dateAddedIsStoredOrderBecauseNoTimestampExists() {
        // Documented behaviour, not a fallback on release year.
        assertEquals(List(size) { it }, order(PlaylistSort(PlaylistSortKey.DATE_ADDED)))
        assertEquals((size - 1 downTo 0).toList(), order(PlaylistSort(PlaylistSortKey.DATE_ADDED, true)))
    }

    @Test
    fun emptyAndSingleListsAreHandled() {
        assertEquals(emptyList<Int>(), PlaylistOrdering.order(0, PlaylistSort(), { "" }, { "" }, { 0 }))
        assertEquals(listOf(0), PlaylistOrdering.order(1, PlaylistSort(PlaylistSortKey.TITLE, true), { "x" }, { "y" }, { 1 }))
    }

    // ---- matching -----------------------------------------------------------

    @Test
    fun matchesTitleArtistAndAlbum() {
        assertTrue(PlaylistOrdering.matches("zebra", "Zebra", "Radiohead", "OK Computer"))
        assertTrue(PlaylistOrdering.matches("radiohead", "Zebra", "Radiohead", "OK Computer"))
        assertTrue(PlaylistOrdering.matches("computer", "Zebra", "Radiohead", "OK Computer"))
        assertFalse(PlaylistOrdering.matches("portishead", "Zebra", "Radiohead", "OK Computer"))
    }

    @Test
    fun matchesIsCaseAndSpaceInsensitive() {
        assertTrue(PlaylistOrdering.matches("  YoAsObI ", "Zebra", "YOASOBI", "A"))
    }

    @Test
    fun everyTermMustMatch() {
        assertTrue(PlaylistOrdering.matches("yoasobi love", "A Love Song", "YOASOBI", "A"))
        assertFalse(PlaylistOrdering.matches("yoasobi nothing", "A Love Song", "YOASOBI", "A"))
    }

    @Test
    fun blankQueryMatchesEverything() {
        assertTrue(PlaylistOrdering.matches("", "anything", "x", "y"))
        assertTrue(PlaylistOrdering.matches("   ", "anything", "x", "y"))
    }

    // ---- combined mapping ---------------------------------------------------

    @Test
    fun filterThenSort() {
        val sort = PlaylistSort(PlaylistSortKey.TITLE)
        assertEquals(listOf(0, 3), visible(PlaylistOrdering.DEFAULT, "zebra"))
        // "yoasobi" hits the artist of rows 1 and 5; TITLE sort puts
        // "alpha" (row 1) before "Yak" (row 5).
        assertEquals(listOf("alpha", "Yak"), render(visible(sort, "yoasobi")))
        // Matching on the album alone must work too: only row 0 is on
        // "OK Computer", and its sibling Radiohead track is not.
        assertEquals(listOf("Zebra"), render(visible(PlaylistOrdering.DEFAULT, "computer")))
    }

    @Test
    fun filteredRowsMapBackToTheirSourceIndices() {
        val got = visible(PlaylistOrdering.DEFAULT, "zebra")
        assertEquals(listOf(0, 3), got)
        // Each returned index must really be the row the user matched.
        for (i in got) {
            assertTrue(titles[i].contains("Zebra", ignoreCase = true))
        }
    }

    @Test
    fun filterThatMatchesNothingYieldsNothing() {
        assertEquals(emptyList<Int>(), visible(PlaylistOrdering.DEFAULT, "portishead"))
    }

    @Test
    fun filterWithDescendingSortKeepsSourceMapping() {
        val got = visible(PlaylistSort(PlaylistSortKey.DURATION, descending = true), "zebra")
        // The two Zebra rows are 300s and 310s: 310 first when descending.
        assertEquals(listOf(3, 0), got)
    }

    @Test
    fun filterWithDefaultSortKeepsStoredOrder() {
        // Only row 5 ("Yak") contains "ya"; the stored order is preserved.
        assertEquals(listOf(5), visible(PlaylistOrdering.DEFAULT, "ya"))
        // Rows 1 and 5 share the artist YOASOBI: stored order, not alpha order.
        assertEquals(listOf(1, 5), visible(PlaylistOrdering.DEFAULT, "yoasobi"))
    }

    @Test
    fun visibleIndicesIsAPermutationOfTheMatches() {
        val sort = PlaylistSort(PlaylistSortKey.ARTIST)
        val got = visible(sort, "a")
        assertEquals(got.distinct(), got)
        assertTrue(got.all { it in 0 until size })
    }

    @Test
    fun everySortAndQueryCombinationStaysInBounds() {
        for (key in PlaylistSortKey.entries) {
            for (desc in listOf(false, true)) {
                for (q in listOf("", "a", "zebra", "yoasobi love", "nothing")) {
                    val got = visible(PlaylistSort(key, desc), q)
                    assertTrue("$key desc=$desc q=$q", got.all { it in 0 until size })
                    assertEquals("$key desc=$desc q=$q", got.distinct(), got)
                }
            }
        }
    }

    // ---- mutation guard -----------------------------------------------------

    @Test
    fun mutationAllowedOnlyInDefaultOrderWithNoQuery() {
        assertTrue(PlaylistOrdering.canMutateOrder(PlaylistOrdering.DEFAULT, ""))
        assertTrue(PlaylistOrdering.canMutateOrder(PlaylistOrdering.DEFAULT, "   "))
        assertFalse(PlaylistOrdering.canMutateOrder(PlaylistSort(PlaylistSortKey.TITLE), ""))
        assertFalse(PlaylistOrdering.canMutateOrder(PlaylistOrdering.DEFAULT, "a"))
        assertFalse(
            PlaylistOrdering.canMutateOrder(
                PlaylistSort(PlaylistSortKey.PLAYLIST_ORDER, descending = true), "",
            ),
        )
    }

    // ---- persistence --------------------------------------------------------

    @Test
    fun sortRoundTripsThroughItsStoredForm() {
        for (key in PlaylistSortKey.entries) {
            for (desc in listOf(false, true)) {
                val s = PlaylistSort(key, desc)
                assertEquals(s, PlaylistSort.parse(s.serialize()))
            }
        }
    }

    @Test
    fun parseFallsBackOnCorruptValues() {
        assertEquals(PlaylistOrdering.DEFAULT, PlaylistSort.parse(null))
        assertEquals(PlaylistOrdering.DEFAULT, PlaylistSort.parse(""))
        assertEquals(PlaylistOrdering.DEFAULT, PlaylistSort.parse("   "))
        // An unknown key degrades to the default, keeping whatever direction
        // was stored rather than throwing on a downgrade.
        val recovered = PlaylistSort.parse("REMOVED_KEY|true")
        assertEquals(PlaylistSortKey.PLAYLIST_ORDER, recovered.key)
        assertTrue(recovered.descending)
    }
}