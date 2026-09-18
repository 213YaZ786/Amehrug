package com.amehrug.app.ui.notes

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amehrug.app.AppGraph
import com.amehrug.app.R
import com.amehrug.app.model.Attachment
import com.amehrug.app.model.AttachmentKind
import com.amehrug.app.ui.media.ImageLoader

/** One decrypted picture, scaled for the space it is going into. */
@Composable
fun AttachmentImage(name: String, targetPx: Int, modifier: Modifier = Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = name, key2 = targetPx) {
        value = ImageLoader.load(AppGraph.attachments, name, targetPx)
    }
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val picture = bitmap
        if (picture != null) {
            Image(
                bitmap = picture.asImageBitmap(),
                contentDescription = stringResource(R.string.attachment_image),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The strip under the title in the editor, with a viewer on tap. */
@Composable
fun AttachmentStrip(
    attachments: List<Attachment>,
    onRemove: (Attachment) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (attachments.isEmpty()) return
    var opened by remember { mutableStateOf<Attachment?>(null) }
    LazyRow(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(attachments, key = { it.id }) { attachment ->
            when (attachment.kind) {
                AttachmentKind.IMAGE -> AttachmentImage(
                    name = attachment.fileName,
                    targetPx = 320,
                    modifier = Modifier
                        .size(112.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { opened = attachment },
                )
                else -> OtherFile(attachment = attachment, onClick = { opened = attachment })
            }
        }
    }

    val current = opened
    if (current != null) {
        AlertDialog(
            onDismissRequest = { opened = null },
            title = { Text(fileLabel(current)) },
            text = {
                if (current.kind == AttachmentKind.IMAGE) {
                    AttachmentImage(
                        name = current.fileName,
                        targetPx = 1280,
                        modifier = Modifier.fillMaxWidth().height(320.dp).clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Text(stringResource(R.string.attachment_no_player))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemove(current)
                        opened = null
                    },
                ) {
                    Text(stringResource(R.string.attachment_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { opened = null }) { Text(stringResource(R.string.action_close)) }
            },
        )
    }
}

@Composable
private fun OtherFile(attachment: Attachment, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .size(width = 160.dp, height = 112.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_more),
            contentDescription = null,
            modifier = Modifier.padding(end = 10.dp),
        )
        Text(text = fileLabel(attachment), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun fileLabel(attachment: Attachment): String = when (attachment.kind) {
    AttachmentKind.IMAGE -> stringResource(R.string.attachment_image)
    AttachmentKind.AUDIO -> {
        val seconds = ((attachment.durationMs ?: 0) / 1000).toInt()
        stringResource(R.string.attachment_audio, seconds / 60, seconds % 60)
    }
    AttachmentKind.FILE -> stringResource(R.string.attachment_file)
}
