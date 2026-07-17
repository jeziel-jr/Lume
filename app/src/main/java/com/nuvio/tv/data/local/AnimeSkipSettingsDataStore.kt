package com.nuvio.tv.data.local

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.nuvio.tv.BuildConfig
import com.nuvio.tv.core.profile.ProfileManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(ExperimentalCoroutinesApi::class)
class AnimeSkipSettingsDataStore @Inject constructor(
    private val factory: ProfileDataStoreFactory,
    private val profileManager: ProfileManager
) {
    companion object {
        private const val FEATURE = "animeskip_settings"
    }

    private fun store() = factory.get(profileManager.activeProfileId.value, FEATURE)

    private val enabledKey = booleanPreferencesKey("animeskip_enabled")
    private val clientIdKey = stringPreferencesKey("animeskip_client_id")
    private val buildClientId: String
        get() = BuildConfig.ANIMESKIP_CLIENT_ID.trim()

    val enabled: Flow<Boolean> = profileManager.activeProfileId.flatMapLatest { pid ->
        factory.get(pid, FEATURE).data.map { preferences ->
            preferences[enabledKey] ?: buildClientId.isNotBlank()
        }
    }

    val clientId: Flow<String> = profileManager.activeProfileId.flatMapLatest { pid ->
        factory.get(pid, FEATURE).data.map { preferences ->
            preferences[clientIdKey]
                ?.trim()
                ?.takeIf { it.isNotBlank() }
                ?: buildClientId
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        store().edit { it[enabledKey] = enabled }
    }

    suspend fun setClientId(clientId: String) {
        store().edit { it[clientIdKey] = clientId.trim() }
    }
}
