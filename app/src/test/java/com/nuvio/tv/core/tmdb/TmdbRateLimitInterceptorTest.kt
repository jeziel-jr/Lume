package com.nuvio.tv.core.tmdb

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Test

class TmdbRateLimitInterceptorTest {
    @Test
    fun `429 retries twice and respects retry-after`() {
        val chain = mockk<Interceptor.Chain>()
        val request = mockk<Request>()
        val first = rateLimitedResponse(retryAfter = "2")
        val second = rateLimitedResponse()
        val success = response(200)
        every { chain.request() } returns request
        every { chain.proceed(request) } returnsMany listOf(first, second, success)
        val sleeps = mutableListOf<Long>()

        val result = TmdbRateLimitInterceptor(
            sleepMillis = sleeps::add,
            jitterMillis = { 0L },
        ).intercept(chain)

        assertEquals(success, result)
        assertEquals(listOf(2_000L, 2_000L), sleeps)
        verify(exactly = 3) { chain.proceed(request) }
        verify(exactly = 1) { first.close() }
        verify(exactly = 1) { second.close() }
    }

    @Test
    fun `non-429 response is not retried`() {
        val chain = mockk<Interceptor.Chain>()
        val request = mockk<Request>()
        val unauthorized = response(401)
        every { chain.request() } returns request
        every { chain.proceed(request) } returns unauthorized

        val result = TmdbRateLimitInterceptor(
            sleepMillis = {},
            jitterMillis = { 0L },
        ).intercept(chain)

        assertEquals(unauthorized, result)
        verify(exactly = 1) { chain.proceed(request) }
    }

    private fun rateLimitedResponse(retryAfter: String? = null): Response = response(429, retryAfter)

    private fun response(code: Int, retryAfter: String? = null): Response = mockk(relaxed = true) {
        every { this@mockk.code } returns code
        every { header("Retry-After") } returns retryAfter
    }
}
