package com.nuvio.tv.data.mapper

import com.nuvio.tv.data.remote.dto.MetaLinkDto
import com.nuvio.tv.domain.model.MetaLink

fun MetaLinkDto.toDomain(): MetaLink? {
    return url?.let {
        MetaLink(
            name = name,
            category = category,
            url = it
        )
    }
}
