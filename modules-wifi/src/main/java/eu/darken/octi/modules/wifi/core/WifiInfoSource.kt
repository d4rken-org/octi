package eu.darken.octi.modules.wifi.core

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.network.NetworkStateProvider
import eu.darken.octi.common.network.WifiStateProvider
import eu.darken.octi.module.core.ModuleInfoSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WifiInfoSource @Inject constructor(
    @AppScope private val appScope: CoroutineScope,
    @ApplicationContext private val context: Context,
    private val networkStateProvider: NetworkStateProvider,
    private val wifiStateProvider: WifiStateProvider,
) : ModuleInfoSource<WifiInfo> {


    override val info: Flow<WifiInfo> = combine(
        networkStateProvider.networkState,
        wifiStateProvider.wifiState
    ) { networkState, wifiState ->
        WifiInfo(
            currentWifi = WifiInfo.Wifi(
                reception = wifiState?.signalStrength,
                ssid = wifiState?.ssid,
                freqType = wifiState?.frequency?.let {
                    when (it) {
                        in 2401..2499 -> WifiInfo.Wifi.Type.TWO_POINT_FOUR_GHZ
                        in 4901..5899 -> WifiInfo.Wifi.Type.FIVE_GHZ
                        else -> WifiInfo.Wifi.Type.UNKNOWN
                    }
                }
            )
        )
    }

}