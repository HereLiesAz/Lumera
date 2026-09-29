package com.hereliesaz.illumera.domain

import com.hereliesaz.illumera.data.model.stremio.MetaVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EpisodeUtilsTest {

    @Test
    fun playbackIdAlwaysUsesSeriesSeasonAndEpisode() {
        val episode = MetaVideo(id = "addon-specific-id", season = 2, episode = 7)

        assertEquals("series123:2:7", episodePlaybackId("series123", episode))
    }

    @Test
    fun streamIdUsesAddonIdWhenPresentAndFallsBackWhenBlank() {
        assertEquals(
            "addon-id",
            episodeStreamId("series", MetaVideo(id = "addon-id", season = 3, episode = 4))
        )
        assertEquals(
            "series:3:4",
            episodeStreamId("series", MetaVideo(id = "   ", season = 3, episode = 4))
        )
    }

    @Test
    fun displayTitleUsesSafeDefaultsForInvalidSeasonAndEpisodeNumbers() {
        assertEquals(
            "S1:E1 - Pilot",
            episodeDisplayTitle(MetaVideo(title = "Pilot", season = 0, episode = -1))
        )
        assertEquals(
            "S2:E9 - Finale",
            episodeDisplayTitle(MetaVideo(title = "Finale", season = 2, episode = 9))
        )
    }

    @Test
    fun hasAiredTreatsMissingAndPastDatesAsAired() {
        assertTrue(MetaVideo(released = null).hasAired())
        assertTrue(MetaVideo(released = "2000-01-01T12:00:00.000Z").hasAired())
    }

    @Test
    fun hasAiredRejectsFarFutureDates() {
        assertFalse(MetaVideo(released = "2999-01-01T00:00:00.000Z").hasAired())
    }

    @Test
    fun findNextEpisodeSortsInputAndSkipsSpecials() {
        val episodes = listOf(
            MetaVideo(id = "s2e1", title = "S2E1", season = 2, episode = 1, released = "2000-01-01"),
            MetaVideo(id = "special", title = "Special", season = 0, episode = 1, released = "2000-01-01"),
            MetaVideo(id = "s1e2", title = "S1E2", season = 1, episode = 2, released = "2000-01-01"),
            MetaVideo(id = "s1e1", title = "S1E1", season = 1, episode = 1, released = "2000-01-01"),
            MetaVideo(id = "invalid", title = "Invalid", season = 1, episode = 0, released = "2000-01-01")
        )

        val next = findNextEpisode("show", "show:1:1", episodes)

        assertEquals("s1e2", next?.id)
    }

    @Test
    fun findNextEpisodeSupportsLegacyIdsEndingInSeasonAndEpisode() {
        val episodes = listOf(
            MetaVideo(id = "one", season = 1, episode = 1, released = "2000-01-01"),
            MetaVideo(id = "two", season = 1, episode = 2, released = "2000-01-01")
        )

        val next = findNextEpisode("show", "legacy:anything:1:1", episodes)

        assertEquals("two", next?.id)
    }

    @Test
    fun findNextEpisodeReturnsNullForUnknownCurrentOrLastEpisode() {
        val episodes = listOf(
            MetaVideo(id = "one", season = 1, episode = 1, released = "2000-01-01"),
            MetaVideo(id = "two", season = 1, episode = 2, released = "2000-01-01")
        )

        assertNull(findNextEpisode("show", "show:9:9", episodes))
        assertNull(findNextEpisode("show", "show:1:2", episodes))
        assertNull(findNextEpisode("show", "show:1:1", emptyList()))
    }

    @Test
    fun findNextEpisodeNeverAutoplaysUnairedEpisode() {
        val episodes = listOf(
            MetaVideo(id = "one", season = 1, episode = 1, released = "2000-01-01"),
            MetaVideo(id = "two", season = 1, episode = 2, released = "2999-01-01")
        )

        assertNull(findNextEpisode("show", "show:1:1", episodes))
    }

    @Test
    fun findNextEpisodeSkipsUndatedEpisodeAfterTheNewestAiredOne() {
        val episodes = listOf(
            MetaVideo(id = "seven", season = 1, episode = 7, released = "2000-01-01"),
            MetaVideo(id = "eight", season = 1, episode = 8, released = null)
        )

        assertNull(findNextEpisode("show", "show:1:7", episodes))
    }

    @Test
    fun findNextEpisodeKeepsUndatedEpisodeWhenALaterOneAired() {
        val episodes = listOf(
            MetaVideo(id = "one", season = 1, episode = 1, released = "2000-01-01"),
            MetaVideo(id = "two", season = 1, episode = 2, released = null),
            MetaVideo(id = "three", season = 1, episode = 3, released = "2000-01-15")
        )

        assertEquals("two", findNextEpisode("show", "show:1:1", episodes)?.id)
    }

    @Test
    fun findNextEpisodeKeepsUndatedEpisodesOfAShowWithNoDates() {
        val episodes = listOf(
            MetaVideo(id = "one", season = 1, episode = 1),
            MetaVideo(id = "two", season = 1, episode = 2)
        )

        assertEquals("two", findNextEpisode("show", "show:1:1", episodes)?.id)
    }

    @Test
    fun findNextEpisodeFollowsTheVariantBeingPlayed() {
        val cutA = MetaVideo(id = "cut-a", season = 1, episode = 1, released = "2000-01-01")
        val cutB = MetaVideo(id = "cut-b", season = 1, episode = 1, released = "2000-01-01")
        val two = MetaVideo(id = "two", season = 1, episode = 2, released = "2000-01-01")
        val episodes = listOf(cutA, cutB, two)

        val playing = episodePlaybackId("show", cutB, episodes)
        assertEquals("show:variant=cut-b:1:1", playing)
        assertEquals("two", findNextEpisode("show", playing, episodes)?.id)
        // The other cut of the same episode is never its "next".
        assertEquals("two", findNextEpisode("show", episodePlaybackId("show", cutA, episodes), episodes)?.id)
    }

    @Test
    fun findNextEpisodeStaysFastOnVeryLongShows() {
        // Every episode has two variants: the old per-episode recount made this quadratic.
        val episodes = (1..3_000).flatMap { n ->
            listOf(
                MetaVideo(id = "a$n", season = 1, episode = n, released = "2000-01-01"),
                MetaVideo(id = "b$n", season = 1, episode = n, released = "2000-01-01")
            )
        }
        val playing = episodePlaybackId("show", episodes[5_990], episodes)
        val start = System.nanoTime()
        val next = findNextEpisode("show", playing, episodes)
        val elapsedMs = (System.nanoTime() - start) / 1_000_000
        assertEquals(2_997, next?.episode)
        assertTrue("took ${elapsedMs}ms", elapsedMs < 500)
    }
}
