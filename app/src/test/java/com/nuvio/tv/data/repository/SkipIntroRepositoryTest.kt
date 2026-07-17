package com.nuvio.tv.data.repository

import com.nuvio.tv.core.tmdb.TmdbService
import com.nuvio.tv.data.local.AnimeSkipSettingsDataStore
import com.nuvio.tv.data.remote.api.AniSkipApi
import com.nuvio.tv.data.remote.api.AniSkipInterval
import com.nuvio.tv.data.remote.api.AniSkipResponse
import com.nuvio.tv.data.remote.api.AniSkipResult
import com.nuvio.tv.data.remote.api.AnimeSkipApi
import com.nuvio.tv.data.remote.api.ArmApi
import com.nuvio.tv.data.remote.api.ArmEntry
import com.nuvio.tv.data.remote.api.IntroDbApi
import com.nuvio.tv.data.remote.api.IntroDbSegment
import com.nuvio.tv.data.remote.api.IntroDbSegmentsResponse
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class SkipIntroRepositoryTest {
    private val introDbApi = mockk<IntroDbApi>()
    private val aniSkipApi = mockk<AniSkipApi>()
    private val animeSkipApi = mockk<AnimeSkipApi>(relaxed = true)
    private val armApi = mockk<ArmApi>()
    private val animeSkipSettings = mockk<AnimeSkipSettingsDataStore>()
    private val tmdbService = mockk<TmdbService>()

    private lateinit var repository: SkipIntroRepository

    @Before
    fun setUp() {
        every { animeSkipSettings.enabled } returns flowOf(false)
        every { animeSkipSettings.clientId } returns flowOf("")
        coEvery { introDbApi.getSegments(any(), any(), any()) } returns
            Response.success(IntroDbSegmentsResponse())
        coEvery { aniSkipApi.getSkipTimes(any(), any(), any(), any()) } returns
            Response.success(AniSkipResponse(found = false))
        coEvery { armApi.resolveImdbToAll(any(), any()) } returns Response.success(emptyList())
        coEvery { armApi.resolveMalToImdb(any(), any(), any()) } returns Response.success(ArmEntry())
        coEvery { armApi.resolveMalToAnilist(any(), any(), any()) } returns Response.success(ArmEntry())
        coEvery { armApi.resolveKitsuToMal(any(), any(), any()) } returns Response.success(ArmEntry())
        coEvery { armApi.resolveKitsuToImdb(any(), any(), any()) } returns Response.success(ArmEntry())
        coEvery { armApi.resolveKitsuToAnilist(any(), any(), any()) } returns Response.success(ArmEntry())
        coEvery { tmdbService.tmdbToImdb(any(), any()) } returns null

        repository = SkipIntroRepository(
            introDbApi = introDbApi,
            aniSkipApi = aniSkipApi,
            animeSkipApi = animeSkipApi,
            armApi = armApi,
            animeSkipSettingsDataStore = animeSkipSettings,
            tmdbService = tmdbService,
        )
    }

    @Test
    fun `tmdb episode resolves to imdb and preserves all introdb segments`() = runTest {
        coEvery { tmdbService.tmdbToImdb(95396, "series") } returns "tt11280740"
        coEvery { introDbApi.getSegments("tt11280740", 2, 4) } returns Response.success(
            IntroDbSegmentsResponse(
                intro = IntroDbSegment(startMs = 2_000, endMs = 62_000),
                recap = IntroDbSegment(startSec = 62.0, endSec = 95.0),
                outro = IntroDbSegment(startSec = 2_400.0, endSec = 2_460.0),
            )
        )

        val result = repository.getSkipIntervalsForContent(
            contentId = "tmdb:95396:2:4",
            contentType = "series",
            season = null,
            episode = null,
        )

        assertEquals(listOf("intro", "recap", "outro"), result.map { it.type })
        assertEquals(listOf("introdb", "introdb", "introdb"), result.map { it.provider })
        assertEquals(2.0, result.first().startTime, 0.001)
        coVerify(exactly = 1) { tmdbService.tmdbToImdb(95396, "series") }
    }

    @Test
    fun `introdb wins each category and aniskip fills missing categories`() = runTest {
        coEvery { introDbApi.getSegments("tt0903747", 1, 2) } returns Response.success(
            IntroDbSegmentsResponse(
                intro = IntroDbSegment(startSec = 3.0, endSec = 58.0),
            )
        )
        coEvery { armApi.resolveImdbToAll("tt0903747", any()) } returns Response.success(
            listOf(ArmEntry(myanimelist = 42))
        )
        coEvery { aniSkipApi.getSkipTimes("42", 2, any(), any()) } returns Response.success(
            AniSkipResponse(
                found = true,
                results = listOf(
                    AniSkipResult(AniSkipInterval(4.0, 59.0), "op"),
                    AniSkipResult(AniSkipInterval(0.0, 3.0), "recap"),
                    AniSkipResult(AniSkipInterval(1_300.0, 1_350.0), "ed"),
                ),
            )
        )

        val result = repository.getSkipIntervals("tt0903747", 1, 2)

        assertEquals(3, result.size)
        assertEquals("introdb", result.single { it.type == "intro" }.provider)
        assertEquals("aniskip", result.single { it.type == "recap" }.provider)
        assertEquals("aniskip", result.single { it.type == "ed" }.provider)
    }

    @Test
    fun `legacy imdb mal and kitsu identities remain accepted`() = runTest {
        repository.getSkipIntervalsForContent("tt0903747:3:5", "series", null, null)

        coEvery { armApi.resolveMalToImdb(malId = "55") } returns
            Response.success(ArmEntry(myanimelist = 55, imdb = "tt-mal"))
        coEvery { armApi.resolveImdbToAll("tt-mal", any()) } returns
            Response.success(listOf(ArmEntry(myanimelist = 55)))
        repository.getSkipIntervalsForContent("mal:55:7", "series", null, null)

        coEvery { armApi.resolveKitsuToImdb(kitsuId = "77") } returns
            Response.success(ArmEntry(kitsu = 77, imdb = "tt-kitsu"))
        coEvery { armApi.resolveKitsuToMal(kitsuId = "77") } returns
            Response.success(ArmEntry(kitsu = 77))
        coEvery { armApi.resolveImdbToAll("tt-kitsu", any()) } returns
            Response.success(listOf(ArmEntry(kitsu = 77)))
        repository.getSkipIntervalsForContent("kitsu:77:8", "series", null, null)

        coVerify { introDbApi.getSegments("tt0903747", 3, 5) }
        coVerify { introDbApi.getSegments("tt-mal", 1, 7) }
        coVerify { introDbApi.getSegments("tt-kitsu", 1, 8) }
    }

    @Test
    fun `invalid identities degrade to an empty result without provider calls`() = runTest {
        val result = repository.getSkipIntervalsForContent("tmdb:not-a-number", "series", 1, 1)

        assertTrue(result.isEmpty())
        coVerify(exactly = 0) { tmdbService.tmdbToImdb(any(), any()) }
        coVerify(exactly = 0) { introDbApi.getSegments(any(), any(), any()) }
    }
}
