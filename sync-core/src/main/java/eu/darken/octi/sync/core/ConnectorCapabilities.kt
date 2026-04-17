package eu.darken.octi.sync.core

data class ConnectorCapabilities(
    val deviceRemovalPolicy: DeviceRemovalPolicy,
) {
    companion object {
        val DEFAULT_FOR_TEST = ConnectorCapabilities(
            deviceRemovalPolicy = DeviceRemovalPolicy.REMOVE_LOCAL_ONLY,
        )
    }
}

enum class DeviceRemovalPolicy {
    /** Remove the local association; the device's data remains on the backend (GDrive AppData pattern). */
    REMOVE_LOCAL_ONLY,

    /** Revoke the device entirely; its server-side data is removed (Octi Server pattern). */
    REMOVE_AND_REVOKE_REMOTE,
}
