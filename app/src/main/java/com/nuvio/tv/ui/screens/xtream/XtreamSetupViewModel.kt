package com.nuvio.tv.ui.screens.xtream

import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.R
import com.nuvio.tv.core.qr.QrCodeGenerator
import com.nuvio.tv.core.server.DeviceIpAddress
import com.nuvio.tv.core.server.XtreamSetupServer
import com.nuvio.tv.data.xtream.XtreamAccountInfo
import com.nuvio.tv.data.xtream.XtreamApiFactory
import com.nuvio.tv.data.xtream.XtreamAvailabilityStore
import com.nuvio.tv.data.xtream.XtreamCatalogRepository
import com.nuvio.tv.data.xtream.XtreamCredentials
import com.nuvio.tv.data.xtream.XtreamCredentialsStore
import com.nuvio.tv.data.xtream.XtreamEndpointResolver
import com.nuvio.tv.data.xtream.XtreamPlaybackResolver
import com.nuvio.tv.data.xtream.XtreamServerHealthMonitor
import com.nuvio.tv.data.xtream.normalizeXtreamBaseUrl
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

data class XtreamSetupUiState(
    val qrCode: Bitmap? = null,
    val serverUrl: String? = null,
    /** Provider endpoint defined for the app, shown read-only to the user. */
    val serverEndpoint: String = "",
    val resolvingEndpoint: Boolean = false,
    val error: String? = null,
    val pendingId: String? = null,
    val pendingCredentials: XtreamCredentials? = null,
    val pendingAccountInfo: XtreamAccountInfo? = null,
    val validating: Boolean = false,
    val applied: Boolean = false,
    /** Operator escape hatch: reveals the editable endpoint field. */
    val advancedServer: Boolean = false,
    /** Endpoint typed in the escape hatch, applied only when the user confirms on the TV. */
    val pendingManualEndpoint: String? = null,
)

@HiltViewModel
class XtreamSetupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val credentialsStore: XtreamCredentialsStore,
    private val apiFactory: XtreamApiFactory,
    private val catalogRepository: XtreamCatalogRepository,
    private val availabilityStore: XtreamAvailabilityStore,
    private val playbackResolver: XtreamPlaybackResolver,
    private val serverHealthMonitor: XtreamServerHealthMonitor,
    private val endpointResolver: XtreamEndpointResolver,
) : ViewModel() {
    private val _uiState = MutableStateFlow(XtreamSetupUiState())
    val uiState: StateFlow<XtreamSetupUiState> = _uiState.asStateFlow()
    private var server: XtreamSetupServer? = null

    init {
        start()
    }

    fun start() {
        stopServer()
        _uiState.update {
            it.copy(
                resolvingEndpoint = true,
                error = null,
                advancedServer = it.advancedServer || endpointResolver.manualOverride() != null,
            )
        }
        viewModelScope.launch {
            // Bounded: a device without connectivity still reaches the form with the last known endpoint.
            val endpoint = endpointResolver.awaitEndpoint()
            val ip = DeviceIpAddress.get(context)
            if (ip == null) {
                _uiState.update {
                    it.copy(
                        resolvingEndpoint = false,
                        error = context.getString(R.string.error_network_required),
                    )
                }
                return@launch
            }
            val started = XtreamSetupServer.startOnAvailablePort(
                context = context,
                endpointProvider = endpointResolver::currentEndpoint,
                onCredentialsProposed = ::validate,
            )
            if (started == null) {
                _uiState.update {
                    it.copy(
                        resolvingEndpoint = false,
                        error = context.getString(R.string.error_server_ports_unavailable),
                    )
                }
                return@launch
            }
            server = started
            val url = "http://$ip:${started.listeningPort}/#${started.qrFragment}"
            _uiState.update {
                it.copy(
                    qrCode = QrCodeGenerator.generate(url, 512),
                    serverUrl = "http://$ip:${started.listeningPort}",
                    serverEndpoint = endpoint,
                    resolvingEndpoint = false,
                )
            }
            delay(10 * 60 * 1_000L)
            if (_uiState.value.applied) return@launch
            stopServer()
            _uiState.update {
                it.copy(qrCode = null, error = context.getString(R.string.xtream_setup_expired))
            }
        }
    }

    /** Reveals the editable endpoint field for the operator; the user never sees this path. */
    fun revealAdvancedServer() {
        if (_uiState.value.advancedServer) return
        _uiState.update { it.copy(advancedServer = true) }
    }

    private fun validate(id: String, credentials: XtreamCredentials) {
        _uiState.update { it.copy(validating = true, error = null, pendingId = id) }
        viewModelScope.launch {
            val userInfo = runCatching {
                withTimeout(20_000L) {
                    apiFactory.apiFor(credentials.baseUrl)
                        .authenticate(credentials.username, credentials.password)
                        .userInfo
                }
            }.getOrNull()
            if (userInfo?.isAuthorized == true) {
                server?.updateStatus(id, XtreamSetupServer.Status.AWAITING_CONFIRMATION)
                _uiState.update {
                    it.copy(
                        validating = false,
                        pendingCredentials = credentials,
                        pendingAccountInfo = XtreamAccountInfo(
                            username = userInfo.username?.takeIf(String::isNotBlank) ?: credentials.username,
                            status = userInfo.status,
                            expirationEpochSeconds = userInfo.expirationEpochSeconds,
                            activeConnections = userInfo.activeConnectionCount,
                            maxConnections = userInfo.maximumConnectionCount,
                        ),
                        error = null,
                    )
                }
            } else {
                val message = context.getString(R.string.xtream_setup_invalid_credentials)
                server?.updateStatus(id, XtreamSetupServer.Status.INVALID, message)
                _uiState.update { it.copy(validating = false, pendingId = null, error = message) }
            }
        }
    }

    fun submitManual(username: String, password: String, endpoint: String? = null) {
        val state = _uiState.value
        if (username.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(error = context.getString(R.string.xtream_setup_missing_fields)) }
            return
        }
        val resolvedEndpoint = endpoint
            ?.takeIf { it.isNotBlank() && state.advancedServer }
            ?.let(::normalizeXtreamBaseUrl)
            ?: endpointResolver.currentEndpoint()
        val credentials = XtreamCredentials(resolvedEndpoint, username, password).normalized()
        require(credentials.isComplete) { "Xtream setup produced incomplete credentials" }
        _uiState.update {
            it.copy(
                pendingManualEndpoint = resolvedEndpoint.takeIf { state.advancedServer },
                serverEndpoint = resolvedEndpoint,
            )
        }
        validate(UUID.randomUUID().toString(), credentials)
    }

    fun confirm() {
        val state = _uiState.value
        val id = state.pendingId ?: return
        val credentials = state.pendingCredentials ?: return
        val accountInfo = state.pendingAccountInfo
        val previous = credentialsStore.current()
        viewModelScope.launch {
            server?.updateStatus(id, XtreamSetupServer.Status.APPLIED)
            delay(900L)
            credentialsStore.save(credentials, accountInfo)
            state.pendingManualEndpoint?.let(endpointResolver::setManualOverride)
            serverHealthMonitor.clear()
            apiFactory.clear()
            playbackResolver.clearRuntimeCaches()
            val sourceChanged = shouldClearXtreamCache(previous, credentials)
            if (sourceChanged) availabilityStore.clear()
            catalogRepository.invalidateForCredentialsChange(clearPersistentCache = sourceChanged)
            _uiState.update {
                it.copy(
                    applied = true,
                    pendingCredentials = null,
                    pendingAccountInfo = null,
                    pendingManualEndpoint = null,
                    validating = false,
                )
            }
            stopServer()
        }
    }

    fun reject() {
        _uiState.value.pendingId?.let { server?.updateStatus(it, XtreamSetupServer.Status.REJECTED) }
        _uiState.update {
            it.copy(
                pendingId = null,
                pendingCredentials = null,
                pendingAccountInfo = null,
                pendingManualEndpoint = null,
                validating = false,
            )
        }
    }

    private fun stopServer() {
        server?.stop()
        server = null
    }

    override fun onCleared() {
        stopServer()
        super.onCleared()
    }
}

internal fun shouldClearXtreamCache(
    previous: XtreamCredentials?,
    next: XtreamCredentials,
): Boolean = previous?.let {
    it.baseUrl != next.baseUrl || it.username != next.username
} ?: false
