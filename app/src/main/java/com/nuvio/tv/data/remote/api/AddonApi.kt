package com.nuvio.tv.data.remote.api

import com.nuvio.tv.data.remote.dto.AddonManifestDto
import com.nuvio.tv.data.remote.dto.CatalogResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Dynamic-URL client for Stremio-style addons (manifest + catalog resources).
 * The manifest/catalog/stream endpoints removed during the cleanup are not part of this
 * restored surface (catalog-driven Discover + addon management only).
 */
interface AddonApi {

    @GET
    suspend fun getManifest(@Url manifestUrl: String): Response<AddonManifestDto>

    @GET
    suspend fun getCatalog(@Url catalogUrl: String): Response<CatalogResponseDto>
}
