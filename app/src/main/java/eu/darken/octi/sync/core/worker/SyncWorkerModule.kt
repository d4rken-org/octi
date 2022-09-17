package eu.darken.octi.sync.core.worker

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import kotlinx.coroutines.CoroutineScope

@InstallIn(SyncWorkerComponent::class)
@Module
abstract class SyncWorkerModule {

    @Binds
    @SyncWorkerScope
    abstract fun syncWorkerScope(scope: SyncWorkerCoroutineScope): CoroutineScope

}
