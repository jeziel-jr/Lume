package com.nuvio.tv.data.xtream

import com.nuvio.tv.data.remote.api.XtreamApi
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

@Singleton
class XtreamApiFactory @Inject constructor(
    @Named("xtream") private val client: OkHttpClient,
    private val moshi: Moshi,
) {
    @Volatile private var cachedBaseUrl: String? = null
    @Volatile private var cachedApi: XtreamApi? = null

    @Synchronized
    fun apiFor(baseUrl: String): XtreamApi {
        val normalized = normalizeXtreamBaseUrl(baseUrl).let { if (it.endsWith('/')) it else "$it/" }
        if (cachedBaseUrl == normalized) cachedApi?.let { return it }
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(XtreamApi::class.java)
            .also {
                cachedBaseUrl = normalized
                cachedApi = it
            }
    }

    @Synchronized
    fun clear() {
        cachedBaseUrl = null
        cachedApi = null
    }
}
