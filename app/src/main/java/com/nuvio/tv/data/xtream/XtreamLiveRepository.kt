package com.nuvio.tv.data.xtream

import com.nuvio.tv.data.remote.api.XtreamLiveStream
import com.nuvio.tv.domain.model.EpgProgram
import com.nuvio.tv.domain.model.LiveChannel
import com.nuvio.tv.domain.model.LiveChannelCategory
import com.nuvio.tv.domain.model.LiveTvSnapshot
import com.nuvio.tv.domain.repository.LiveTvRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrl

internal const val LIVE_CACHE_TTL_MILLIS = 20L * 60L * 1000L

/**
 * One in-memory snapshot of live channels, refreshed at most every
 * [LIVE_CACHE_TTL_MILLIS]. A failed refresh keeps serving the stale snapshot.
 */
@Singleton
class XtreamLiveRepository internal constructor(
    private val dataSource: XtreamDataSource,
    private val credentialsProvider: () -> XtreamCredentials?,
    private val nowMillis: () -> Long,
    private val base64Decoder: (String) -> String? = ::decodeBase64Candidate
) : LiveTvRepository {

    @Inject
    constructor(
        dataSource: XtreamDataSource,
        credentialsStore: XtreamCredentialsStore
    ) : this(dataSource, { credentialsStore.current() }, { System.currentTimeMillis() })

    @Volatile
    private var cached: LiveTvSnapshot? = null

    @Volatile
    private var cachedAtMillis: Long = 0L

    override suspend fun loadSnapshot(): LiveTvSnapshot {
        val cachedSnapshot = cached
        if (cachedSnapshot != null && nowMillis() - cachedAtMillis < LIVE_CACHE_TTL_MILLIS) {
            return cachedSnapshot
        }
        val fetchedAt = nowMillis()
        val snapshot = try {
            coroutineScope {
                val categories = async { dataSource.getLiveCategories() }
                val streams = async { dataSource.getLiveStreams() }
                LiveTvSnapshot(
                    categories = categories.await()
                        .map { it.toDomain() }
                        .sortedBy { it.name.lowercase() },
                    channels = streams.await().map { it.toDomain() },
                    fetchedAtMillis = fetchedAt
                )
            }
        } catch (error: Exception) {
            cachedSnapshot ?: throw error
        }
        cached = snapshot
        cachedAtMillis = fetchedAt
        return snapshot
    }

    override suspend fun epg(streamId: Int, limit: Int): List<EpgProgram> {
        return runCatching {
            val response = dataSource.getShortEpg(streamId, limit)
            if (response.error != null) return@runCatching emptyList()
            response.epgListings.orEmpty().map { entry ->
                EpgProgram(
                    title = entry.title?.let { decodeEpgText(it, base64Decoder) }?.takeIf { it.isNotBlank() }
                        ?: entry.id.orEmpty(),
                    description = entry.description?.let { decodeEpgText(it, base64Decoder) },
                    startEpoch = entry.parsedStartEpoch?.takeIf { it > 0L },
                    endEpoch = entry.parsedStopEpoch?.takeIf { it > 0L }
                )
            }
        }.getOrDefault(emptyList())
    }

    override fun playbackUrl(channel: LiveChannel): String? {
        val credentials = credentialsProvider() ?: return null
        return runCatching {
            credentials.baseUrl.trimEnd('/').toHttpUrl().newBuilder()
                .addPathSegment("live")
                .addPathSegment(credentials.username)
                .addPathSegment(credentials.password)
                .addPathSegment("${channel.streamId}.m3u8")
                .build()
                .toString()
        }.getOrNull()
    }

    private fun XtreamLiveStream.toDomain(): LiveChannel = LiveChannel(
        streamId = parsedStreamId,
        name = name?.takeIf { it.isNotBlank() } ?: "Canal $parsedStreamId",
        iconUrl = streamIcon?.takeIf { it.isNotBlank() },
        epgChannelId = epgChannelId?.takeIf { it.isNotBlank() },
        categoryId = parsedCategoryId,
        number = parsedNumber
    )

    private fun com.nuvio.tv.data.remote.api.XtreamLiveCategory.toDomain(): LiveChannelCategory {
        val name = categoryName?.trim()?.takeIf { it.isNotBlank() } ?: "Categoria $id"
        return LiveChannelCategory(
            id = id,
            name = name,
            isAdult = ADULT_PATTERN.containsMatchIn(name)
        )
    }

    private companion object {
        // Covers pt/en adult labels: "❌Adultos❌", "❌Adultos OnlyFans(+18)❌", "Adult", "18+".
        val ADULT_PATTERN = Regex("(?i)(adulto|adult|onlyfans|18\\+|\\+18)")
    }
}
