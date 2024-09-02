package eu.darken.octi.common.preferences

import eu.darken.octi.common.ca.CaString

interface EnumPreference<T : Enum<T>> {
    val label: CaString
}