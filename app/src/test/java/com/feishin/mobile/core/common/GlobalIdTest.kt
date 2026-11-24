package com.feishin.mobile.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class GlobalIdTest {

    @Test
    fun validRemoteId_formatsAndParses() {
        assertEquals("p1:tr-1", GlobalId.format("p1", "tr-1"))
        assertEquals("p1:tr-1", GlobalId.format("p1", "p1:tr-1"))
        assertEquals("p1" to "tr-1", GlobalId.parse("p1:tr-1"))
        assertEquals("p1", GlobalId.profileIdOrNull("p1:tr-1"))
        assertEquals("tr-1", GlobalId.rawId("p1:tr-1"))
    }

    @Test
    fun colonContainingRawId_preservesFullSuffix() {
        assertEquals("p1:foo:bar:baz", GlobalId.format("p1", "foo:bar:baz"))
        assertEquals("p1" to "foo:bar:baz", GlobalId.parse("p1:foo:bar:baz"))
        assertEquals("p1", GlobalId.profileIdOrNull("p1:foo:bar:baz"))
        assertEquals("foo:bar:baz", GlobalId.rawId("p1:foo:bar:baz"))
    }

    @Test
    fun localIds_areReservedAndNeverPrefixedOrParsed() {
        val deviceIds = listOf("local:42", "local_album:9", "local_artist:1")
        for (id in deviceIds) {
            assertTrue(GlobalId.isDeviceLocal(id))
            assertTrue(GlobalId.isReserved(id))
            assertEquals(id, GlobalId.format("p1", id))
            assertNull(GlobalId.parse(id))
            assertNull(GlobalId.profileIdOrNull(id))
            assertEquals(id, GlobalId.rawId(id))
        }

        val localPlaylistId = "local_pl:uuid"
        assertFalse(GlobalId.isDeviceLocal(localPlaylistId))
        assertTrue(GlobalId.isReserved(localPlaylistId))
        assertEquals(localPlaylistId, GlobalId.format("p1", localPlaylistId))
        assertNull(GlobalId.parse(localPlaylistId))
        assertNull(GlobalId.profileIdOrNull(localPlaylistId))
        assertEquals(localPlaylistId, GlobalId.rawId(localPlaylistId))
    }

    @Test
    fun virtualIds_areReservedAndNeverPrefixedOrParsed() {
        for (id in listOf("__favorites__", "__cached__")) {
            assertFalse(GlobalId.isDeviceLocal(id))
            assertTrue(GlobalId.isReserved(id))
            assertEquals(id, GlobalId.format("p1", id))
            assertNull(GlobalId.parse(id))
            assertNull(GlobalId.profileIdOrNull(id))
            assertEquals(id, GlobalId.rawId(id))
        }
    }

    @Test
    fun autoCollectionIds_preventedFromMatchingAsProfileId() {
        val autoId = "col_pl:p1:pl_42"
        assertFalse(GlobalId.isDeviceLocal(autoId))
        assertTrue(GlobalId.isReserved(autoId))
        assertEquals(autoId, GlobalId.format("p1", autoId))
        assertNull(GlobalId.parse(autoId))
        assertNull(GlobalId.profileIdOrNull(autoId))
        assertEquals(autoId, GlobalId.rawId(autoId))
    }

    @Test
    fun invalidProfileIds_rejectedByFormat() {
        assertThrows(IllegalArgumentException::class.java) { GlobalId.format("", "tr-1") }
        assertThrows(IllegalArgumentException::class.java) { GlobalId.format("bad:id", "tr-1") }
        assertThrows(IllegalArgumentException::class.java) { GlobalId.format("bad id", "tr-1") }
    }

    @Test
    fun malformedOrLegacyIds_returnNullFromParse() {
        for (id in listOf("p1:", ":tr-1", "tr-1", "bad id:tr-1", "")) {
            assertNull("Expected null for $id", GlobalId.parse(id))
            assertNull(GlobalId.profileIdOrNull(id))
            assertEquals(id, GlobalId.rawId(id))
        }
    }
}
