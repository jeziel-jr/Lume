package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.data.xtream.XtreamAccountInfo
import com.nuvio.tv.data.xtream.XtreamApiFactory
import com.nuvio.tv.data.xtream.XtreamCredentials
import com.nuvio.tv.data.xtream.XtreamCredentialsStore
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
)

@HiltViewModel
class XtreamProfileViewModel @Inject constructor(
    private val credentialsStore: XtreamCredentialsStore,
    private val apiFactory: XtreamApiFactory,
    private val serverHealthMonitor: XtreamServerHealthMonitor,
) : ViewModel() {
    private val _uiState = MutableStateFlow(XtreamProfileUiState())
    val uiState: StateFlow<XtreamProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(credentialsStore.credentials, credentialsStore.accountInfo) { credentials, accountInfo ->
                credentials to accountInfo
            }.collect { (credentials, accountInfo) ->
                _uiState.update { it.copy(credentials = credentials, accountInfo = accountInfo) }
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

    fun signOut() {
        serverHealthMonitor.clear()
        credentialsStore.clear()
    }
}
