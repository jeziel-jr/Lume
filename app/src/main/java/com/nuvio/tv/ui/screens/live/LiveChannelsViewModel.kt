package com.nuvio.tv.ui.screens.live

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.R
import com.nuvio.tv.data.local.ParentalControlDataStore
import com.nuvio.tv.data.local.ParentalControlSession
import com.nuvio.tv.domain.model.LiveChannel
import com.nuvio.tv.domain.model.LiveChannelCategory
import com.nuvio.tv.domain.model.LiveTvSnapshot
import com.nuvio.tv.domain.repository.LiveTvRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LiveChannelsUiState(
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val adultContentHidden: Boolean = true,
    val adultUnlocked: Boolean = false,
    val categories: List<LiveChannelCategory> = emptyList(),
    val selectedCategoryId: Int? = null,
    val channels: List<LiveChannel> = emptyList(),
    val pinPrompt: Boolean = false,
    @StringRes val pinErrorRes: Int? = null
)

@HiltViewModel
class LiveChannelsViewModel @Inject constructor(
    private val repository: LiveTvRepository,
    private val parentalDataStore: ParentalControlDataStore,
    private val session: ParentalControlSession
) : ViewModel() {

    private val _state = MutableStateFlow(LiveChannelsUiState())
    val state: StateFlow<LiveChannelsUiState> = _state.asStateFlow()

    private var snapshot: LiveTvSnapshot? = null
    private var selectedCategory: LiveChannelCategory? = null

    init {
        viewModelScope.launch {
            parentalDataStore.settings.collect { settings ->
                val hiddenChanged = settings.adultContentHidden != _state.value.adultContentHidden
                _state.update {
                    it.copy(
                        adultContentHidden = settings.adultContentHidden,
                        adultUnlocked = session.adultUnlocked
                    )
                }
                if (hiddenChanged) {
                    rebuildCategories()
                }
            }
        }
        load()
    }

    fun load() {
        _state.update { it.copy(isLoading = true, loadError = null) }
        viewModelScope.launch {
            try {
                snapshot = repository.loadSnapshot()
                _state.update { it.copy(isLoading = false, loadError = null) }
                rebuildCategories()
                refreshChannels()
            } catch (error: Exception) {
                _state.update { it.copy(isLoading = false, loadError = error.message) }
            }
        }
    }

    fun onCategoryFocused(category: LiveChannelCategory?) {
        selectedCategory = category
        _state.update { it.copy(selectedCategoryId = category?.id) }
        refreshChannels()
    }

    fun onCategoryClicked(category: LiveChannelCategory) {
        if (category.isAdult && !_state.value.adultUnlocked) {
            _state.update { it.copy(pinPrompt = true, pinErrorRes = null) }
        }
    }

    fun onPinConfirmed(pin: String) {
        viewModelScope.launch {
            if (parentalDataStore.verifyPin(pin)) {
                session.unlock()
                _state.update {
                    it.copy(
                        adultUnlocked = true,
                        pinPrompt = false,
                        pinErrorRes = null
                    )
                }
                refreshChannels()
            } else {
                _state.update { it.copy(pinErrorRes = R.string.live_pin_wrong) }
            }
        }
    }

    fun onPinDismissed() {
        _state.update { it.copy(pinPrompt = false, pinErrorRes = null) }
    }

    fun playbackUrl(channel: LiveChannel): String? = repository.playbackUrl(channel)

    private fun rebuildCategories() {
        val current = snapshot ?: return
        val visible = if (_state.value.adultContentHidden) {
            current.categories.filterNot { it.isAdult }
        } else {
            current.categories
        }
        _state.update { it.copy(categories = visible) }
        val selected = selectedCategory
        if (selected != null && selected.isAdult && _state.value.adultContentHidden) {
            selectedCategory = null
            _state.update { it.copy(selectedCategoryId = null) }
        }
        refreshChannels()
    }

    private fun refreshChannels() {
        val current = snapshot ?: return
        val category = selectedCategory
        val channels = when {
            category == null -> current.channels.filter { channel ->
                val categoryId = channel.categoryId
                categoryId == 0 || current.categories.none { it.isAdult && it.id == categoryId }
            }
            category.isAdult && !_state.value.adultUnlocked -> emptyList()
            else -> current.channels.filter { it.categoryId == category.id }
        }
        _state.update { it.copy(channels = channels) }
    }
}
