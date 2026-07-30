package com.nuvio.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.nuvio.tv.R
import com.nuvio.tv.data.xtream.CatalogPlaybackAvailability
import com.nuvio.tv.ui.theme.NuvioTheme

internal fun CatalogPlaybackAvailability?.showsCatalogAvailabilityBadge(): Boolean =
    this == CatalogPlaybackAvailability.UNAVAILABLE ||
        this == CatalogPlaybackAvailability.UNKNOWN

@Composable
fun CatalogAvailabilityBadge(
    availability: CatalogPlaybackAvailability?,
    modifier: Modifier = Modifier,
) {
    if (!availability.showsCatalogAvailabilityBadge()) {
        return
    }
    val label = if (availability == CatalogPlaybackAvailability.UNAVAILABLE) {
        stringResource(R.string.catalog_availability_unavailable)
    } else {
        stringResource(R.string.catalog_availability_checking)
    }
    Row(
        modifier = modifier
            .zIndex(3f)
            .height(20.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(NuvioTheme.colors.BackgroundCard.copy(alpha = 0.92f))
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(2.dp)
                .height(12.dp)
                .background(NuvioTheme.colors.Warning, RoundedCornerShape(1.dp)),
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = NuvioTheme.colors.TextPrimary,
            maxLines = 1,
        )
    }
}
