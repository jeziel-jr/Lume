package com.nuvio.tv.core.config

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteConfigVerifierTest {

    private val keyPair: KeyPair = KeyPairGenerator.getInstance("EC")
        .apply { initialize(256) }
        .generateKeyPair()

    private val keys = mapOf("test" to Base64.getEncoder().encodeToString(keyPair.public.encoded))

    @Test
    fun `accepts a payload signed by the matching key`() {
        val payload = document(revision = 2)

        assertTrue(RemoteConfigSignature.verify(payload, sign(payload), "test", keys))
    }

    @Test
    fun `rejects a payload changed after signing`() {
        val signature = sign(document(revision = 2))

        assertFalse(RemoteConfigSignature.verify(document(revision = 3), signature, "test", keys))
    }

    @Test
    fun `rejects a signature from another key`() {
        val attacker = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        val payload = document(revision = 2)
        val signature = Base64.getEncoder().encodeToString(
            Signature.getInstance("SHA256withECDSA").run {
                initSign(attacker.private)
                update(payload)
                sign()
            },
        )

        assertFalse(RemoteConfigSignature.verify(payload, signature, "test", keys))
    }

    @Test
    fun `rejects an unknown key identifier`() {
        val payload = document(revision = 2)

        assertFalse(RemoteConfigSignature.verify(payload, sign(payload), "unknown", keys))
    }

    @Test
    fun `rejects a signature that is not base64`() {
        assertFalse(RemoteConfigSignature.verify(document(revision = 2), "not base64 !!", "test", keys))
    }

    @Test
    fun `verifies the published configuration with the embedded key`() {
        val document = PublishedRemoteConfig.read()
        val config = RemoteConfigParser.parse(PublishedRemoteConfig.moshi, document.payload)
            ?: error("published remote-config/xtream.json does not parse")

        assertTrue(
            "published configuration must be signed with a key embedded in RemoteConfigKeys",
            RemoteConfigVerifier().verify(document.payloadBytes, document.signature, config.keyId),
        )
    }

    private fun document(revision: Int): ByteArray =
        """{"schema":1,"revision":$revision,"keyId":"test","endpoints":["https://example.test"]}"""
            .toByteArray()

    private fun sign(payload: ByteArray): String = Base64.getEncoder().encodeToString(
        Signature.getInstance("SHA256withECDSA").run {
            initSign(keyPair.private)
            update(payload)
            sign()
        },
    )
}
