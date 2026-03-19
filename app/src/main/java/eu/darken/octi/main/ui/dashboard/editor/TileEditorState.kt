package eu.darken.octi.main.ui.dashboard.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import eu.darken.octi.main.ui.dashboard.TileLayoutConfig

data class TilePosition(val x: Float, val y: Float)

data class RowInfo(
    val yTop: Float,
    val yBottom: Float,
    val modules: List<String>,
)

sealed interface DropTarget {
    /** Insert as full-width before this row index. Use rows.size for "after last row". */
    data class BetweenRows(val beforeRowIndex: Int) : DropTarget

    /** Pair with the single tile in this row. */
    data class PairInRow(val rowIndex: Int) : DropTarget

    /** Swap with a tile in a 2-tile row. */
    data class SwapInRow(val targetModuleId: String) : DropTarget

    /** Horizontal swap within the same row. */
    data class SwapSameRow(val targetModuleId: String) : DropTarget

    /** Move to hidden section. */
    data object ToHidden : DropTarget

    /** Move from hidden to visible. */
    data object FromHidden : DropTarget
}

class TileEditorState(initialConfig: TileLayoutConfig) {
    private val allModules: List<String> = initialConfig.order.toList()

    var order by mutableStateOf(allModules)
        private set
    var wideModules by mutableStateOf(initialConfig.wideModules)
        private set
    var hiddenModules by mutableStateOf(initialConfig.hiddenModules)
        private set

    // Drag state — visual only during drag, layout applied on drop
    var draggedModuleId by mutableStateOf<String?>(null)
        private set
    var dragOffsetX by mutableFloatStateOf(0f)
        private set
    var dragOffsetY by mutableFloatStateOf(0f)
        private set

    // Drop target — computed during drag for visual feedback, applied on drop
    var dropTarget by mutableStateOf<DropTarget?>(null)
        private set

    // Position tracking: moduleId -> center in window coordinates
    val tilePositions = mutableStateMapOf<String, TilePosition>()
    var editorRows by mutableStateOf<List<RowInfo>>(emptyList())
    var dividerYPosition by mutableFloatStateOf(Float.MAX_VALUE)

    val visibleModules: List<String>
        get() = order.filter { it !in hiddenModules }

    val hiddenModulesList: List<String>
        get() = order.filter { it in hiddenModules }

    val isDragging: Boolean
        get() = draggedModuleId != null

    fun startDrag(moduleId: String) {
        draggedModuleId = moduleId
        dragOffsetX = 0f
        dragOffsetY = 0f
        dropTarget = null
    }

    fun updateDrag(deltaX: Float, deltaY: Float) {
        if (draggedModuleId == null) return
        dragOffsetX += deltaX
        dragOffsetY += deltaY
        dropTarget = computeDropTarget()
    }

    fun endDrag() {
        val dragged = draggedModuleId ?: return
        val target = dropTarget

        if (target != null) {
            applyDrop(dragged, target)
        }

        draggedModuleId = null
        dragOffsetX = 0f
        dragOffsetY = 0f
        dropTarget = null
    }

    private fun computeDropTarget(): DropTarget? {
        val dragged = draggedModuleId ?: return null
        val draggedPos = tilePositions[dragged] ?: return null
        val visualX = draggedPos.x + dragOffsetX
        val visualY = draggedPos.y + dragOffsetY
        val isCurrentlyHidden = dragged in hiddenModules

        // Divider crossing
        if (!isCurrentlyHidden && visualY > dividerYPosition) return DropTarget.ToHidden
        if (isCurrentlyHidden && visualY < dividerYPosition) return DropTarget.FromHidden

        // Hidden reorder — no visual target needed
        if (isCurrentlyHidden) return null

        // Find which row the visual center is in
        val draggedRow = editorRows.find { dragged in it.modules }
        val targetRow = editorRows.find { visualY in it.yTop..it.yBottom }

        if (targetRow != null && dragged !in targetRow.modules) {
            // Different row
            return if (targetRow.modules.size == 1) {
                val rowIndex = editorRows.indexOf(targetRow)
                DropTarget.PairInRow(rowIndex)
            } else {
                val closest = targetRow.modules.minByOrNull { moduleId ->
                    val pos = tilePositions[moduleId] ?: return@minByOrNull Float.MAX_VALUE
                    kotlin.math.abs(visualX - pos.x)
                } ?: return null
                DropTarget.SwapInRow(closest)
            }
        } else if (targetRow == null && editorRows.isNotEmpty()) {
            // Between rows
            val insertBefore = editorRows.indexOfFirst { visualY < it.yTop }
            val beforeIndex = if (insertBefore >= 0) insertBefore else editorRows.size
            val currentRowIndex = editorRows.indexOfFirst { dragged in it.modules }
            val currentRow = editorRows.getOrNull(currentRowIndex)
            val isInPairedRow = currentRow != null && currentRow.modules.size == 2

            // Don't show indicator if the tile is alone in its row and would stay in the same spot
            if (!isInPairedRow && (beforeIndex == currentRowIndex || beforeIndex == currentRowIndex + 1)) return null
            return DropTarget.BetweenRows(beforeIndex)
        } else if (draggedRow != null && draggedRow == targetRow && draggedRow.modules.size == 2) {
            // Same row — horizontal swap
            val other = draggedRow.modules.first { it != dragged }
            val otherPos = tilePositions[other] ?: return null
            val draggedOrigPos = tilePositions[dragged] ?: return null

            val shouldSwap = if (draggedOrigPos.x < otherPos.x) {
                visualX > otherPos.x
            } else {
                visualX < otherPos.x
            }
            return if (shouldSwap) DropTarget.SwapSameRow(other) else null
        }

        return null
    }

    private fun applyDrop(dragged: String, target: DropTarget) {
        when (target) {
            is DropTarget.ToHidden -> {
                hiddenModules = hiddenModules + dragged
                wideModules = wideModules - dragged
            }

            is DropTarget.FromHidden -> {
                hiddenModules = hiddenModules - dragged
            }

            is DropTarget.PairInRow -> {
                val draggedPos = tilePositions[dragged] ?: return
                val visualX = draggedPos.x + dragOffsetX
                val row = editorRows.getOrNull(target.rowIndex) ?: return
                val targetModule = row.modules.firstOrNull() ?: return
                val targetIndex = order.indexOf(targetModule)
                val targetPos = tilePositions[targetModule] ?: return
                val currentIndex = order.indexOf(dragged)

                val insertIndex = if (visualX < targetPos.x) targetIndex else targetIndex + 1
                val mutable = order.toMutableList()
                mutable.removeAt(currentIndex)
                val adjusted = if (currentIndex < insertIndex) insertIndex - 1 else insertIndex
                mutable.add(adjusted.coerceIn(0, mutable.size), dragged)
                order = mutable
                wideModules = wideModules - dragged - targetModule
            }

            is DropTarget.SwapInRow -> {
                val currentIndex = order.indexOf(dragged)
                val closestIndex = order.indexOf(target.targetModuleId)
                val mutable = order.toMutableList()
                mutable.removeAt(currentIndex)
                val adjusted = if (currentIndex < closestIndex) closestIndex - 1 else closestIndex
                mutable.add(adjusted.coerceIn(0, mutable.size), dragged)
                order = mutable
                wideModules = wideModules - dragged - target.targetModuleId
            }

            is DropTarget.BetweenRows -> {
                val currentIndex = order.indexOf(dragged)
                val insertBeforeRow = editorRows.getOrNull(target.beforeRowIndex)
                val insertIndex = if (insertBeforeRow != null) {
                    order.indexOf(insertBeforeRow.modules.first())
                } else {
                    val lastRow = editorRows.lastOrNull()
                    if (lastRow != null) order.indexOf(lastRow.modules.last()) + 1 else order.size
                }
                val mutable = order.toMutableList()
                mutable.removeAt(currentIndex)
                val adjusted = if (currentIndex < insertIndex) insertIndex - 1 else insertIndex
                mutable.add(adjusted.coerceIn(0, mutable.size), dragged)
                order = mutable
                wideModules = wideModules + dragged
            }

            is DropTarget.SwapSameRow -> {
                val currentIndex = order.indexOf(dragged)
                val otherIndex = order.indexOf(target.targetModuleId)
                val mutable = order.toMutableList()
                mutable[currentIndex] = target.targetModuleId
                mutable[otherIndex] = dragged
                order = mutable
            }
        }
    }

    fun toggleHidden(moduleId: String) {
        hiddenModules = if (moduleId in hiddenModules) {
            hiddenModules - moduleId
        } else {
            hiddenModules + moduleId
        }
    }

    fun toConfig(): TileLayoutConfig = TileLayoutConfig(
        order = order,
        wideModules = wideModules,
        hiddenModules = hiddenModules,
    )
}
