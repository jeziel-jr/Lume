package com.nuvio.tv.core.tmdb

import java.util.concurrent.ThreadLocalRandom
import okhttp3.Interceptor
import okhttp3.Response

class TmdbRateLimitInterceptor internal constructor(
    private val maxRetries: Int = 2,
    private val sleepMillis: (Long) -> Unit = Thread::sleep,
    private val jitterMillis: () -> Long = { ThreadLocalRandom.current().nextLong(75L, 251L) },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var attempt = 0
        var response = chain.proceed(chain.request())
        while (response.code == 429 && attempt < maxRetries) {
            val retryAfterMillis = response.header("Retry-After")
                ?.trim()
                ?.toLongOrNull()
                ?.times(1_000L)
            response.close()
            val exponential = 1_000L shl attempt
            sleepMillis(((retryAfterMillis ?: exponential) + jitterMillis()).coerceAtMost(10_000L))
            attempt += 1
            response = chain.proceed(chain.request())
        }
        return response
    }
}
