package eu.darken.octi.common.uix

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.annotation.MenuRes
import androidx.annotation.XmlRes
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDirections
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceFragmentCompat
import eu.darken.octi.common.datastore.PreferenceScreenData
import eu.darken.octi.common.debug.logging.Logging.Priority.VERBOSE
import eu.darken.octi.common.debug.logging.log
import eu.darken.octi.main.ui.settings.SettingsFragment
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

abstract class PreferenceFragment2 : PreferenceFragmentCompat() {

    abstract val settings: PreferenceScreenData

    @get:XmlRes
    abstract val preferenceFile: Int

    val toolbar: Toolbar
        get() = (parentFragment as SettingsFragment).toolbar

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        toolbar.menu.clear()
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = settings.mapper
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        refreshPreferenceScreen()

        settings.dataStore.data
            .onEach {
                log(VERBOSE) { "Preferences changed." }
                onPreferencesChanged()
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)

        super.onViewCreated(view, savedInstanceState)
    }

    override fun getCallbackFragment(): Fragment? = parentFragment

    fun refreshPreferenceScreen() {
        if (preferenceScreen != null) preferenceScreen = null
        addPreferencesFromResource(preferenceFile)
        onPreferencesCreated()
    }

    open fun onPreferencesCreated() {

    }

    open fun onPreferencesChanged() {

    }

    fun NavDirections.navigate() = findNavController().navigate(this)

    fun setupMenu(@MenuRes menuResId: Int, block: (MenuItem) -> Unit) {
        toolbar.apply {
            menu.clear()
            inflateMenu(menuResId)
            setOnMenuItemClickListener {
                block(it)
                true
            }
        }
    }

}