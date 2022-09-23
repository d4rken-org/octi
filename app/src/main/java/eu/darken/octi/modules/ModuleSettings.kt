package eu.darken.octi.modules

import eu.darken.octi.common.preferences.FlowPreference

interface ModuleSettings {

    val isEnabled: FlowPreference<Boolean>
}