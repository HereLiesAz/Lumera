package com.hereliesaz.illumera.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RowRestoreIndexTest {

    private val ids = listOf("tt1", "tt2", "tt3", "tt4")

    @Test
    fun exactMatchIsKept() {
        assertEquals(2, resolveRowRestoreIndex("3_tt3_2", 3, ids))
    }

    @Test
    fun shiftedItemIsFoundById() {
        // tt1 was removed (e.g. marked watched), so tt3 moved from index 2 to 1.
        assertEquals(1, resolveRowRestoreIndex("3_tt3_2", 3, listOf("tt2", "tt3", "tt4")))
    }

    @Test
    fun removedItemFallsBackToNearestNeighbourNotTheFirstItem() {
        assertEquals(2, resolveRowRestoreIndex("3_tt3_2", 3, listOf("tt1", "tt2", "tt4")))
        // Removed last item: the new last item.
        assertEquals(2, resolveRowRestoreIndex("3_tt4_3", 3, listOf("tt1", "tt2", "tt3")))
    }

    @Test
    fun idsContainingUnderscoresAndViewMoreResolve() {
        assertEquals(1, resolveRowRestoreIndex("0_kitsu_12_1", 0, listOf("a", "kitsu_12")))
        assertEquals(2, resolveRowRestoreIndex("0_viewmore_2", 0, listOf("a", "b", "viewmore")))
    }

    @Test
    fun otherRowsOrNoKeyOrEmptyRowGiveNull() {
        assertNull(resolveRowRestoreIndex(null, 3, ids))
        assertNull(resolveRowRestoreIndex("4_tt3_2", 3, ids))
        assertNull(resolveRowRestoreIndex("3_tt3_2", 3, emptyList()))
    }
}
