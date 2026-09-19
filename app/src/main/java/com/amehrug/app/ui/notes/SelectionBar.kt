package com.amehrug.app.ui.notes

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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amehrug.app.R
import com.amehrug.app.model.Folder

/**
 * What takes the dock's place while notes are selected.
 *
 * It sits where the dock sits, at the bottom, because that is where the hand
 * already is once a note has been held down. The search pill on top does not
 * move and stays usable. Same shape, same elevation and same insets as the
 * dock, so the swap reads as one pill changing its buttons rather than two
 * pills trading places.
 */
@Composable
fun SelectionBar(
    count: Int,
    folder: Folder,
    onClose: () -> Unit,
    onPin: () -> Unit,
    onColor: () -> Unit,
    onLabels: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The actions are listed before anything is drawn, because how wide a
    // button can be depends on how many there are. Seven of them at a fixed
    // size overflowed a narrow phone.
    val actions = buildList {
        add(Triple(R.drawable.ic_close, R.string.action_clear_selection, onClose))
        when (folder) {
            Folder.DELETED -> {
                add(Triple(R.drawable.ic_restore, R.string.action_restore, onRestore))
                add(
                    Triple(
                        R.drawable.ic_delete,
                        R.string.action_delete_forever,
                        onDeleteForever,
                    ),
                )
            }
            Folder.ARCHIVED -> {
                add(Triple(R.drawable.ic_unarchive, R.string.action_unarchive, onRestore))
                add(Triple(R.drawable.ic_palette, R.string.action_color, onColor))
                add(Triple(R.drawable.ic_label, R.string.action_labels, onLabels))
                add(Triple(R.drawable.ic_export, R.string.action_export, onExport))
                add(Triple(R.drawable.ic_delete, R.string.action_delete, onTrash))
            }
            Folder.NOTES -> {
                add(Triple(R.drawable.ic_pin, R.string.action_pin, onPin))
                add(Triple(R.drawable.ic_palette, R.string.action_color, onColor))
                add(Triple(R.drawable.ic_label, R.string.action_labels, onLabels))
                add(Triple(R.drawable.ic_export, R.string.action_export, onExport))
                add(Triple(R.drawable.ic_archive, R.string.action_archive, onArchive))
                add(Triple(R.drawable.ic_delete, R.string.action_delete, onTrash))
            }
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // What is left once the pill's own padding and the counter are
        // taken out, shared between the buttons, never above the comfortable
        // size and never below the one a thumb can still hit.
        val room = maxWidth - 12.dp - 34.dp
        val slot = (room / actions.size).coerceIn(38.dp, 46.dp)
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            tonalElevation = 3.dp,
            shadowElevation = 3.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val first = actions.first()
                BarAction(first.first, first.second, slot, first.third)
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 5.dp),
                )
                for (action in actions.drop(1)) {
                    BarAction(action.first, action.second, slot, action.third)
                }
            }
        }
    }
}

/**
 * Every button answers under the thumb. The tick is the light one, the same
 * the text handles use, so a row of actions does not feel like a row of long
 * presses.
 */
@Composable
private fun BarAction(icon: Int, label: Int, slot: androidx.compose.ui.unit.Dp, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    IconButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = Modifier.size(slot),
    ) {
        Icon(painter = painterResource(icon), contentDescription = stringResource(label))
    }
}
