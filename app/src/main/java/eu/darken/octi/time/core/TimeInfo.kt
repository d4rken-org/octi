package eu.darken.octi.time.core

import java.time.OffsetDateTime

data class TimeInfo(
    val deviceTime: OffsetDateTime = OffsetDateTime.now()
)