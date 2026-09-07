package com.nuvio.tv.data.xtream

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class XtreamRemoteDataSource @Inject constructor(
    private val credentialsStore: XtreamCredentialsStore,
    private val apiFactory: XtreamApiFactory,
) : XtreamDataSource {
    override suspend fun getVodStreams() = withApi { api, credentials ->
        api.getVodStreams(credentials.username, credentials.password)
    }

    override suspend fun getVodInfo(id: Int) = withApi { api, credentials ->
        api.getVodInfo(credentials.username, credentials.password, id)
    }

    override suspend fun getSeries() = withApi { api, credentials ->
        api.getSeries(credentials.username, credentials.password)
    }

    override suspend fun getSeriesInfo(id: Int) = withApi { api, credentials ->
        api.getSeriesInfo(credentials.username, credentials.password, id)
    }

    override suspend fun getVodCategories() = withApi { api, credentials ->
        api.getVodCategories(credentials.username, credentials.password)
    }

    override suspend fun getSeriesCategories() = withApi { api, credentials ->
        api.getSeriesCategories(credentials.username, credentials.password)
    }
    override suspend fun getLiveCategories() = withApi { api, credentials ->
        api.getLiveCategories(credentials.username, credentials.password)
    }

    override suspend fun getLiveStreams(categoryId: Int?) = withApi { api, credentials ->
        api.getLiveStreams(credentials.username, credentials.password, categoryId = categoryId)
    }

    override suspend fun getShortEpg(streamId: Int, limit: Int) = withApi { api, credentials ->
        api.getShortEpg(credentials.username, credentials.password, streamId, limit)
    }

    private suspend fun <T> withApi(
        block: suspend (com.nuvio.tv.data.remote.api.XtreamApi, XtreamCredentials) -> T,
    ): T {
        val credentials = credentialsStore.current() ?: error("Xtream is not configured")
        return block(apiFactory.apiFor(credentials.baseUrl), credentials)
    }
}
