package com.nuvio.tv.ui.screens.settings

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.R
import com.nuvio.tv.data.local.ParentalControlDataStore
import com.nuvio.tv.data.local.ParentalControlSession
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PinDialogMode {
    DEFINE,
    CHANGE_CURRENT,
    CHANGE_NEW,
    SHOW_ADULT
}

data class ParentalControlState(
    val pinSet: Boolean = false,
    val adultContentHidden: Boolean = true,
    val dialog: PinDialogMode? = null,
    @StringRes val dialogErrorRes: Int? = null,
    val defineThenShow: Boolean = false
)

@HiltViewModel
class ParentalControlViewModel @Inject constructor(
    private val dataStore: ParentalControlDataStore,
    private val session: ParentalControlSession
) : ViewModel() {

    private val _state = MutableStateFlow(ParentalControlState())
    val state: StateFlow<ParentalControlState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            dataStore.settings.collect { settings ->
                _state.update {
                    it.copy(
                        pinSet = settings.pinSet,
                        adultContentHidden = settings.adultContentHidden
                    )
                }
            }
        }
    }

    fun openPinEntry() {
        val mode = if (_state.value.pinSet) PinDialogMode.CHANGE_CURRENT else PinDialogMode.DEFINE
        _state.update { it.copy(dialog = mode, dialogErrorRes = null, defineThenShow = false) }
    }

    fun setShowAdult(show: Boolean) {
        if (!show) {
            viewModelScope.launch { dataStore.setAdultContentHidden(true) }
            return
        }
        val mode = if (_state.value.pinSet) PinDialogMode.SHOW_ADULT else PinDialogMode.DEFINE
        _state.update { it.copy(dialog = mode, dialogErrorRes = null, defineThenShow = mode == PinDialogMode.DEFINE) }
    }

    fun onPinConfirm(pin: String) {
        when (_state.value.dialog) {
            PinDialogMode.DEFINE -> viewModelScope.launch {
                dataStore.definePin(pin)
                val shouldShow = _state.value.defineThenShow
                if (shouldShow) {
                    dataStore.setAdultContentHidden(false)
                }
                _state.update {
                    it.copy(
                        dialog = null,
                        dialogErrorRes = null,
                        defineThenShow = false,
                        adultContentHidden = if (shouldShow) false else it.adultContentHidden
                    )
                }
            }
            PinDialogMode.CHANGE_CURRENT -> viewModelScope.launch {
                if (dataStore.verifyPin(pin)) {
                    _state.update { it.copy(dialog = PinDialogMode.CHANGE_NEW, dialogErrorRes = null) }
                } else {
                    _state.update { it.copy(dialogErrorRes = R.string.live_pin_wrong) }
                }
            }
            PinDialogMode.CHANGE_NEW -> viewModelScope.launch {
                dataStore.definePin(pin)
                _state.update { it.copy(dialog = null, dialogErrorRes = null) }
            }
            PinDialogMode.SHOW_ADULT -> viewModelScope.launch {
                if (dataStore.verifyPin(pin)) {
                    dataStore.setAdultContentHidden(false)
                    _state.update { it.copy(dialog = null, dialogErrorRes = null) }
                } else {
                    _state.update { it.copy(dialogErrorRes = R.string.live_pin_wrong) }
                }
            }
            null -> Unit
        }
    }

    fun onPinDismiss() {
        _state.update { it.copy(dialog = null, dialogErrorRes = null, defineThenShow = false) }
    }

    fun forgotPin() {
        viewModelScope.launch {
            dataStore.clearPin()
            dataStore.setAdultContentHidden(true)
            session.adultUnlocked = false
            _state.update {
                it.copy(
                    dialog = null,
                    dialogErrorRes = null,
                    pinSet = false,
                    adultContentHidden = true
                )
            }
        }
    }
}
