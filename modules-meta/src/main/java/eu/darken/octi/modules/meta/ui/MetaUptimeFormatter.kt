package eu.darken.octi.modules.meta.ui

import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Instant

fun formatUptime(now: Instant, bootedAt: Instant): String {
    val raw = now - bootedAt
    val span = if (raw < ZERO) ZERO else raw
    return span.toComponents { days, hours, minutes, _, _ ->
        when {
            days > 0L -> "${days}d ${hours}h"
            hours > 0L -> "${hours}h ${minutes}m"
            minutes > 0L -> "${minutes}m"
            else -> "<1m"
        }
    }
}
