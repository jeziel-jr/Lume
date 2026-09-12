package com.nuvio.tv.core.config

import java.security.KeyFactory
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import okio.ByteString.Companion.decodeBase64

/**
 * Verifies the detached ECDSA P-256 signature that authenticates [RemoteConfig].
 *
 * Verification runs over the raw bytes of the published document, so the operator never has to
 * reproduce a canonical JSON serialization in two languages. Anything that cannot be verified —
 * unknown key identifier, malformed base64, invalid signature — is rejected and the caller keeps
 * the configuration it already trusts.
 */
@Singleton
class RemoteConfigVerifier @Inject constructor() {

    fun verify(payload: ByteArray, signatureDerBase64: String, keyId: String): Boolean =
        RemoteConfigSignature.verify(payload, signatureDerBase64, keyId, RemoteConfigKeys.ACCEPTED)
}

/**
 * Signature check with an explicit key set, so the rule stays testable without shipping a private
 * key in the test sources.
 */
internal object RemoteConfigSignature {

    fun verify(
        payload: ByteArray,
        signatureDerBase64: String,
        keyId: String,
        acceptedKeys: Map<String, String>,
    ): Boolean {
        val signature = signatureDerBase64.trim().decodeBase64() ?: return false
        val key = publicKey(keyId, acceptedKeys) ?: return false
        return runCatching {
            Signature.getInstance(SIGNATURE_ALGORITHM).run {
                initVerify(key)
                update(payload)
                verify(signature.toByteArray())
            }
        }.getOrDefault(false)
    }

    private fun publicKey(keyId: String, acceptedKeys: Map<String, String>): PublicKey? {
        val encoded = acceptedKeys[keyId] ?: return null
        val bytes = encoded.decodeBase64() ?: return null
        return runCatching {
            KeyFactory.getInstance(KEY_ALGORITHM).generatePublic(X509EncodedKeySpec(bytes.toByteArray()))
        }.getOrNull()
    }

    private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    private const val KEY_ALGORITHM = "EC"
}
