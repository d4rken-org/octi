package eu.darken.octi.common.navigation

import eu.darken.octi.common.navigation.NavigationDestination
import kotlinx.serialization.Serializable

object Nav {
    sealed interface Main : NavigationDestination {
        @Serializable
        data object Dashboard : Main

        @Serializable
        data object Welcome : Main

        @Serializable
        data object Privacy : Main

        @Serializable
        data class Upgrade(val forced: Boolean = false) : Main

        @Serializable
        data class AppsList(val deviceId: String) : Main

        @Serializable
        data class PowerAlerts(val deviceId: String) : Main
    }

    sealed interface Sync : NavigationDestination {
        @Serializable
        data object List : Sync

        @Serializable
        data object Add : Sync

        @Serializable
        data object AddGDrive : Sync

        @Serializable
        data object AddKServer : Sync

        @Serializable
        data class KServerLinkHost(val connectorId: String) : Sync

        @Serializable
        data object KServerLinkClient : Sync

        @Serializable
        data class Devices(val connectorId: String) : Sync
    }

    sealed interface Settings : NavigationDestination {
        @Serializable
        data object Index : Settings

        @Serializable
        data object General : Settings

        @Serializable
        data object Support : Settings

        @Serializable
        data object Acknowledgements : Settings

        @Serializable
        data object Modules : Settings

        @Serializable
        data object Sync : Settings
    }
}
