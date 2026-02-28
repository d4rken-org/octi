package eu.darken.octi.modules.apps.ui.appslist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.twotone.ArrowBack
import androidx.compose.material.icons.twotone.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import eu.darken.octi.R
import eu.darken.octi.common.compose.waitForState
import eu.darken.octi.common.error.ErrorEventHandler
import eu.darken.octi.common.navigation.NavigationEventHandler
import eu.darken.octi.modules.apps.R as AppsR
import eu.darken.octi.modules.apps.core.AppsSortMode
import eu.darken.octi.modules.apps.core.installerIconRes

@Composable
fun AppsListScreenHost(
    deviceId: String,
    vm: AppsListVM = hiltViewModel(),
) {
    LaunchedEffect(Unit) { vm.initialize(deviceId) }

    ErrorEventHandler(vm)
    NavigationEventHandler(vm)

    val context = LocalContext.current

    LaunchedEffect(vm.events) {
        vm.events.collect { event ->
            when (event) {
                is AppListAction.OpenAppOrStore -> {
                    try {
                        context.startActivity(event.intent)
                    } catch (_: Exception) {
                        try {
                            context.startActivity(event.fallback)
                        } catch (_: Exception) {
                            // Ignore
                        }
                    }
                }
            }
        }
    }

    val state by waitForState(vm.state)

    var showSortDialog by rememberSaveable { mutableStateOf(false) }

    state?.let {
        AppsListScreen(
            state = it,
            onNavigateUp = { vm.navUp() },
            onSort = { showSortDialog = true },
        )
    }

    if (showSortDialog) {
        SortDialog(
            currentMode = state?.sortMode ?: AppsSortMode.NAME,
            onSelect = { mode ->
                vm.updateSortMode(mode)
                showSortDialog = false
            },
            onDismiss = { showSortDialog = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppsListScreen(
    state: AppsListVM.State,
    onNavigateUp: () -> Unit,
    onSort: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(AppsR.string.module_apps_list_title))
                        if (state.deviceLabel.isNotEmpty()) {
                            Text(
                                text = stringResource(R.string.device_x_label, state.deviceLabel),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.TwoTone.ArrowBack, null)
                    }
                },
                actions = {
                    IconButton(onClick = onSort) {
                        Icon(Icons.TwoTone.Sort, stringResource(AppsR.string.module_apps_sort_label))
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            items(
                items = state.items,
                key = { it.pkg.packageName },
            ) { item ->
                AppItem(item = item)
            }
        }
    }
}

@Composable
private fun AppItem(item: AppsListVM.PkgItem) {
    val pkg = item.pkg
    Row(
        modifier = Modifier
            .clickable { item.onClick() }
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = pkg,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pkg.label ?: pkg.packageName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${pkg.versionName} (${pkg.versionCode}) - ${pkg.packageName}",
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (pkg.installerPkg != null) {
            Icon(
                painter = painterResource(pkg.installerIconRes),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun SortDialog(
    currentMode: AppsSortMode,
    onSelect: (AppsSortMode) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(AppsR.string.module_apps_sort_label)) },
        text = {
            Column {
                AppsSortMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .clickable { onSelect(mode) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = mode == currentMode,
                            onClick = { onSelect(mode) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = mode.label.get(context))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
