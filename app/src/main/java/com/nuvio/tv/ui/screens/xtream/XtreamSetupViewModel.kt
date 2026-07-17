package com.nuvio.tv.ui.screens.xtream

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.R
import com.nuvio.tv.core.qr.QrCodeGenerator
import com.nuvio.tv.core.server.DeviceIpAddress
import com.nuvio.tv.core.server.XtreamSetupServer
import com.nuvio.tv.data.xtream.XtreamApiFactory
import com.nuvio.tv.data.xtream.XtreamAccountInfo
import com.nuvio.tv.data.xtream.XtreamAvailabilityStore
import com.nuvio.tv.data.xtream.XtreamCatalogRepository
import com.nuvio.tv.data.xtream.XtreamCredentials
import com.nuvio.tv.data.xtream.XtreamCredentialsStore
import com.nuvio.tv.data.xtream.XtreamPlaybackResolver
import com.nuvio.tv.data.xtream.XtreamServerHealthMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

data class XtreamSetupUiState(
    val qrCode: android.graphics.Bitmap? = null,
    val serverUrl: String? = null,
    val error: String? = null,
    val pendingId: String? = null,
    val pendingCredentials: XtreamCredentials? = null,
    val pendingAccountInfo: XtreamAccountInfo? = null,
    val validating: Boolean = false,
    val applied: Boolean = false,
    val defaultBaseUrl: String = "",
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(XtreamSetupUiState())
    val uiState: StateFlow<XtreamSetupUiState> = _uiState.asStateFlow()
    private var server: XtreamSetupServer? = null

    init {
        start()
    }

    fun start() {
        stopServer()
        val ip = DeviceIpAddress.get(context)
        if (ip == null) {
            _uiState.value = XtreamSetupUiState(error = context.getString(R.string.error_network_required))
            return
        }
        val currentDefault = credentialsStore.current()?.baseUrl ?: credentialsStore.defaultBaseUrl
        val started = XtreamSetupServer.startOnAvailablePort(
            context = context,
            defaultBaseUrl = currentDefault,
            onCredentialsProposed = ::validate,
        )
        if (started == null) {
            _uiState.value = XtreamSetupUiState(error = context.getString(R.string.error_server_ports_unavailable))
            return
        }
        server = started
        val url = "http://$ip:${started.listeningPort}/#${started.qrFragment}"
        _uiState.value = XtreamSetupUiState(
            qrCode = QrCodeGenerator.generate(url, 512),
            serverUrl = "http://$ip:${started.listeningPort}",
            defaultBaseUrl = currentDefault,
        )
        viewModelScope.launch {
            delay(10 * 60 * 1_000L)
            if (_uiState.value.applied) return@launch
            stopServer()
            _uiState.update { it.copy(qrCode = null, error = context.getString(R.string.xtream_setup_expired)) }
        }
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

    fun submitManual(baseUrl: String, username: String, password: String) {
        val credentials = XtreamCredentials(baseUrl, username, password).normalized()
        if (!credentials.isComplete) {
            _uiState.update { it.copy(error = context.getString(R.string.xtream_setup_missing_fields)) }
            return
        }
        validate(UUID.randomUUID().toString(), credentials)
    }

    fun confirm() {
        val id = _uiState.value.pendingId ?: return
        val credentials = _uiState.value.pendingCredentials ?: return
        val accountInfo = _uiState.value.pendingAccountInfo
        val previous = credentialsStore.current()
        viewModelScope.launch {
            server?.updateStatus(id, XtreamSetupServer.Status.APPLIED)
            delay(900L)
            credentialsStore.save(credentials, accountInfo)
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
