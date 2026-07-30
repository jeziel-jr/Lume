package com.nuvio.tv.ui.screens.player

import com.nuvio.tv.data.local.StreamAutoPlayMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NextEpisodeEndPromptRulesTest {

    private val airedNextEpisode = NextEpisodeInfo(
        videoId = "next",
        season = 1,
        episode = 2,
        title = "Next episode",
        thumbnail = null,
        overview = null,
        released = null,
        hasAired = true,
        unairedMessage = null,
    )

    @Test
    fun `manual mode with binge group preference disabled keeps terminal prompt eligible`() {
        val state = PlayerUiState(
            playbackEnded = true,
            nextEpisode = airedNextEpisode,
            streamAutoPlayMode = StreamAutoPlayMode.MANUAL,
            streamAutoPlayPreferBingeGroupForNextEpisode = false,
            streamAutoPlayNextEpisodeEnabled = false,
        )

        assertTrue(shouldShowNextEpisodeEndPrompt(state))
    }

    @Test
    fun `next episode autoplay enabled keeps terminal prompt ineligible`() {
        val state = PlayerUiState(
            playbackEnded = true,
            nextEpisode = airedNextEpisode,
            streamAutoPlayNextEpisodeEnabled = true,
        )

        assertFalse(shouldShowNextEpisodeEndPrompt(state))
    }

    @Test
    fun `next episode that has not aired keeps terminal prompt ineligible`() {
        val state = PlayerUiState(
            playbackEnded = true,
            nextEpisode = airedNextEpisode.copy(hasAired = false),
            streamAutoPlayNextEpisodeEnabled = false,
        )

        assertFalse(shouldShowNextEpisodeEndPrompt(state))
    }

    @Test
    fun `missing next episode keeps terminal prompt ineligible`() {
        val state = PlayerUiState(
            playbackEnded = true,
            streamAutoPlayNextEpisodeEnabled = false,
        )

        assertFalse(shouldShowNextEpisodeEndPrompt(state))
    }

    @Test
    fun `error keeps terminal prompt ineligible`() {
        val state = PlayerUiState(
            playbackEnded = true,
            error = "Playback failed",
            nextEpisode = airedNextEpisode,
            streamAutoPlayNextEpisodeEnabled = false,
        )

        assertFalse(shouldShowNextEpisodeEndPrompt(state))
    }
}
