package com.hereliesaz.illumera.ui.player.base

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExoPlayerHttpFallbackTest {

    @Test
    fun rangeNotSatisfiableKeepsDedicatedRestartPathUntilItHasAlreadyRetried() {
        assertFalse(shouldAdvanceSourceForHttpStatus(416, hasRetried416 = false))
        assertTrue(shouldAdvanceSourceForHttpStatus(416, hasRetried416 = true))
    }

    @Test
    fun rejectedOrUnavailableHttpSourceAdvancesToNextCandidate() {
        listOf(401, 403, 404, 410, 429, 500, 502, 503).forEach { status ->
            assertTrue(
                "Expected HTTP $status to advance source",
                shouldAdvanceSourceForHttpStatus(status, hasRetried416 = false)
            )
        }
    }

    @Test
    fun failedRuntimeCheckIsRecognizedForOneShotPlayerRebuild() {
        assertTrue(isFailedRuntimeCheckCode("ERROR_CODE_FAILED_RUNTIME_CHECK"))
        assertFalse(isFailedRuntimeCheckCode("ERROR_CODE_IO_BAD_HTTP_STATUS"))
    }

    @Test
    fun sourceErrorsInTheLastStretchCountAsTheEndNotABadSource() {
        val duration = 45 * 60_000L
        assertTrue(isWithinEndWindow(duration - 5_000L, duration))
        assertTrue(isWithinEndWindow(duration - END_OF_STREAM_WINDOW_MS, duration))
        assertFalse(isWithinEndWindow(duration - END_OF_STREAM_WINDOW_MS - 1, duration))
        assertFalse(isWithinEndWindow(10 * 60_000L, duration))
    }

    @Test
    fun unknownDurationOrNoPositionIsNeverTheEnd() {
        assertFalse(isWithinEndWindow(5_000L, 0L))
        assertFalse(isWithinEndWindow(0L, 20_000L))
    }
}
