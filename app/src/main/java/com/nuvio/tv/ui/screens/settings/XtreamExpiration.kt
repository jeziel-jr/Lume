package com.nuvio.tv.ui.screens.settings

import java.time.Duration
import java.time.Instant

internal sealed interface XtreamExpiration {
    data object Missing : XtreamExpiration
    data object Expired : XtreamExpiration
    data object LessThanMinute : XtreamExpiration
    data class Days(val count: Long) : XtreamExpiration
    data class HoursAndMinutes(val hours: Long, val minutes: Long) : XtreamExpiration
}

internal fun classifyXtreamExpiration(
    expirationEpochSeconds: Long?,
    now: Instant,
): XtreamExpiration {
    if (expirationEpochSeconds == null) return XtreamExpiration.Missing

    val expiration = runCatching { Instant.ofEpochSecond(expirationEpochSeconds) }
        .getOrNull()
        ?: return XtreamExpiration.Missing
    val remaining = Duration.between(now, expiration)
    if (remaining.isZero || remaining.isNegative) return XtreamExpiration.Expired

    val seconds = remaining.seconds
    if (seconds < Duration.ofMinutes(1).seconds) return XtreamExpiration.LessThanMinute
    if (seconds >= Duration.ofHours(24).seconds) {
        return XtreamExpiration.Days(seconds / Duration.ofDays(1).seconds)
    }
    return XtreamExpiration.HoursAndMinutes(
        hours = seconds / Duration.ofHours(1).seconds,
        minutes = (seconds % Duration.ofHours(1).seconds) / Duration.ofMinutes(1).seconds,
    )
}
