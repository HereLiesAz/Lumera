package com.hereliesaz.illumera.ui.queue

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueFocusNeighbourTest {

    @Test
    fun removedCardHandsFocusToTheCardNowAtItsPositionInTheSameRow() {
        val before = listOf(listOf("a", "b", "c"), listOf("s1", "s2"))
        val after = listOf(listOf("a", "c"), listOf("s1", "s2"))
        assertEquals("c", queueFocusNeighbour("b", before, after))
    }

    @Test
    fun removedLastCardFallsBackToTheNewLastCard() {
        val before = listOf(listOf("a", "b"), listOf("s1"))
        val after = listOf(listOf("a"), listOf("s1"))
        assertEquals("a", queueFocusNeighbour("b", before, after))
    }

    @Test
    fun emptiedRowFallsBackToTheNearestOtherRow() {
        val before = listOf(listOf("a"), listOf("s1", "s2"))
        val after = listOf(emptyList(), listOf("s1", "s2"))
        assertEquals("s1", queueFocusNeighbour("a", before, after))
    }

    @Test
    fun nothingLeftGivesNull() {
        assertNull(queueFocusNeighbour("a", listOf(listOf("a"), emptyList()), listOf(emptyList(), emptyList())))
    }
}
