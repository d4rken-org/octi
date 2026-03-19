package eu.darken.octi.main.ui.dashboard.editor

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Apps
import androidx.compose.material.icons.twotone.BatteryFull
import androidx.compose.material.icons.twotone.CellTower
import androidx.compose.material.icons.twotone.ContentPaste
import androidx.compose.material.icons.twotone.DragHandle
import androidx.compose.material.icons.twotone.QuestionMark
import androidx.compose.material.icons.twotone.VisibilityOff
import androidx.compose.material.icons.twotone.Wifi
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import eu.darken.octi.R
import eu.darken.octi.common.R as CommonR
import eu.darken.octi.common.compose.Preview2
import eu.darken.octi.common.compose.PreviewWrapper
import eu.darken.octi.main.ui.dashboard.TileLayoutConfig
import eu.darken.octi.modules.apps.R as AppsR
import eu.darken.octi.modules.clipboard.R as ClipboardR
import eu.darken.octi.modules.connectivity.R as ConnectivityR
import eu.darken.octi.modules.power.R as PowerR
import eu.darken.octi.modules.wifi.R as WifiR

@Composable
fun TileEditorCard(
    deviceName: String,
    initialConfig: TileLayoutConfig,
    onDone: (TileLayoutConfig) -> Unit,
    onCancel: () -> Unit,
    onReset: () -> Unit,
    onSaveAsDefault: (TileLayoutConfig) -> Unit,
) {
    val state = remember { TileEditorState(initialConfig) }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                return if (state.isDragging) available else Offset.Zero
            }
        }
    }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .nestedScroll(nestedScrollConnection)
            .animateContentSize(),
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = deviceName,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onCancel) {
                Text(stringResource(CommonR.string.general_cancel_action))
            }
            TextButton(onClick = { onDone(state.toConfig()) }) {
                Text(stringResource(R.string.dashboard_editor_done_action))
            }
        }

        HorizontalDivider()

        // Visible tiles in grid layout
        val visibleModules = state.visibleModules
        val wideModules = state.wideModules
        val rows = buildEditorRows(visibleModules, wideModules)
        val currentDropTarget = state.dropTarget

        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            rows.forEachIndexed { rowIndex, row ->
                // Drop indicator line BEFORE this row
                if (currentDropTarget is DropTarget.BetweenRows && currentDropTarget.beforeRowIndex == rowIndex) {
                    DropIndicatorLine()
                }

                val isWideRow = row.size == 1
                val isHeroRow = rowIndex == 0 && isWideRow
                val isRowHighlighted = currentDropTarget is DropTarget.PairInRow && currentDropTarget.rowIndex == rowIndex
                val rowContainsDragged = row.any { it == state.draggedModuleId }

                Row(
                    modifier = (if (isWideRow) {
                        Modifier.fillMaxWidth()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max)
                    }).then(
                        if (rowContainsDragged) Modifier.zIndex(10f) else Modifier
                    ).onGloballyPositioned { coords ->
                        val pos = coords.positionInWindow()
                        val rowInfo = RowInfo(
                            yTop = pos.y,
                            yBottom = pos.y + coords.size.height,
                            modules = row,
                        )
                        val currentRows = state.editorRows.toMutableList()
                        while (currentRows.size <= rowIndex) currentRows.add(rowInfo)
                        currentRows[rowIndex] = rowInfo
                        state.editorRows = currentRows.take(rows.size)
                    },
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    row.forEach { moduleId ->
                        val isSwapTarget = (currentDropTarget is DropTarget.SwapInRow && currentDropTarget.targetModuleId == moduleId) ||
                            (currentDropTarget is DropTarget.SwapSameRow && currentDropTarget.targetModuleId == moduleId)

                        val tileModifier = if (isWideRow) {
                            Modifier.fillMaxWidth()
                        } else {
                            Modifier.weight(1f).fillMaxHeight()
                        }
                        EditorGridTile(
                            moduleId = moduleId,
                            isHero = isHeroRow,
                            isHidden = false,
                            isDragged = state.draggedModuleId == moduleId,
                            isDropTarget = isRowHighlighted || isSwapTarget,
                            dragOffsetX = if (state.draggedModuleId == moduleId) state.dragOffsetX else 0f,
                            dragOffsetY = if (state.draggedModuleId == moduleId) state.dragOffsetY else 0f,
                            modifier = tileModifier,
                            state = state,
                        )
                    }
                }
            }

            // Drop indicator line AFTER last row
            if (currentDropTarget is DropTarget.BetweenRows && currentDropTarget.beforeRowIndex == rows.size) {
                DropIndicatorLine()
            }
        }

        // Always-visible hidden section — drop zone for hiding tiles
        HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))

        val isHiddenDropTarget = currentDropTarget is DropTarget.ToHidden
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .onGloballyPositioned { coords ->
                    val posInWindow = coords.positionInWindow()
                    state.dividerYPosition = posInWindow.y
                },
            shape = RoundedCornerShape(12.dp),
            color = if (isHiddenDropTarget) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.TwoTone.VisibilityOff,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.dashboard_editor_hidden_tiles_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }

                if (state.hiddenModulesList.isNotEmpty()) {
                    state.hiddenModulesList.forEach { moduleId ->
                        EditorGridTile(
                            moduleId = moduleId,
                            isHero = false,
                            isHidden = true,
                            isDragged = state.draggedModuleId == moduleId,
                            dragOffsetX = if (state.draggedModuleId == moduleId) state.dragOffsetX else 0f,
                            dragOffsetY = if (state.draggedModuleId == moduleId) state.dragOffsetY else 0f,
                            modifier = Modifier.fillMaxWidth(),
                            state = state,
                        )
                    }
                }
            }
        }

        // Bottom actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = {
                onReset()
                onCancel()
            }) {
                Text(stringResource(R.string.dashboard_editor_reset_action))
            }
            TextButton(onClick = {
                val config = state.toConfig()
                onSaveAsDefault(config)
                onDone(config)
            }) {
                Text(stringResource(R.string.dashboard_editor_save_default_action))
            }
        }
    }
}

@Composable
private fun DropIndicatorLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(3.dp)
            .padding(horizontal = 4.dp)
            .background(
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(1.5.dp),
            ),
    )
}

@Composable
private fun EditorGridTile(
    moduleId: String,
    isHero: Boolean,
    isHidden: Boolean,
    isDragged: Boolean,
    isDropTarget: Boolean = false,
    dragOffsetX: Float,
    dragOffsetY: Float,
    modifier: Modifier = Modifier,
    state: TileEditorState,
) {
    val icon = moduleIdToEditorIcon(moduleId)
    val label = moduleIdToEditorLabel(moduleId)

    val tileColor = when {
        isDropTarget -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        isHidden -> MaterialTheme.colorScheme.surfaceContainerLow
        isHero -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }

    val contentAlpha = if (isHidden) 0.38f else 1f

    Surface(
        modifier = modifier
            .onGloballyPositioned { coords ->
                val posInWindow = coords.positionInWindow()
                state.tilePositions[moduleId] = TilePosition(
                    x = posInWindow.x + coords.size.width / 2f,
                    y = posInWindow.y + coords.size.height / 2f,
                )
            }
            .then(
                if (isDragged) {
                    Modifier
                        .zIndex(1f)
                        .graphicsLayer {
                            translationX = dragOffsetX
                            translationY = dragOffsetY
                            shadowElevation = 8f
                        }
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(12.dp),
        color = tileColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 80.dp)
                .padding(12.dp)
                .pointerInput(moduleId) {
                    detectDragGestures(
                        onDragStart = { state.startDrag(moduleId) },
                        onDragEnd = { state.endDrag() },
                        onDragCancel = { state.endDrag() },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            state.updateDrag(dragAmount.x, dragAmount.y)
                        },
                    )
                },
        ) {
            // Top-left: Icon + Label
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = contentAlpha),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            // Bottom-right: Drag handle
            Icon(
                imageVector = Icons.TwoTone.DragHandle,
                contentDescription = null,
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.End),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
        }
    }
}

private fun buildEditorRows(visibleModules: List<String>, wideModules: Set<String>): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var i = 0
    while (i < visibleModules.size) {
        if (visibleModules[i] in wideModules) {
            rows.add(listOf(visibleModules[i]))
            i++
        } else if (i + 1 < visibleModules.size && visibleModules[i + 1] !in wideModules) {
            rows.add(listOf(visibleModules[i], visibleModules[i + 1]))
            i += 2
        } else {
            rows.add(listOf(visibleModules[i]))
            i++
        }
    }
    return rows
}

private fun moduleIdToEditorIcon(moduleId: String): ImageVector = when (moduleId) {
    "eu.darken.octi.module.core.power" -> Icons.TwoTone.BatteryFull
    "eu.darken.octi.module.core.wifi" -> Icons.TwoTone.Wifi
    "eu.darken.octi.module.core.apps" -> Icons.TwoTone.Apps
    "eu.darken.octi.module.core.clipboard" -> Icons.TwoTone.ContentPaste
    "eu.darken.octi.module.core.connectivity" -> Icons.TwoTone.CellTower
    else -> Icons.TwoTone.QuestionMark
}

@Composable
private fun moduleIdToEditorLabel(moduleId: String): String = when (moduleId) {
    "eu.darken.octi.module.core.power" -> stringResource(PowerR.string.module_power_label)
    "eu.darken.octi.module.core.wifi" -> stringResource(WifiR.string.module_wifi_label)
    "eu.darken.octi.module.core.apps" -> stringResource(AppsR.string.module_apps_label)
    "eu.darken.octi.module.core.clipboard" -> stringResource(ClipboardR.string.module_clipboard_label)
    "eu.darken.octi.module.core.connectivity" -> stringResource(ConnectivityR.string.module_connectivity_label)
    else -> moduleId
}

@Preview2
@Composable
private fun TileEditorCardPreview() = PreviewWrapper {
    TileEditorCard(
        deviceName = "Pixel 8",
        initialConfig = TileLayoutConfig(),
        onDone = {},
        onCancel = {},
        onReset = {},
        onSaveAsDefault = {},
    )
}
