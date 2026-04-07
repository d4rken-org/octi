package eu.darken.octi.common

import kotlin.time.Clock
import kotlin.time.Instant

fun Instant.clampToNow(): Instant {
    val now = Clock.System.now()
    return if (this > now) now else this
}
