package com.amehrug.app.ui.notes

import androidx.compose.foundation.layout.Box
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
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
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
                BarAction(R.drawable.ic_close, R.string.action_clear_selection, onClose)
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
                when (folder) {
                    Folder.DELETED -> {
                        BarAction(R.drawable.ic_restore, R.string.action_restore, onRestore)
                        BarAction(R.drawable.ic_delete, R.string.action_delete_forever, onDeleteForever)
                    }
                    Folder.ARCHIVED -> {
                        BarAction(R.drawable.ic_unarchive, R.string.action_unarchive, onRestore)
                        BarAction(R.drawable.ic_palette, R.string.action_color, onColor)
                        BarAction(R.drawable.ic_label, R.string.action_labels, onLabels)
                        BarAction(R.drawable.ic_delete, R.string.action_delete, onTrash)
                    }
                    Folder.NOTES -> {
                        BarAction(R.drawable.ic_pin, R.string.action_pin, onPin)
                        BarAction(R.drawable.ic_palette, R.string.action_color, onColor)
                        BarAction(R.drawable.ic_label, R.string.action_labels, onLabels)
                        BarAction(R.drawable.ic_archive, R.string.action_archive, onArchive)
                        BarAction(R.drawable.ic_delete, R.string.action_delete, onTrash)
                    }
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
private fun BarAction(icon: Int, label: Int, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    IconButton(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        modifier = Modifier.size(46.dp),
    ) {
        Icon(painter = painterResource(icon), contentDescription = stringResource(label))
    }
}
