package eu.darken.octi.modules.meta.core

import android.os.Build
import android.os.SystemClock
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.replayingShare
import eu.darken.octi.common.flow.setupCommonEventHandlers
import eu.darken.octi.modules.ModuleInfoSource
import eu.darken.octi.sync.core.SyncSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetaInfoSource @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
    private val syncSettings: SyncSettings,
) : ModuleInfoSource<MetaInfo> {

    override val info: Flow<MetaInfo> = syncSettings.deviceLabel.flow
        .flatMapLatest { deviceLabel ->
            flow {
                while (currentCoroutineContext().isActive) {
                    val info = MetaInfo(
                        deviceLabel = deviceLabel.takeIf { !it.isNullOrEmpty() },
                        deviceId = syncSettings.deviceId,
                        octiVersionName = BuildConfigWrap.VERSION_NAME,
                        octiGitSha = BuildConfigWrap.GIT_SHA,
                        deviceName = Build.MODEL,
                        deviceType = MetaInfo.DeviceType.PHONE,
                        androidVersionName = Build.VERSION.CODENAME.let { if (it == "REL") Build.VERSION.RELEASE else it },
                        androidApiLevel = Build.VERSION.SDK_INT,
                        androidSecurityPatch = Build.VERSION.SECURITY_PATCH,
                        deviceBootedAt = Instant.now().minusMillis(SystemClock.elapsedRealtime()),
                    )
                    emit(info)
                    delay(10 * 1000)
                }
            }
        }
        .setupCommonEventHandlers(TAG) { "self" }
        .replayingShare(scope + dispatcherProvider.Default)

    companion object {
        val TAG = logTag("Module", "Meta", "Source")
    }
}
