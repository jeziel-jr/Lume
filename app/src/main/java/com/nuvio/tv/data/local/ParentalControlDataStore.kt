package com.nuvio.tv.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.parentalDataStore: DataStore<Preferences> by preferencesDataStore(name = "parental_control")

data class ParentalControlSettings(
    val pinSet: Boolean,
    val adultContentHidden: Boolean
)

/**
 * Local parental-control preferences. Stores only a salted SHA-256 hash of the
 * 4-digit PIN; the PIN itself is never persisted.
 */
@Singleton
class ParentalControlDataStore internal constructor(
    private val dataStore: DataStore<Preferences>
) {

    @Inject
    constructor(@ApplicationContext context: Context) : this(context.parentalDataStore)

    private val pinSaltKey = stringPreferencesKey("pin_salt")
    private val pinHashKey = stringPreferencesKey("pin_hash")
    private val adultContentHiddenKey = booleanPreferencesKey("adult_content_hidden")

    val settings: Flow<ParentalControlSettings> = dataStore.data.map { prefs ->
        ParentalControlSettings(
            pinSet = !prefs[pinSaltKey].isNullOrBlank() && !prefs[pinHashKey].isNullOrBlank(),
            adultContentHidden = prefs[adultContentHiddenKey] ?: true
        )
    }

    /** Validates and stores a new 4-digit PIN. Returns true when accepted. */
    suspend fun definePin(pin: String): Boolean {
        if (!Regex("^\\d{4}$").matches(pin)) return false
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val saltHex = salt.toHex()
        val hashHex = sha256Hex(salt + pin.toByteArray(Charsets.UTF_8))
        dataStore.edit { prefs ->
            prefs[pinSaltKey] = saltHex
            prefs[pinHashKey] = hashHex
        }
        return true
    }

    /** Verifies the given PIN against the stored hash. */
    suspend fun verifyPin(pin: String): Boolean {
        val prefs = dataStore.data.first()
        val saltHex = prefs[pinSaltKey] ?: return false
        val hashHex = prefs[pinHashKey] ?: return false
        val salt = saltHex.hexToBytes() ?: return false
        val candidate = sha256Hex(salt + pin.toByteArray(Charsets.UTF_8))
        return candidate.equals(hashHex, ignoreCase = true)
    }

    suspend fun clearPin() {
        dataStore.edit { prefs ->
            prefs.remove(pinSaltKey)
            prefs.remove(pinHashKey)
        }
    }

    suspend fun setAdultContentHidden(hidden: Boolean) {
        dataStore.edit { prefs ->
            prefs[adultContentHiddenKey] = hidden
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray? {
        if (length % 2 != 0) return null
        return runCatching {
            ByteArray(length / 2) { i ->
                substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }.getOrNull()
    }

    private fun sha256Hex(input: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(input).toHex()
}
