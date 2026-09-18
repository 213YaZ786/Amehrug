package com.amehrug.app.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amehrug.app.R
import com.amehrug.app.model.DockItem

/**
 * The dock: one pill holding every button, floating over the wall rather
 * than sitting on the edge, clear of the gesture bar.
 *
 * Hold a button and drag it sideways to move it. The order is written to the
 * settings table, so it survives a restart.
 *
 * Built from a Surface and a Row. Material's own floating toolbar lives on
 * the 1.5.0 alpha line, and this project stays on the stable one.
 */
@Composable
fun NoteDock(
    order: List<DockItem>,
    columns: Int,
    selected: DockItem?,
    onAction: (DockItem) -> Unit,
    onReorder: (List<DockItem>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val live = remember { order.toMutableStateList() }
    // The stored order wins whenever it changes underneath, which happens
    // once at startup when the settings arrive.
    LaunchedEffect(order) {
        if (live.toList() != order) {
            live.clear()
            live.addAll(order)
        }
    }

    var dragging by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Six buttons at 54dp need 324dp plus the pill's own padding, which
        // is more than a narrow phone has. They shrink rather than overflow.
        val slot = if (maxWidth < 400.dp) 46.dp else 54.dp
        val slotPx = with(LocalDensity.current) { slot.toPx() }

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for ((index, item) in live.withIndex()) {
                    val held = index == dragging
                    val on = item == selected && dragging < 0
                    Box(
                        modifier = Modifier
                            .size(slot)
                            .graphicsLayer {
                                translationX = if (held) dragOffset else 0f
                                val scale = if (held) 1.18f else 1f
                                scaleX = scale
                                scaleY = scale
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(slot - 6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (on) {
                                        MaterialTheme.colorScheme.secondaryContainer
                                    } else {
                                        Color.Transparent
                                    },
                                )
                                .clickable(enabled = dragging < 0) { onAction(item) }
                                .pointerInput(index, live.size, slotPx) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            dragging = index
                                            dragOffset = 0f
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            val from = dragging
                                            if (from >= 0) {
                                                dragOffset += amount.x
                                                // Half a slot of travel is
                                                // one place moved.
                                                val step = when {
                                                    dragOffset > slotPx / 2 -> 1
                                                    dragOffset < -slotPx / 2 -> -1
                                                    else -> 0
                                                }
                                                val target = from + step
                                                if (step != 0 && target in live.indices) {
                                                    live.add(target, live.removeAt(from))
                                                    dragging = target
                                                    dragOffset -= step * slotPx
                                                } else if (step != 0) {
                                                    // Already at the end,
                                                    // so it stops there.
                                                    dragOffset = step * slotPx / 2
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            if (dragging >= 0) onReorder(live.toList())
                                            dragging = -1
                                            dragOffset = 0f
                                        },
                                        onDragCancel = {
                                            dragging = -1
                                            dragOffset = 0f
                                        },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(dockIcon(item, columns)),
                                contentDescription = stringResource(dockLabel(item)),
                                tint = if (on) {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun dockIcon(item: DockItem, columns: Int): Int = when (item) {
    DockItem.HOME -> R.drawable.ic_note
    DockItem.SEARCH -> R.drawable.ic_search
    DockItem.LAYOUT -> if (columns == 2) R.drawable.ic_list else R.drawable.ic_grid
    DockItem.LIST -> R.drawable.ic_checklist
    DockItem.NOTE -> R.drawable.ic_add
    DockItem.SETTINGS -> R.drawable.ic_settings
}

private fun dockLabel(item: DockItem): Int = when (item) {
    DockItem.HOME -> R.string.folder_notes
    DockItem.SEARCH -> R.string.action_search
    DockItem.LAYOUT -> R.string.action_layout
    DockItem.LIST -> R.string.action_new_list
    DockItem.NOTE -> R.string.action_new_note
    DockItem.SETTINGS -> R.string.settings_title
}
