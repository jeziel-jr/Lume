package com.nuvio.tv.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nuvio.tv.core.profile.ProfileManager
import com.nuvio.tv.domain.model.UserProfile
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileSettingsViewModel @Inject constructor(
    private val profileManager: ProfileManager
) : ViewModel() {

    val profiles: StateFlow<List<UserProfile>> = profileManager.profiles

    val activeProfileId: StateFlow<Int> = profileManager.activeProfileId

    val isPrimaryProfileActive: StateFlow<Boolean> = profileManager.activeProfileId
        .map { it == 1 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val canAddProfile: Boolean
        get() = profileManager.canCreateProfile

    fun setActiveProfile(id: Int) {
        viewModelScope.launch {
            profileManager.setActiveProfile(id)
        }
    }

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    fun createProfile(
        name: String,
        avatarColorHex: String,
        usesPrimaryAddons: Boolean,
        usesPrimaryPlugins: Boolean,
        avatarId: String? = null
    ) {
        if (_isCreating.value) return
        viewModelScope.launch {
            _isCreating.value = true
            val existingIds = profileManager.profiles.value.map { it.id }.toSet()
            val success = profileManager.createProfile(
                name = name,
                avatarColorHex = avatarColorHex,
                avatarId = avatarId
            )
            if (success) {
                val profiles = profileManager.profiles.value
                val newProfile = profiles.firstOrNull { it.id !in existingIds }
                if (newProfile != null && (usesPrimaryAddons || usesPrimaryPlugins)) {
                    profileManager.updateProfile(
                        newProfile.copy(
                            usesPrimaryAddons = usesPrimaryAddons,
                            usesPrimaryPlugins = usesPrimaryPlugins
                        )
                    )
                }
            }
            _isCreating.value = false
        }
    }

    fun updateProfile(profile: UserProfile) {
        viewModelScope.launch {
            profileManager.updateProfile(profile)
        }
    }

    fun deleteProfile(id: Int) {
        viewModelScope.launch {
            profileManager.deleteProfile(id)
        }
    }
}
