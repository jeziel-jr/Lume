package com.nuvio.tv.data.xtream

import android.util.Log
import com.nuvio.tv.core.config.RemoteConfig
import com.nuvio.tv.core.config.RemoteConfigFetcher
import com.nuvio.tv.data.local.RemoteConfigStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Owns the provider endpoint the rest of the app uses.
 *
 * Precedence: a local manual override (hidden operator escape hatch) wins over the endpoint stored
 * with the account, which wins over the endpoint stored by the last rotation, which wins over the
 * bootstrap constant compiled into the APK. The resolver never leaves the app without an endpoint
 * and never touches username or password.
 *
 * Refresh triggers: cold start, a six-hour TTL while the process lives, a connectivity failure
 * reported by [XtreamServerHealthMonitor], and the manual action in Settings.
 */
@Singleton
class XtreamEndpointResolver @Inject constructor(
    private val store: RemoteConfigStore,
    private val fetcher: RemoteConfigFetcher,
    private val probe: XtreamEndpointProbe,
    private val switcher: XtreamSourceSwitcher,
    private val credentialsStore: XtreamCredentialsStore,
    private val serverHealthMonitor: XtreamServerHealthMonitor,
) {
    enum class RefreshReason { STARTUP, PERIODIC, FAILURE, MANUAL }

    enum class RefreshOutcome { APPLIED, UP_TO_DATE, FAILED }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private var started = false

    /** Reactive view of the operator override, for the settings surface. */
    val manualEndpoint: StateFlow<String?> = store.state
        .map { it.manualOverride }
        .stateIn(scope, SharingStarted.Eagerly, store.current().manualOverride)

    /** Endpoint to use right now; never suspends and always returns a usable value. */
    fun currentEndpoint(): String {
        val snapshot = store.current()
        return snapshot.manualOverride
            ?: credentialsStore.current()?.baseUrl
            ?: snapshot.activeEndpoint
            ?: BOOTSTRAP_ENDPOINT
    }

    fun manualOverride(): String? = store.current().manualOverride

    fun start() {
        if (started) return
        started = true
        scope.launch {
            refresh(RefreshReason.STARTUP)
            launch { observeConnectivityFailures() }
            launch { periodicRefreshLoop() }
        }
    }

    /**
     * Resolves the endpoint for the setup flow, bounded so a device without connectivity still
     * reaches the form with the last known endpoint.
     */
    suspend fun awaitEndpoint(timeoutMillis: Long = SETUP_RESOLVE_TIMEOUT_MILLIS): String {
        withTimeoutOrNull(timeoutMillis) { refresh(RefreshReason.MANUAL) }
        return currentEndpoint()
    }

    /** Fetches and applies the published configuration when the reason allows it. */
    suspend fun refresh(reason: RefreshReason): RefreshOutcome = refreshMutex.withLock {
        val snapshot = store.current()
        val now = System.currentTimeMillis()
        if (!shouldRefresh(reason, snapshot.fetchedAtMillis, now)) return RefreshOutcome.UP_TO_DATE

        val envelope = fetcher.fetch(cacheBuster = now.toString().takeIf { reason == RefreshReason.MANUAL })
        if (envelope == null) {
            Log.w(TAG, "remote_config_unavailable reason=$reason")
            return RefreshOutcome.FAILED
        }
        if (envelope.config.revision <= snapshot.revision) {
            Log.i(TAG, "remote_config_up_to_date revision=${envelope.config.revision}")
            return RefreshOutcome.UP_TO_DATE
        }

        store.saveDocument(
            payload = envelope.payload,
            signature = envelope.signature,
            revision = envelope.config.revision,
            fetchedAtMillis = now,
        )
        Log.i(TAG, "remote_config_applied revision=${envelope.config.revision} endpoints=${envelope.config.endpoints}")
        applyConfiguration(envelope.config, failureDriven = reason == RefreshReason.FAILURE)
        return RefreshOutcome.APPLIED
    }

    fun setManualOverride(endpoint: String?) {
        val normalized = endpoint?.takeIf { it.isNotBlank() }?.let(::normalizeXtreamBaseUrl)
        store.setManualOverride(normalized)
    }

    /** Drops the override and goes back to the published configuration. */
    suspend fun clearManualOverride() {
        if (store.current().manualOverride == null) return
        store.setManualOverride(null)
        refresh(RefreshReason.MANUAL)
    }

    private suspend fun applyConfiguration(config: RemoteConfig, failureDriven: Boolean) {
        if (store.current().manualOverride != null) return
        val endpoints = XtreamEndpointPolicy.sanitize(config.endpoints)
        if (endpoints.isEmpty()) return
        val current = currentEndpoint()
        val target = if (failureDriven) {
            XtreamEndpointPolicy.selectForFailure(endpoints, current) { probeEndpoint(it) }
        } else {
            XtreamEndpointPolicy.selectForConfig(endpoints, current) { probeEndpoint(it) }
        } ?: return
        Log.i(TAG, "endpoint_switch from=$current to=$target failureDriven=$failureDriven")
        store.setActiveEndpoint(target)
        switcher.switchTo(target)
    }

    private suspend fun probeEndpoint(endpoint: String): Boolean {
        val credentials = credentialsStore.current() ?: return probe.isReachable(endpoint)
        return probe.isAuthorized(credentials.copy(baseUrl = normalizeXtreamBaseUrl(endpoint)))
    }

    private suspend fun observeConnectivityFailures() {
        serverHealthMonitor.health.collect { health ->
            if (health.isConnectivityFailure()) refresh(RefreshReason.FAILURE)
        }
    }

    private suspend fun periodicRefreshLoop() {
        while (true) {
            delay(PERIODIC_TICK_MILLIS)
            refresh(RefreshReason.PERIODIC)
        }
    }

    private fun shouldRefresh(reason: RefreshReason, fetchedAtMillis: Long, nowMillis: Long): Boolean {
        val age = nowMillis - fetchedAtMillis
        return when (reason) {
            RefreshReason.MANUAL -> true
            RefreshReason.FAILURE -> age >= MIN_REFRESH_INTERVAL_MILLIS
            RefreshReason.STARTUP, RefreshReason.PERIODIC -> age >= CONFIG_TTL_MILLIS
        }
    }

    private fun XtreamServerHealth.isConnectivityFailure(): Boolean {
        if (account.state == XtreamHealthState.LOCAL_NETWORK_FAILURE) return true
        return listOf(movies, series).any { component ->
            component.state == XtreamHealthState.PROVIDER_FAILURE && component.reason in CONNECTIVITY_REASONS
        }
    }

    companion object {
        /** Used until a verified configuration or a stored endpoint exists. */
        const val BOOTSTRAP_ENDPOINT = "http://capone.icu"

        private const val TAG = "LumeEndpoint"

        private const val CONFIG_TTL_MILLIS = 6 * 60 * 60 * 1_000L
        private const val MIN_REFRESH_INTERVAL_MILLIS = 60 * 1_000L
        private const val PERIODIC_TICK_MILLIS = 30 * 60 * 1_000L
        private const val SETUP_RESOLVE_TIMEOUT_MILLIS = 2_500L

        private val CONNECTIVITY_REASONS = setOf(
            XtreamHealthReason.DNS_FAILURE,
            XtreamHealthReason.NETWORK_FAILURE,
            XtreamHealthReason.TIMEOUT,
        )
    }
}
