package eu.darken.octi.common.upgrade.core

import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.upgrade.core.data.AvailableSku
import eu.darken.octi.common.upgrade.core.data.Sku

enum class OctiSku constructor(override val sku: Sku) : AvailableSku {
    PRO_UPGRADE(Sku("${BuildConfigWrap.APPLICATION_ID}.iap.upgrade.pro"))
}