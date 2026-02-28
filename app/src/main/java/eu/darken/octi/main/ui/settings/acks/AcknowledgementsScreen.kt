package eu.darken.octi.main.ui.settings.acks

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import eu.darken.octi.R
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.common.settings.SettingsBaseItem
import eu.darken.octi.common.settings.SettingsCategoryHeader

@Composable
fun AcknowledgementsScreenHost(vm: AcknowledgementsVM = hiltViewModel()) {
    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    AcknowledgementsScreen(
        onNavigateUp = { vm.navUp() },
        onOpenUrl = { url -> vm.openUrl(url) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AcknowledgementsScreen(
    onNavigateUp: () -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.settings_acknowledgements_label)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.TwoTone.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(modifier = Modifier.padding(innerPadding)) {
            item {
                SettingsCategoryHeader(text = stringResource(R.string.general_thank_you_label))
            }
            item {
                SettingsBaseItem(
                    title = "Jakob Moeller",
                    subtitle = stringResource(R.string.acks_thanks_jakob_desc),
                    onClick = { onOpenUrl("https://www.linkedin.com/in/jakob-m%C3%B6ller/") },
                )
            }
            item {
                SettingsBaseItem(
                    title = "Max Patchs",
                    subtitle = stringResource(R.string.acks_thanks_maxpatchs_desc),
                    onClick = { onOpenUrl("https://twitter.com/maxpatchs") },
                )
            }
            item {
                SettingsBaseItem(
                    title = "crowdin.com",
                    subtitle = stringResource(R.string.acks_thanks_crowdin_desc),
                    onClick = { onOpenUrl("https://crowdin.com/") },
                )
            }
            item {
                SettingsCategoryHeader(text = stringResource(R.string.settings_licenses_label))
            }
            item {
                SettingsBaseItem(
                    title = "Material Design Icons",
                    subtitle = "materialdesignicons.com (SIL Open Font License 1.1 / Attribution 4.0 International)",
                    onClick = { onOpenUrl("https://github.com/Templarian/MaterialDesign") },
                )
            }
            item {
                SettingsBaseItem(
                    title = "Lottie",
                    subtitle = "Airbnb's Lottie for Android. (APACHE 2.0)",
                    onClick = { onOpenUrl("https://github.com/airbnb/lottie-android") },
                )
            }
            item {
                SettingsBaseItem(
                    title = "Kotlin",
                    subtitle = "The Kotlin Programming Language. (APACHE 2.0)",
                    onClick = { onOpenUrl("https://github.com/JetBrains/kotlin") },
                )
            }
            item {
                SettingsBaseItem(
                    title = "Dagger",
                    subtitle = "A fast dependency injector for Android and Java. (APACHE 2.0)",
                    onClick = { onOpenUrl("https://github.com/google/dagger") },
                )
            }
            item {
                SettingsBaseItem(
                    title = "Android",
                    subtitle = "Android Open Source Project (APACHE 2.0)",
                    onClick = { onOpenUrl("https://source.android.com/source/licenses.html") },
                )
            }
            item {
                SettingsBaseItem(
                    title = "Android",
                    subtitle = "The Android robot is reproduced or modified from work created and shared by Google and used according to terms described in the Creative Commons 3.0 Attribution License.",
                    onClick = { onOpenUrl("https://developer.android.com/distribute/tools/promote/brand.html") },
                )
            }
        }
    }
}

@Preview2
@Composable
private fun AcknowledgementsScreenPreview() = PreviewWrapper {
    AcknowledgementsScreen(onNavigateUp = {}, onOpenUrl = {})
}
