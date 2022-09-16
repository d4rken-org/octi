package eu.darken.octi.metainfo.core

import android.os.Build
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.DynamicStateFlow
import eu.darken.octi.sync.core.SyncOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetaRepo @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val syncOptions: SyncOptions,
) {

    data class State(
        val self: SyncDataContainer<MetaInfo>,
        val others: Collection<SyncDataContainer<MetaInfo>> = emptySet(),
    ) {
        val all: Collection<SyncDataContainer<MetaInfo>>
            get() = listOf(self) + others
    }

    private val _state = DynamicStateFlow(parentScope = scope) {
        val info = MetaInfo(
            versionName = BuildConfigWrap.VERSION_NAME,
            deviceName = Build.MODEL,
            deviceType = MetaInfo.DeviceType.PHONE,
            androidVersionName = Build.VERSION.RELEASE_OR_CODENAME,
            androidApiLevel = Build.VERSION.SDK_INT,
        )
        State(
            self = SyncDataContainer(
                deviceId = syncOptions.deviceId,
                modifiedAt = Instant.now(),
                data = info,
            )
        )
    }

    val state: Flow<State> = _state.flow

    suspend fun updateOthers(newOthers: Collection<SyncDataContainer<MetaInfo>>) {
        log(TAG) { "updateOthers(newOthers=$newOthers)" }
        _state.updateBlocking {
            copy(
                others = newOthers
            )
        }
    }

    companion object {
        val TAG = logTag("Module", "Time", "Repo")
    }
}