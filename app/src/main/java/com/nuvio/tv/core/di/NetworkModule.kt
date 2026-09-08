package com.nuvio.tv.core.di

import android.content.Context
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.LocaleCache
import com.nuvio.tv.core.network.IPv4FirstDns
import com.nuvio.tv.core.tmdb.TmdbRateLimitInterceptor
import com.nuvio.tv.data.remote.api.AniSkipApi
import com.nuvio.tv.data.remote.api.AddonApi
import com.nuvio.tv.data.remote.api.AnimeSkipApi
import com.nuvio.tv.data.remote.api.ArmApi
import com.nuvio.tv.data.remote.api.GitHubReleaseApi
import com.nuvio.tv.data.remote.api.IntroDbApi
import com.nuvio.tv.data.remote.api.TmdbApi
import com.nuvio.tv.data.remote.api.TrailerApi
import com.nuvio.tv.data.xtream.XtreamCatalogRepository
import com.nuvio.tv.data.xtream.XtreamCredentialsStore
import com.nuvio.tv.data.xtream.XtreamDataSource
import com.nuvio.tv.data.xtream.XtreamPlaybackResolver
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

/**
 * Builds the value sent in the `Accept-Language` request header so remote
 * services can serve localized payloads (catalog names, descriptions, etc.).
 * Falls back to the system locale when the user has not overridden the app
 * language. The primary tag is given the highest q-weight; English is
 * appended at q=0.7 as a sensible fallback for services that don't support
 * the requested locale.
 */
private fun buildAcceptLanguageHeader(): String {
    val explicit = LocaleCache.localeTag
        .takeIf { it.isNotBlank() && it != LocaleCache.UNSET }
    val primaryTag = (explicit ?: java.util.Locale.getDefault().toLanguageTag())
        .takeIf { it.isNotBlank() && it != "und" }
        ?: "en"
    return if (primaryTag.equals("en", ignoreCase = true) ||
        primaryTag.startsWith("en-", ignoreCase = true)
    ) {
        primaryTag
    } else {
        "$primaryTag,en;q=0.7"
    }
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMoshi(): Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Provides
    @Singleton
    @Named("xtream")
    fun provideXtreamOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .dns(IPv4FirstDns())
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideXtreamPlaybackResolver(
        dataSource: XtreamDataSource,
        catalogRepository: XtreamCatalogRepository,
        availabilityStore: com.nuvio.tv.data.xtream.XtreamAvailabilityStore,
        credentialsStore: XtreamCredentialsStore,
    ): XtreamPlaybackResolver =
        XtreamPlaybackResolver(
            credentialsProvider = { credentialsStore.current() ?: error("Xtream is not configured") },
            dataSource = dataSource,
            catalogRepository = catalogRepository,
            availabilityStore = availabilityStore,
        )

    @Provides
    @Singleton
    fun provideOkHttpClient(@ApplicationContext context: Context): OkHttpClient {
        val trustAllManager = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(trustAllManager), SecureRandom())
        }
        return OkHttpClient.Builder()
            .dns(IPv4FirstDns())
            .sslSocketFactory(sslContext.socketFactory, trustAllManager)
            .hostnameVerifier { _, _ -> true }
            .cache(Cache(File(context.cacheDir, "http_cache"), 50L * 1024 * 1024)) // 50 MB disk cache
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val version = BuildConfig.VERSION_NAME.ifBlank { "dev" }
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Nuvio/$version")
                    .header("Accept-Language", buildAcceptLanguageHeader())
                    .build()
                chain.proceed(request)
            }
            // Prevent OkHttp from caching error responses (4xx/5xx).
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (!response.isSuccessful) {
                    response.newBuilder()
                        .header("Cache-Control", "no-store")
                        .build()
                } else {
                    response
                }
            }
            .addInterceptor(HttpLoggingInterceptor().apply {
                level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
                        else HttpLoggingInterceptor.Level.NONE
            })
            .build()
    }

    @Provides
    @Singleton
    @Named("tmdb")
    fun provideTmdbOkHttpClient(okHttpClient: OkHttpClient): OkHttpClient =
        okHttpClient.newBuilder()
            .dispatcher(Dispatcher().apply {
                maxRequests = 4
                maxRequestsPerHost = 4
            })
            .addInterceptor(TmdbRateLimitInterceptor())
            .build()

    @Provides
    @Singleton
    @Named("tmdb")
    fun provideTmdbRetrofit(@Named("tmdb") okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.themoviedb.org/3/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideTmdbApi(@Named("tmdb") retrofit: Retrofit): TmdbApi =
        retrofit.create(TmdbApi::class.java)

    // --- Skip Intro APIs ---

    @Provides
    @Singleton
    @Named("introDb")
    fun provideIntroDbRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.INTRODB_API_URL.ifEmpty { "https://api.introdb.app/" })
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideIntroDbApi(@Named("introDb") retrofit: Retrofit): IntroDbApi =
        retrofit.create(IntroDbApi::class.java)

    @Provides
    @Singleton
    @Named("aniSkip")
    fun provideAniSkipRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.aniskip.com/v2/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideAniSkipApi(@Named("aniSkip") retrofit: Retrofit): AniSkipApi =
        retrofit.create(AniSkipApi::class.java)

    @Provides
    @Singleton
    @Named("arm")
    fun provideArmRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://arm.haglund.dev/api/v2/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideArmApi(@Named("arm") retrofit: Retrofit): ArmApi =
        retrofit.create(ArmApi::class.java)

    @Provides
    @Singleton
    @Named("animeSkipGql")
    fun provideAnimeSkipGqlRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.anime-skip.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideAnimeSkipApi(@Named("animeSkipGql") retrofit: Retrofit): AnimeSkipApi =
        retrofit.create(AnimeSkipApi::class.java)

    // --- GitHub Releases API (in-app updates) ---

    @Provides
    @Singleton
    @Named("github")
    fun provideGitHubRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideGitHubReleaseApi(@Named("github") retrofit: Retrofit): GitHubReleaseApi =
        retrofit.create(GitHubReleaseApi::class.java)

    // --- Trailer API ---

    @Provides
    @Singleton
    @Named("trailer")
    fun provideTrailerRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl(BuildConfig.TRAILER_API_URL.ifEmpty { "https://localhost/" })
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideTrailerApi(@Named("trailer") retrofit: Retrofit): TrailerApi =
        retrofit.create(TrailerApi::class.java)

    // --- Addon (Stremio manifest/catalog) API — dynamic @Url endpoints on a placeholder base ---

    @Provides
    @Singleton
    fun provideAddonRetrofit(okHttpClient: OkHttpClient, moshi: Moshi): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://placeholder.nuvio.tv/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()

    @Provides
    @Singleton
    fun provideAddonApi(retrofit: Retrofit): AddonApi =
        retrofit.create(AddonApi::class.java)
}
