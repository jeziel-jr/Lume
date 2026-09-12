package com.nuvio.tv.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Persists the verified remote configuration and the endpoint the app currently uses.
 *
 * Backed by SharedPreferences (like [com.nuvio.tv.data.xtream.XtreamCredentialsStore]) because the
 * active endpoint is needed synchronously during startup and during setup, and it holds no secret:
 * the signed document is public information and the credentials stay in their own encrypted store.
 */
@Singleton
class RemoteConfigStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    data class Snapshot(
        val payload: String? = null,
        val signature: String? = null,
        val revision: Int = 0,
        val fetchedAtMillis: Long = 0L,
        val activeEndpoint: String? = null,
        val manualOverride: String? = null,
    )

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Snapshot> = _state.asStateFlow()

    fun current(): Snapshot = _state.value

    fun saveDocument(payload: String, signature: String, revision: Int, fetchedAtMillis: Long) {
        preferences.edit()
            .putString(KEY_PAYLOAD, payload)
            .putString(KEY_SIGNATURE, signature)
            .putInt(KEY_REVISION, revision)
            .putLong(KEY_FETCHED_AT, fetchedAtMillis)
            .apply()
        _state.value = _state.value.copy(
            payload = payload,
            signature = signature,
            revision = revision,
            fetchedAtMillis = fetchedAtMillis,
        )
    }

    fun setActiveEndpoint(endpoint: String?) {
        preferences.edit().putString(KEY_ACTIVE_ENDPOINT, endpoint).apply()
        _state.value = _state.value.copy(activeEndpoint = endpoint)
    }

    fun setManualOverride(endpoint: String?) {
        preferences.edit().putString(KEY_MANUAL_OVERRIDE, endpoint).apply()
        _state.value = _state.value.copy(manualOverride = endpoint)
    }

    private fun read(): Snapshot = Snapshot(
        payload = preferences.getString(KEY_PAYLOAD, null),
        signature = preferences.getString(KEY_SIGNATURE, null),
        revision = preferences.getInt(KEY_REVISION, 0),
        fetchedAtMillis = preferences.getLong(KEY_FETCHED_AT, 0L),
        activeEndpoint = preferences.getString(KEY_ACTIVE_ENDPOINT, null)?.takeIf { it.isNotBlank() },
        manualOverride = preferences.getString(KEY_MANUAL_OVERRIDE, null)?.takeIf { it.isNotBlank() },
    )

    private companion object {
        const val PREFERENCES_NAME = "remote_config"
        const val KEY_PAYLOAD = "payload"
        const val KEY_SIGNATURE = "signature"
        const val KEY_REVISION = "revision"
        const val KEY_FETCHED_AT = "fetched_at"
        const val KEY_ACTIVE_ENDPOINT = "active_endpoint"
        const val KEY_MANUAL_OVERRIDE = "manual_override"
    }
}
