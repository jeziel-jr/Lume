package com.nuvio.tv.data.xtream

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.nuvio.tv.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class XtreamCredentials(
    val baseUrl: String,
    val username: String,
    val password: String,
) {
    fun normalized(): XtreamCredentials = copy(baseUrl = normalizeXtreamBaseUrl(baseUrl))

    val isComplete: Boolean
        get() = baseUrl.isNotBlank() && username.isNotBlank() && password.isNotBlank()
}

data class XtreamAccountInfo(
    val username: String,
    val status: String?,
    val expirationEpochSeconds: Long?,
    val activeConnections: Int?,
    val maxConnections: Int?,
)

fun normalizeXtreamBaseUrl(value: String): String {
    val trimmed = value.trim().trimEnd('/')
        .removeSuffix("/player_api.php")
        .trimEnd('/')
    return when {
        trimmed.isBlank() -> ""
        trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) -> trimmed
        else -> "http://$trimmed"
    }
}

@Singleton
class XtreamCredentialsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val storedAccount = readStoredAccount()
    private val _credentials = MutableStateFlow(storedAccount?.credentials)
    val credentials: StateFlow<XtreamCredentials?> = _credentials.asStateFlow()
    private val _accountInfo = MutableStateFlow(storedAccount?.accountInfo)
    val accountInfo: StateFlow<XtreamAccountInfo?> = _accountInfo.asStateFlow()

    val defaultBaseUrl: String
        get() = normalizeXtreamBaseUrl(BuildConfig.XTREAM_DEFAULT_BASE_URL)

    fun current(): XtreamCredentials? = _credentials.value

    fun save(value: XtreamCredentials, accountInfo: XtreamAccountInfo? = _accountInfo.value) {
        val normalized = value.normalized()
        require(normalized.isComplete) { "Incomplete Xtream credentials" }
        val json = JSONObject()
            .put("baseUrl", normalized.baseUrl)
            .put("username", normalized.username.trim())
            .put("password", normalized.password)
            .put("accountStatus", accountInfo?.status)
            .put("expirationEpochSeconds", accountInfo?.expirationEpochSeconds)
            .put("activeConnections", accountInfo?.activeConnections)
            .put("maxConnections", accountInfo?.maxConnections)
            .toString()
        preferences.edit().putString(CREDENTIALS_KEY, encrypt(json)).apply()
        _credentials.value = normalized.copy(username = normalized.username.trim())
        _accountInfo.value = accountInfo?.copy(username = normalized.username.trim())
    }

    fun updateAccountInfo(value: XtreamAccountInfo) {
        current()?.let { save(it, value) }
    }

    fun clear() {
        preferences.edit().remove(CREDENTIALS_KEY).apply()
        _credentials.value = null
        _accountInfo.value = null
    }

    private fun readStoredAccount(): StoredAccount? {
        val encrypted = preferences.getString(CREDENTIALS_KEY, null) ?: return null
        return runCatching {
            val json = JSONObject(decrypt(encrypted))
            val credentials = XtreamCredentials(
                baseUrl = json.getString("baseUrl"),
                username = json.getString("username"),
                password = json.getString("password"),
            ).normalized().takeIf(XtreamCredentials::isComplete)
                ?: return@runCatching null
            StoredAccount(
                credentials = credentials,
                accountInfo = XtreamAccountInfo(
                    username = credentials.username,
                    status = json.optString("accountStatus").takeIf { it.isNotBlank() && it != "null" },
                    expirationEpochSeconds = json.optLongOrNull("expirationEpochSeconds"),
                    activeConnections = json.optIntOrNull("activeConnections"),
                    maxConnections = json.optIntOrNull("maxConnections"),
                ).takeIf { info ->
                    info.status != null || info.expirationEpochSeconds != null ||
                        info.activeConnections != null || info.maxConnections != null
                },
            )
        }.getOrElse {
            preferences.edit().remove(CREDENTIALS_KEY).apply()
            null
        }
    }

    private fun JSONObject.optLongOrNull(key: String): Long? =
        if (has(key) && !isNull(key)) optLong(key).takeIf { it > 0L } else null

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (has(key) && !isNull(key)) optInt(key).takeIf { it >= 0 } else null

    private fun encrypt(plainText: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        return listOf(
            FORMAT_VERSION,
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            Base64.encodeToString(encrypted, Base64.NO_WRAP),
        ).joinToString(":")
    }

    private fun decrypt(value: String): String {
        val parts = value.split(':', limit = 3)
        require(parts.size == 3 && parts[0] == FORMAT_VERSION)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            encryptionKey(),
            GCMParameterSpec(128, Base64.decode(parts[1], Base64.NO_WRAP)),
        )
        return cipher.doFinal(Base64.decode(parts[2], Base64.NO_WRAP)).toString(Charsets.UTF_8)
    }

    private fun encryptionKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE_PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val PREFERENCES_NAME = "xtream_credentials"
        const val CREDENTIALS_KEY = "encrypted_credentials"
        const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "lume_xtream_credentials_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val FORMAT_VERSION = "v1"
    }

    private data class StoredAccount(
        val credentials: XtreamCredentials,
        val accountInfo: XtreamAccountInfo?,
    )
}
