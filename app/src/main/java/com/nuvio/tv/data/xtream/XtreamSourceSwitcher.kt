package com.nuvio.tv.data.xtream

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies an operator-driven endpoint rotation to every component that cached the old host.
 *
 * The switch is non-destructive: the catalog index and the persisted snapshot keep serving Home
 * while the new endpoint is downloaded and re-indexed, and they are replaced atomically when the
 * refresh succeeds. A dead endpoint therefore never leaves the user with an empty catalog.
 */
@Singleton
class XtreamSourceSwitcher @Inject constructor(
    private val credentialsStore: XtreamCredentialsStore,
    private val apiFactory: XtreamApiFactory,
    private val catalogRepository: XtreamCatalogRepository,
    private val availabilityStore: XtreamAvailabilityStore,
    private val playbackResolver: XtreamPlaybackResolver,
    private val serverHealthMonitor: XtreamServerHealthMonitor,
) {

    suspend fun switchTo(endpoint: String): Boolean {
        val normalized = normalizeXtreamBaseUrl(endpoint)
        if (normalized.isBlank()) return false
        val credentials = credentialsStore.current()
        if (credentials == null) return false
        if (credentials.baseUrl == normalized) return false

        // Credentials first: the catalog fingerprint and every URL builder read the store.
        credentialsStore.updateBaseUrl(normalized)
        apiFactory.clear()
        catalogRepository.switchEndpoint()
        availabilityStore.clear()
        playbackResolver.clearRuntimeCaches()
        serverHealthMonitor.clear()
        return true
    }
}
