package com.nuvio.tv.data.xtream

/**
 * Pure decisions for provider endpoint rotation. Kept free of Android and network types so the
 * precedence rules stay testable.
 */
internal object XtreamEndpointPolicy {

    /**
     * Keeps only usable endpoints from a signature-verified document.
     *
     * Plain HTTP is accepted here because the operator approves the exact host by signing it — the
     * signature is what prevents an unapproved server from being injected, and a burned domain may
     * only answer over HTTP. The trade-off is deliberate: over HTTP the account travels in the
     * clear, so a listener on the network path can read it. Anything that is not HTTP(S) is dropped.
     */
    fun sanitize(endpoints: List<String>): List<String> = endpoints
        .map { it.trim().trimEnd('/') }
        .filter { endpoint ->
            endpoint.startsWith("https://", ignoreCase = true) ||
                endpoint.startsWith("http://", ignoreCase = true)
        }
        .distinct()

    /**
     * Endpoint to adopt after a configuration refresh, or null to keep the current one.
     *
     * The list is the operator's priority order: the current endpoint stays while it is still the
     * published primary, otherwise the first candidate that answers becomes the new endpoint. A
     * candidate that fails is skipped instead of leaving the device without a working endpoint.
     */
    suspend fun selectForConfig(
        endpoints: List<String>,
        current: String?,
        probe: suspend (String) -> Boolean,
    ): String? {
        if (endpoints.isEmpty()) return null
        if (current != null && endpoints.first() == current) return null
        for (candidate in endpoints) {
            if (candidate == current) continue
            if (probe(candidate)) return candidate
        }
        return null
    }

    /**
     * Endpoint to adopt after a connectivity failure, or null to keep the current one.
     *
     * The current endpoint is probed first so a single timeout does not rotate away from a healthy
     * domain; only when it does not answer do the published candidates get a turn.
     */
    suspend fun selectForFailure(
        endpoints: List<String>,
        current: String?,
        probe: suspend (String) -> Boolean,
    ): String? {
        if (current != null && probe(current)) return null
        for (candidate in endpoints) {
            if (candidate == current) continue
            if (probe(candidate)) return candidate
        }
        return null
    }
}
