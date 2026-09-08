package com.nuvio.tv.data.xtream

import com.nuvio.tv.BuildConfig
import com.nuvio.tv.data.remote.api.TmdbApi
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Session cache of TMDB alternative titles for grid classification.
 *
 * The details screen resolves availability with the enriched TMDB identity
 * (localized, original, and all alternative titles), while grid badges used to
 * classify only with the title the card already carries. Provider catalogs often
 * index a movie or series under a regional alternative title (for example
 * "Ataque dos Titãs" or "Breaking Bad: A Química do Mal"), so a card that missed
 * the conservative local match showed a false `Indisponível` badge that only
 * disappeared after the user entered details. This lookup gives classification
 * the same alternative-title pool the details path uses, fetched once per TMDB
 * identity and cached for the session.
 */
@Singleton
class XtreamAlternativeTitleLookup @Inject constructor(
    private val tmdbApi: TmdbApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val titlesByKey = ConcurrentHashMap<String, List<String>>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val failedAtMillis = ConcurrentHashMap<String, Long>()
    private val _revision = MutableStateFlow(0L)
    val revision: StateFlow<Long> = _revision.asStateFlow()

    /** Titles already resolved for this identity, or null while unknown. */
    fun cachedTitles(tmdbId: Int, isSeries: Boolean): List<String>? =
        titlesByKey[key(tmdbId, isSeries)]

    /**
     * Ensures a fetch is scheduled for this identity. Fetches run once per
     * session; failures are retried after a backoff on later requests. A
     * successful fetch bumps [revision] so trackers reclassify waiting items.
     */
    fun requestTitles(tmdbId: Int, isSeries: Boolean) {
        val k = key(tmdbId, isSeries)
        if (titlesByKey.containsKey(k)) return
        if (k in inFlight) return
        val retryAt = failedAtMillis[k] ?: 0L
        if (System.currentTimeMillis() - retryAt < RETRY_FAILED_AFTER_MILLIS) return
        if (!inFlight.add(k)) return
        scope.launch {
            try {
                titlesByKey[k] = fetchTitles(tmdbId, isSeries)
                failedAtMillis.remove(k)
                _revision.value += 1L
            } catch (e: Exception) {
                failedAtMillis[k] = System.currentTimeMillis()
            } finally {
                inFlight.remove(k)
            }
        }
    }

    private suspend fun fetchTitles(tmdbId: Int, isSeries: Boolean): List<String> {
        val response = if (isSeries) {
            tmdbApi.getTvAlternativeTitles(tmdbId, BuildConfig.TMDB_API_KEY)
        } else {
            tmdbApi.getMovieAlternativeTitles(tmdbId, BuildConfig.TMDB_API_KEY)
        }
        // 404 means this identity has no alternative-title resource: an empty
        // result, not a retryable failure.
        if (response.code() == 404) return emptyList()
        if (!response.isSuccessful) throw IOException("TMDB alternative titles HTTP ${response.code()}")
        val body = response.body() ?: return emptyList()
        return (if (isSeries) body.tvTitles else body.movieTitles).orEmpty()
            .mapNotNull { it.title?.trim()?.takeIf(String::isNotBlank) }
            .distinct()
    }

    private fun key(tmdbId: Int, isSeries: Boolean): String =
        (if (isSeries) "tv:" else "movie:") + tmdbId

    private companion object {
        const val RETRY_FAILED_AFTER_MILLIS = 60_000L
    }
}
