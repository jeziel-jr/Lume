package com.nuvio.tv.ui.screens.search

import com.nuvio.tv.domain.model.MetaPreview

internal fun MetaPreview.discoverIdentityKey(): String {
    val externalId = imdbId
        ?.substringBefore(':')
        ?.takeIf(String::isNotBlank)
        ?: id.substringBefore(':').takeIf { id.startsWith("tt", ignoreCase = true) }
        ?: id
    return "$apiType:$externalId"
}
