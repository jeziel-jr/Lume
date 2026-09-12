package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.R
import com.nuvio.tv.data.xtream.XtreamAccountInfo
import com.nuvio.tv.data.xtream.XtreamApiFactory
import com.nuvio.tv.data.xtream.XtreamCredentials
import com.nuvio.tv.data.xtream.XtreamCredentialsStore
import com.nuvio.tv.data.xtream.XtreamEndpointResolver
import com.nuvio.tv.data.xtream.XtreamServerHealth
import com.nuvio.tv.data.xtream.XtreamServerHealthMonitor
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class XtreamProfileUiState(
    val credentials: XtreamCredentials? = null,
    val accountInfo: XtreamAccountInfo? = null,
    val refreshing: Boolean = false,
    val error: Boolean = false,
    val serverHealth: XtreamServerHealth = XtreamServerHealth(),
    val endpointRefreshing: Boolean = false,
    val endpointMessage: Int? = null,
    /** Set when the operator escape hatch pinned a server on this device. */
    val manualServer: String? = null,
)

@HiltViewModel
class XtreamProfileViewModel @Inject constructor(
    private val credentialsStore: XtreamCredentialsStore,
    private val apiFactory: XtreamApiFactory,
    private val serverHealthMonitor: XtreamServerHealthMonitor,
    private val endpointResolver: XtreamEndpointResolver,
) : ViewModel() {
    private val _uiState = MutableStateFlow(XtreamProfileUiState())
    val uiState: StateFlow<XtreamProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                credentialsStore.credentials,
                credentialsStore.accountInfo,
                endpointResolver.manualEndpoint,
            ) { credentials, accountInfo, manualServer ->
                Triple(credentials, accountInfo, manualServer)
            }.collect { (credentials, accountInfo, manualServer) ->
                _uiState.update {
                    it.copy(credentials = credentials, accountInfo = accountInfo, manualServer = manualServer)
                }
            }
        }
        viewModelScope.launch {
            serverHealthMonitor.health.collect { health ->
                _uiState.update { it.copy(serverHealth = health) }
            }
        }
        refresh()
    }

    fun refresh() {
        val credentials = credentialsStore.current() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(refreshing = true, error = false) }
            val userInfo = runCatching {
                apiFactory.apiFor(credentials.baseUrl)
                    .authenticate(credentials.username, credentials.password)
                    .userInfo
            }.getOrNull()
            if (userInfo?.isAuthorized == true) {
                credentialsStore.updateAccountInfo(
                    XtreamAccountInfo(
                        username = userInfo.username?.takeIf(String::isNotBlank) ?: credentials.username,
                        status = userInfo.status,
                        expirationEpochSeconds = userInfo.expirationEpochSeconds,
                        activeConnections = userInfo.activeConnectionCount,
                        maxConnections = userInfo.maximumConnectionCount,
                    ),
                )
                serverHealthMonitor.refresh(accountAuthorized = true)
                _uiState.update { it.copy(refreshing = false, error = false) }
            } else {
                serverHealthMonitor.refresh()
                _uiState.update { it.copy(refreshing = false, error = true) }
            }
        }
    }

    /** Fetches the operator-published server address on demand, bypassing the CDN cache. */
    fun refreshEndpoint() {
        viewModelScope.launch {
            _uiState.update { it.copy(endpointRefreshing = true, endpointMessage = null) }
            val outcome = endpointResolver.refresh(XtreamEndpointResolver.RefreshReason.MANUAL)
            val message = when (outcome) {
                XtreamEndpointResolver.RefreshOutcome.APPLIED -> R.string.xtream_profile_endpoint_updated
                XtreamEndpointResolver.RefreshOutcome.UP_TO_DATE -> R.string.xtream_profile_endpoint_unchanged
                XtreamEndpointResolver.RefreshOutcome.FAILED -> R.string.xtream_profile_endpoint_failed
            }
            _uiState.update { it.copy(endpointRefreshing = false, endpointMessage = message) }
        }
    }

    /** Drops the operator override and returns this device to the published endpoint. */
    fun resetManualServer() {
        viewModelScope.launch {
            endpointResolver.clearManualOverride()
            _uiState.update { it.copy(endpointMessage = R.string.xtream_profile_endpoint_updated) }
        }
    }

    fun signOut() {
        serverHealthMonitor.clear()
        credentialsStore.clear()
    }
}
