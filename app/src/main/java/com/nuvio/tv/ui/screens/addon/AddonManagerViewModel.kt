package com.nuvio.tv.ui.screens.addon

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.R
import com.nuvio.tv.core.network.NetworkResult
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.domain.repository.AddonRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Manages the locally installed Stremio-style addons (install/remove/enable/disable/order).
 * The QR/web-config server, collections/catalog-order editing and remote-account sync that
 * the baseline manager hosted were removed with the cloud/collections cleanup and are not
 * part of this restored surface.
 */
@HiltViewModel
class AddonManagerViewModel @Inject constructor(
    private val addonRepository: AddonRepository,
    private val profileManager: ProfileManager,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddonManagerUiState())
    val uiState: StateFlow<AddonManagerUiState> = _uiState.asStateFlow()

    val isReadOnly: Boolean
        get() = AddonManagementAccess.isReadOnly(profileManager.activeProfile)

    init {
        observeInstalledAddons()
    }

    fun onInstallUrlChange(url: String) {
        _uiState.update { it.copy(installUrl = url, error = null) }
    }

    fun clearTransientMessage() {
        _uiState.update { it.copy(transientMessage = null, transientMessageIsError = false) }
    }

    fun installAddon() {
        val rawUrl = uiState.value.installUrl.trim()
        if (rawUrl.isBlank()) {
            val message = context.getString(R.string.addon_error_invalid_url)
            _uiState.update {
                it.copy(
                    error = message,
                    transientMessage = message,
                    transientMessageIsError = true
                )
            }
            return
        }

        val normalizedUrl = normalizeAddonUrl(rawUrl)
        if (normalizedUrl == null) {
            val message = context.getString(R.string.addon_error_invalid_scheme)
            _uiState.update {
                it.copy(
                    error = message,
                    transientMessage = message,
                    transientMessageIsError = true
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isInstalling = true, error = null, transientMessage = null) }

            when (val result = addonRepository.fetchAddon(normalizedUrl)) {
                is NetworkResult.Success -> {
                    addonRepository.addAddon(normalizedUrl)
                    val addonName = result.data.displayName.ifBlank { result.data.baseUrl }
                    _uiState.update {
                        it.copy(
                            isInstalling = false,
                            installUrl = "",
                            transientMessage = context.getString(R.string.addon_install_success, addonName),
                            transientMessageIsError = false
                        )
                    }
                }
                is NetworkResult.Error -> {
                    val message = result.message
                    _uiState.update {
                        it.copy(
                            isInstalling = false,
                            error = message,
                            transientMessage = message,
                            transientMessageIsError = true
                        )
                    }
                }
                NetworkResult.Loading -> {
                    _uiState.update { it.copy(isInstalling = true) }
                }
            }
        }
    }

    private fun normalizeAddonUrl(input: String): String? {
        var trimmed = input.trim()
        if (trimmed.startsWith("stremio://")) {
            trimmed = trimmed.replaceFirst("stremio://", "https://")
        }
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
            return null
        }

        val withoutManifest = if (trimmed.endsWith("/manifest.json")) {
            trimmed.removeSuffix("/manifest.json")
        } else {
            trimmed
        }

        return withoutManifest.trimEnd('/')
    }

    fun removeAddon(baseUrl: String) {
        viewModelScope.launch {
            addonRepository.removeAddon(baseUrl)
        }
    }

    fun setAddonEnabled(baseUrl: String, enabled: Boolean) {
        viewModelScope.launch {
            addonRepository.setAddonEnabled(baseUrl, enabled)
        }
    }

    fun moveAddonUp(baseUrl: String) {
        reorderAddon(baseUrl, -1)
    }

    fun moveAddonDown(baseUrl: String) {
        reorderAddon(baseUrl, 1)
    }

    private fun reorderAddon(baseUrl: String, direction: Int) {
        val current = _uiState.value.installedAddons
        val index = current.indexOfFirst { it.baseUrl == baseUrl }
        if (index == -1) return

        val newIndex = index + direction
        if (newIndex !in current.indices) return

        val reordered = current.toMutableList().apply {
            val item = removeAt(index)
            add(newIndex, item)
        }

        viewModelScope.launch {
            addonRepository.setAddonOrder(reordered.map { it.baseUrl })
        }
    }

    private fun observeInstalledAddons() {
        viewModelScope.launch {
            if (_uiState.value.installedAddons.isEmpty()) {
                _uiState.update { it.copy(isLoading = true) }
            }
            addonRepository.getInstalledAddons()
                .collect { addons ->
                    _uiState.update { state ->
                        state.copy(
                            installedAddons = addons,
                            isLoading = false,
                            error = null
                        )
                    }
                }
        }
    }
}
