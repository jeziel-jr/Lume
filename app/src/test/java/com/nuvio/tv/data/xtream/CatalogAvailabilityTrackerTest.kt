package com.nuvio.tv.data.xtream

import com.nuvio.tv.domain.model.ContentType
import com.nuvio.tv.domain.model.MetaPreview
import com.nuvio.tv.domain.model.PosterShape
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogAvailabilityTrackerTest {
    @Test
    fun `loading is checking and ready state reclassifies submitted items`() = runTest {
        val catalogState = MutableStateFlow<XtreamCatalogState>(XtreamCatalogState.Loading)
        val availabilityRevision = MutableStateFlow(0L)
        val service = mockk<XtreamCatalogAvailabilityService>()
        every { service.catalogState } returns catalogState
        every { service.availabilityRevision } returns availabilityRevision
        every { service.aliasRevision } returns MutableStateFlow(0L)
        coEvery { service.classify(any()) } answers {
            firstArg<List<MetaPreview>>().associate {
                it.catalogAvailabilityKey() to CatalogPlaybackAvailability.UNAVAILABLE
            }
        }
        val trackerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val tracker = CatalogAvailabilityTracker(trackerScope, service)
        val item = preview("tmdb:1")

        tracker.submit(listOf(item))
        advanceUntilIdle()
        assertEquals(
            CatalogPlaybackAvailability.UNKNOWN,
            tracker.availability.value[item.catalogAvailabilityKey()],
        )

        catalogState.value = XtreamCatalogState.Ready()
        advanceUntilIdle()
        assertEquals(
            CatalogPlaybackAvailability.UNAVAILABLE,
            tracker.availability.value[item.catalogAvailabilityKey()],
        )
        trackerScope.cancel()
    }

    @Test
    fun `new submission cancels stale classification result`() = runTest {
        val catalogState = MutableStateFlow<XtreamCatalogState>(XtreamCatalogState.Ready())
        val availabilityRevision = MutableStateFlow(0L)
        val service = mockk<XtreamCatalogAvailabilityService>()
        every { service.catalogState } returns catalogState
        every { service.availabilityRevision } returns availabilityRevision
        every { service.aliasRevision } returns MutableStateFlow(0L)
        coEvery { service.classify(any()) } coAnswers {
            val submitted = firstArg<List<MetaPreview>>()
            if (submitted.firstOrNull()?.id == "tmdb:old") delay(1_000L)
            submitted.associate {
                it.catalogAvailabilityKey() to CatalogPlaybackAvailability.UNAVAILABLE
            }
        }
        val trackerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val tracker = CatalogAvailabilityTracker(trackerScope, service)
        val old = preview("tmdb:old")
        val fresh = preview("tmdb:fresh")

        tracker.submit(listOf(old))
        runCurrent()
        tracker.submit(listOf(fresh))
        advanceUntilIdle()

        assertTrue(old.catalogAvailabilityKey() !in tracker.availability.value)
        assertEquals(
            CatalogPlaybackAvailability.UNAVAILABLE,
            tracker.availability.value[fresh.catalogAvailabilityKey()],
        )
        trackerScope.cancel()
    }

    @Test
    fun `catalog error clears badges instead of marking content unavailable`() = runTest {
        val catalogState = MutableStateFlow<XtreamCatalogState>(XtreamCatalogState.Loading)
        val availabilityRevision = MutableStateFlow(0L)
        val service = mockk<XtreamCatalogAvailabilityService>()
        every { service.catalogState } returns catalogState
        every { service.availabilityRevision } returns availabilityRevision
        every { service.aliasRevision } returns MutableStateFlow(0L)
        val trackerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val tracker = CatalogAvailabilityTracker(trackerScope, service)

        tracker.submit(listOf(preview("tmdb:1")))
        advanceUntilIdle()
        catalogState.value = XtreamCatalogState.Error("offline")
        advanceUntilIdle()

        assertTrue(tracker.availability.value.isEmpty())
        trackerScope.cancel()
    }

    @Test
    fun `playback cache update reclassifies visible item after returning from details`() = runTest {
        val catalogState = MutableStateFlow<XtreamCatalogState>(XtreamCatalogState.Ready())
        val availabilityRevision = MutableStateFlow(0L)
        var current = CatalogPlaybackAvailability.UNAVAILABLE
        val service = mockk<XtreamCatalogAvailabilityService>()
        every { service.catalogState } returns catalogState
        every { service.availabilityRevision } returns availabilityRevision
        every { service.aliasRevision } returns MutableStateFlow(0L)
        coEvery { service.classify(any()) } answers {
            firstArg<List<MetaPreview>>().associate {
                it.catalogAvailabilityKey() to current
            }
        }
        val trackerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val tracker = CatalogAvailabilityTracker(trackerScope, service)
        val item = preview("tmdb:862")

        tracker.submit(listOf(item))
        advanceUntilIdle()
        assertEquals(
            CatalogPlaybackAvailability.UNAVAILABLE,
            tracker.availability.value[item.catalogAvailabilityKey()],
        )

        current = CatalogPlaybackAvailability.AVAILABLE
        availabilityRevision.value += 1L
        advanceUntilIdle()
        assertEquals(
            CatalogPlaybackAvailability.AVAILABLE,
            tracker.availability.value[item.catalogAvailabilityKey()],
        )
        trackerScope.cancel()
    }

    @Test
    fun `alias lookup completion reclassifies item awaiting alternative titles`() = runTest {
        val catalogState = MutableStateFlow<XtreamCatalogState>(XtreamCatalogState.Ready())
        val availabilityRevision = MutableStateFlow(0L)
        val aliasRevision = MutableStateFlow(0L)
        var current = CatalogPlaybackAvailability.UNKNOWN
        val service = mockk<XtreamCatalogAvailabilityService>()
        every { service.catalogState } returns catalogState
        every { service.availabilityRevision } returns availabilityRevision
        every { service.aliasRevision } returns aliasRevision
        coEvery { service.classify(any()) } answers {
            firstArg<List<MetaPreview>>().associate {
                it.catalogAvailabilityKey() to current
            }
        }
        val trackerScope = CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        val tracker = CatalogAvailabilityTracker(trackerScope, service)
        val item = preview("tmdb:1429")

        tracker.submit(listOf(item))
        advanceUntilIdle()
        assertEquals(
            CatalogPlaybackAvailability.UNKNOWN,
            tracker.availability.value[item.catalogAvailabilityKey()],
        )

        current = CatalogPlaybackAvailability.LIKELY_AVAILABLE
        aliasRevision.value += 1L
        advanceUntilIdle()
        assertEquals(
            CatalogPlaybackAvailability.LIKELY_AVAILABLE,
            tracker.availability.value[item.catalogAvailabilityKey()],
        )
        trackerScope.cancel()
    }

    private fun preview(id: String) = MetaPreview(
        id = id,
        type = ContentType.MOVIE,
        name = id,
        poster = null,
        posterShape = PosterShape.POSTER,
        background = null,
        logo = null,
        description = null,
        releaseInfo = "2026",
        imdbRating = null,
        genres = emptyList(),
    )
}
