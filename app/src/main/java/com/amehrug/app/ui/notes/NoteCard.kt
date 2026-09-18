package com.amehrug.app.ui.notes

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amehrug.app.R
import com.amehrug.app.model.AttachmentKind
import com.amehrug.app.model.Note
import com.amehrug.app.model.NoteType
import com.amehrug.app.ui.theme.noteContainerColor
import com.amehrug.app.ui.theme.noteContentColor

private const val PREVIEW_LINES = 8
private const val PREVIEW_ITEMS = 6

@Composable
fun NoteCard(
    note: Note,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // detectTapGestures rather than combinedClickable, which is still an
    // experimental foundation API.
    val border by animateDpAsState(
        targetValue = if (selected) 3.dp else 0.dp,
        label = "selection border",
    )
    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = border,
                color = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(16.dp),
            )
            .pointerInput(note.id) {
                detectTapGestures(onTap = { onClick() }, onLongPress = { onLongClick() })
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = noteContainerColor(note.color),
            contentColor = noteContentColor(),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        val picture = note.attachments.firstOrNull { it.kind == AttachmentKind.IMAGE }
        if (picture != null) {
            AttachmentImage(
                name = picture.fileName,
                targetPx = 480,
                modifier = Modifier.fillMaxWidth().height(132.dp),
            )
        }
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (note.title.isNotBlank()) {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (note.type) {
                NoteType.NOTE -> if (note.body.isNotBlank()) {
                    Text(
                        text = note.body,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = PREVIEW_LINES,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                NoteType.LIST -> ListPreview(note)
            }
            if (note.labels.isNotEmpty()) {
                Text(
                    text = note.labels.joinToString(separator = "   "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ListPreview(note: Note) {
    val shown = note.items.take(PREVIEW_ITEMS)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (item in shown) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(
                        if (item.checked) R.drawable.ic_check else R.drawable.ic_checklist,
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(14.dp).padding(end = 0.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = item.body,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Default,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
        val rest = note.items.size - shown.size
        if (rest > 0) {
            Text(
                text = pluralStringResource(R.plurals.more_items, rest, rest),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
