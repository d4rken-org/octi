package eu.darken.octi.module.core

import eu.darken.octi.common.preferences.FlowPreference

interface ModuleSettings {

    val isEnabled: FlowPreference<Boolean>
}