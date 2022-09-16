package eu.darken.octi.sync.core.worker

import dagger.BindsInstance
import dagger.hilt.DefineComponent
import dagger.hilt.components.SingletonComponent

@SyncWorkerScope
@DefineComponent(parent = SingletonComponent::class)
interface SyncWorkerComponent {

    @DefineComponent.Builder
    interface Builder {

        fun coroutineScope(@BindsInstance coroutineScope: SyncWorkerCoroutineScope): Builder

        fun build(): SyncWorkerComponent
    }
}