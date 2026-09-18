package com.amehrug.app.ui.notes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amehrug.app.R
import com.amehrug.app.model.NoteColor
import com.amehrug.app.ui.theme.noteContainerColor

/** The colour grid, shown for one or for many notes at once. */
@Composable
fun ColorDialog(onDismiss: () -> Unit, onPick: (NoteColor) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_color)) },
        text = {
            // Four to a row, laid out by hand rather than with FlowRow,
            // which is still an experimental layout.
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (row in NoteColor.entries.chunked(4)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        for (color in row) {
                            Surface(
                                shape = CircleShape,
                                color = noteContainerColor(color),
                                modifier = Modifier.size(44.dp).clickable { onPick(color) },
                            ) {
                                if (color == NoteColor.DEFAULT) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize(),
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_close),
                                            contentDescription = stringResource(R.string.color_none),
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

/**
 * Labels for the selected notes. A box that is ticked puts the label on every
 * one of them, unticked takes it off every one of them.
 */
@Composable
fun LabelDialog(
    labels: List<String>,
    checked: Set<String>,
    onToggle: (String, Boolean) -> Unit,
    onCreate: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newLabel by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_labels)) },
        text = {
            Column(modifier = Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                for (label in labels) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(label, label !in checked) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = label in checked,
                            onCheckedChange = { on -> onToggle(label, on) },
                        )
                        Text(text = label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                OutlinedTextField(
                    value = newLabel,
                    onValueChange = { newLabel = it },
                    label = { Text(stringResource(R.string.label_new)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
                TextButton(
                    onClick = {
                        onCreate(newLabel)
                        newLabel = ""
                    },
                    enabled = newLabel.isNotBlank(),
                ) {
                    Text(stringResource(R.string.label_add))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
        },
    )
}

/** Used for deleting for good, which nothing can undo. */
@Composable
fun ConfirmDialog(title: Int, message: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = { Text(stringResource(message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.action_continue)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
