package com.nuvio.tv.core.config

/**
 * Public keys accepted for the remote configuration signature (SPKI DER, base64).
 *
 * The private key never leaves the operator machine (`~/.lume/config-signing/`) and is not part of
 * this repository. To rotate the signing key, generate a new pair, add its public key here with a
 * new identifier, sign the configuration with the new key, and keep the previous entry until every
 * installed device ships the update that contains it.
 */
internal object RemoteConfigKeys {
    val ACCEPTED: Map<String, String> = mapOf(
        "k1" to "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEXpFBO6eCDtJp00mL4iXleS8qsVc+ZFmAtzj3DejhiIIUSj3wQyM33VXXQfAVS3ror1W1eLWR5RzVucqaHk/HTg==",
    )
}
