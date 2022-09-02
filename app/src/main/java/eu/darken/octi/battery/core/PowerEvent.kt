package eu.darken.octi.battery.core

sealed class PowerEvent {
    object PowerConnected : PowerEvent()
    object PowerDisconnected : PowerEvent()
}
