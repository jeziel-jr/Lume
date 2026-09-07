package com.nuvio.tv.core.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamAutoPlayPolicyTest {
    @Test
    fun `non direct playback never requires the picker regardless of stream count`() {
        assertFalse(
            StreamAutoPlayPolicy.shouldRequirePickerForDirectPlay(
                forceDirectPlayback = false,
                playableStreamCount = 3,
                isAutoNext = false
            )
        )
    }

    @Test
    fun `single playable stream keeps direct playback`() {
        assertFalse(
            StreamAutoPlayPolicy.shouldRequirePickerForDirectPlay(
                forceDirectPlayback = true,
                playableStreamCount = 1,
                isAutoNext = false
            )
        )
    }

    @Test
    fun `fresh watch with multiple streams requires the picker`() {
        assertTrue(
            StreamAutoPlayPolicy.shouldRequirePickerForDirectPlay(
                forceDirectPlayback = true,
                playableStreamCount = 2,
                isAutoNext = false
            )
        )
        assertTrue(
            StreamAutoPlayPolicy.shouldRequirePickerForDirectPlay(
                forceDirectPlayback = true,
                playableStreamCount = 5,
                isAutoNext = false
            )
        )
    }

    @Test
    fun `auto-next continuation never requires the picker`() {
        assertFalse(
            StreamAutoPlayPolicy.shouldRequirePickerForDirectPlay(
                forceDirectPlayback = true,
                playableStreamCount = 2,
                isAutoNext = true
            )
        )
        assertFalse(
            StreamAutoPlayPolicy.shouldRequirePickerForDirectPlay(
                forceDirectPlayback = true,
                playableStreamCount = 5,
                isAutoNext = true
            )
        )
    }
}
