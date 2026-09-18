package com.amehrug.app.ui.notes

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amehrug.app.R
import com.amehrug.app.model.Folder

/** What replaces the search pill while notes are selected. */
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
) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.ic_close),
                    contentDescription = stringResource(R.string.action_clear_selection),
                )
            }
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(end = 8.dp),
            )
            when (folder) {
                Folder.DELETED -> {
                    BarAction(R.drawable.ic_restore, R.string.action_restore, onRestore)
                    BarAction(R.drawable.ic_delete, R.string.action_delete_forever, onDeleteForever)
                }
                Folder.ARCHIVED -> {
                    BarAction(R.drawable.ic_restore, R.string.action_unarchive, onRestore)
                    BarAction(R.drawable.ic_palette, R.string.action_color, onColor)
                    BarAction(R.drawable.ic_checklist, R.string.action_labels, onLabels)
                    BarAction(R.drawable.ic_delete, R.string.action_delete, onTrash)
                }
                Folder.NOTES -> {
                    BarAction(R.drawable.ic_pin, R.string.action_pin, onPin)
                    BarAction(R.drawable.ic_palette, R.string.action_color, onColor)
                    BarAction(R.drawable.ic_checklist, R.string.action_labels, onLabels)
                    BarAction(R.drawable.ic_archive, R.string.action_archive, onArchive)
                    BarAction(R.drawable.ic_delete, R.string.action_delete, onTrash)
                }
            }
        }
    }
}

@Composable
private fun BarAction(icon: Int, label: Int, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(painter = painterResource(icon), contentDescription = stringResource(label))
    }
}
