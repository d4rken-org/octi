package eu.darken.octi.meta.core

import android.os.Build
import android.os.SystemClock
import eu.darken.octi.common.BuildConfigWrap
import eu.darken.octi.common.coroutine.AppScope
import eu.darken.octi.common.coroutine.DispatcherProvider
import eu.darken.octi.common.debug.logging.logTag
import eu.darken.octi.common.flow.replayingShare
import eu.darken.octi.common.flow.setupCommonEventHandlers
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MetaSource @Inject constructor(
    @AppScope private val scope: CoroutineScope,
    dispatcherProvider: DispatcherProvider,
) {

    val self: Flow<MetaInfo> = flow {
        while (currentCoroutineContext().isActive) {
            val info = MetaInfo(
                octiVersionName = BuildConfigWrap.VERSION_NAME,
                octiGitSha = BuildConfigWrap.GIT_SHA,
                deviceName = Build.MODEL,
                deviceType = MetaInfo.DeviceType.PHONE,
                androidVersionName = Build.VERSION.CODENAME,
                androidApiLevel = Build.VERSION.SDK_INT,
                deviceUptime = SystemClock.elapsedRealtime(),
            )
            emit(info)
            delay(10 * 1000)
        }
    }
        .setupCommonEventHandlers(TAG) { "self" }
        .replayingShare(scope + dispatcherProvider.IO)

    companion object {
        val TAG = logTag("Module", "Meta", "Source")
    }
}