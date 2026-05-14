package eu.darken.octi.common.upgrade

import android.content.Context
import android.content.Intent
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import eu.darken.octi.main.ui.MainActivity
import eu.darken.octi.main.ui.MainActivityVM
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpgradeLauncherImpl @Inject constructor() : UpgradeLauncher {
    override fun launch(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivityVM.EXTRA_OPEN_UPGRADE, true)
        }
        context.startActivity(intent)
    }

    @Module
    @InstallIn(SingletonComponent::class)
    abstract class Mod {
        @Binds
        abstract fun bind(impl: UpgradeLauncherImpl): UpgradeLauncher
    }
}
