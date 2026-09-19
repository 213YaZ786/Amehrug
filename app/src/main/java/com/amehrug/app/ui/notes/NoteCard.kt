package com.amehrug.app.ui.notes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amehrug.app.R
import com.amehrug.app.model.AttachmentKind
import com.amehrug.app.model.Note
import com.amehrug.app.model.NoteTimestamp
import com.amehrug.app.model.NoteType
import com.amehrug.app.ui.theme.noteContainerColor
import com.amehrug.app.ui.theme.noteContentColor
import com.amehrug.app.ui.theme.noteOutlineColor
import kotlinx.coroutines.launch

private const val PREVIEW_LINES = 8
private const val PREVIEW_ITEMS = 6

private val CARD_CORNER = 22.dp
private val RESTING_OUTLINE = 2.dp
private val SELECTED_OUTLINE = 3.5.dp

@Composable
fun NoteCard(
    note: Note,
    timestamp: NoteTimestamp,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    // No gesture lives here. The wall reads taps, long presses and the
    // selection sweep in one place, because a detector on the card would
    // race the one on the grid for the same long press.

    // A card answers being picked: it settles back a little and gives one
    // short shake, rather than wobbling for as long as it stays selected.
    val scale = remember { Animatable(1f) }
    val tilt = remember { Animatable(0f) }
    LaunchedEffect(selected) {
        if (selected) {
            launch {
                scale.animateTo(
                    targetValue = 0.955f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                )
            }
            tilt.animateTo(1.6f, tween(70))
            tilt.animateTo(-1.6f, tween(90))
            tilt.animateTo(0.8f, tween(80))
            tilt.animateTo(0f, tween(90))
        } else {
            launch { tilt.animateTo(0f, tween(120)) }
            scale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy),
            )
        }
    }

    // The card always carries a border, so a note reads as an object rather
    // than as a patch of colour. It is drawn through Card's own border
    // parameter rather than Modifier.border, because the card paints its
    // background after the modifier chain and covers a stroke drawn there.
    val border by animateDpAsState(
        targetValue = if (selected) SELECTED_OUTLINE else RESTING_OUTLINE,
        label = "card outline width",
    )
    val resting = noteOutlineColor(note.color)
    val borderColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else resting,
        label = "card outline colour",
    )
    val shape = RoundedCornerShape(CARD_CORNER)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale.value
                scaleY = scale.value
                rotationZ = tilt.value
            },
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = noteContainerColor(note.color),
            contentColor = noteContentColor(),
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(border, borderColor),
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
            NoteStamp(at = note.createdAt, format = timestamp)
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
                        // The preview carries the styles too, otherwise a
                        // note reads differently on the wall and in the
                        // editor.
                        text = annotate(
                            text = note.body,
                            spans = note.spans,
                            linkColor = MaterialTheme.colorScheme.primary,
                        ),
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

/**
 * The creation date, above everything else on the card. Nothing is drawn
 * when the format is off or the note carries no date.
 */
@Composable
private fun NoteStamp(at: Long, format: NoteTimestamp) {
    val text = stampText(at = at, format = format) ?: return
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
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
