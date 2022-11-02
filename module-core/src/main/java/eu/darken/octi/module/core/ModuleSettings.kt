package eu.darken.octi.module.core

import eu.darken.octi.common.datastore.DataStoreValue

interface ModuleSettings {

    val isEnabled: DataStoreValue<Boolean>
}