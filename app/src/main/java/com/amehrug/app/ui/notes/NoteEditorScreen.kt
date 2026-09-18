package com.amehrug.app.ui.notes

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.amehrug.app.AppGraph
import com.amehrug.app.R
import com.amehrug.app.diagnostics.Diagnostics
import com.amehrug.app.model.Attachment
import com.amehrug.app.model.AttachmentKind
import com.amehrug.app.model.ListItem
import com.amehrug.app.model.Note
import com.amehrug.app.model.NoteColor
import com.amehrug.app.model.NoteType
import com.amehrug.app.ui.media.ImageLoader
import com.amehrug.app.ui.theme.noteContainerColor
import kotlinx.coroutines.launch

/**
 * One note, open. Typing changes state only. The note is written when the
 * screen leaves, once, which is what makes the back gesture feel instant and
 * keeps the database quiet while someone types.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(noteId: Long, newType: NoteType, onClose: () -> Unit) {
    val repository = AppGraph.notes
    var loaded by remember(noteId) { mutableStateOf(noteId == 0L) }
    var original by remember(noteId) { mutableStateOf(Note(type = newType)) }

    var title by remember(noteId) { mutableStateOf("") }
    var body by remember(noteId) { mutableStateOf("") }
    var color by remember(noteId) { mutableStateOf(NoteColor.DEFAULT) }
    var pinned by remember(noteId) { mutableStateOf(false) }
    var type by remember(noteId) { mutableStateOf(newType) }
    val items = remember(noteId) { mutableListOf<ListItem>().toMutableStateList() }
    var showColors by remember { mutableStateOf(false) }
    val attachments = remember(noteId) { mutableListOf<Attachment>().toMutableStateList() }
    // The identifier the note ends up with. A picture cannot be attached to
    // a note that does not exist yet, so adding one saves it first.
    var savedId by remember(noteId) { mutableStateOf(noteId) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(noteId) {
        if (noteId != 0L) {
            val note = repository.getNote(noteId)
            if (note != null) {
                original = note
                title = note.title
                body = note.body
                color = note.color
                pinned = note.pinned
                type = note.type
                items.clear()
                items.addAll(note.items)
                attachments.clear()
                attachments.addAll(note.attachments)
                savedId = note.id
            }
            loaded = true
        }
    }

    // Read at the moment the screen leaves, never a stale copy.
    val current = rememberUpdatedState(
        Note(
            id = savedId,
            type = type,
            folder = original.folder,
            color = color,
            title = title,
            body = body,
            items = items.toList(),
            labels = original.labels,
            attachments = attachments.toList(),
            reminders = original.reminders,
            pinned = pinned,
            createdAt = original.createdAt,
            modifiedAt = original.modifiedAt,
            deletedAt = original.deletedAt,
        ),
    )

    fun save() {
        val note = current.value
        // appScope, not the screen's scope: the write must finish even though
        // the screen is already gone.
        AppGraph.appScope.launch { repository.save(note) }
    }

    fun leave() {
        save()
        onClose()
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    // The note must exist before a file can point at it.
                    if (savedId == 0L) {
                        val id = repository.save(current.value, force = true)
                        if (id > 0) savedId = id
                    }
                    if (savedId == 0L) return@launch
                    val stored = context.contentResolver.openInputStream(uri)?.use { input ->
                        AppGraph.attachments.write(input)
                    } ?: return@launch
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val attachment = Attachment(
                        kind = AttachmentKind.IMAGE,
                        fileName = stored,
                        mimeType = mime,
                        sizeBytes = AppGraph.attachments.sizeOf(stored),
                        createdAt = System.currentTimeMillis(),
                    )
                    val rowId = repository.addAttachment(savedId, attachment, attachments.size)
                    attachments.add(attachment.copy(id = rowId))
                } catch (e: Exception) {
                    Diagnostics.log.error("media", "adding a picture", e)
                    Toast.makeText(context, R.string.attachment_failed, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun removeAttachment(attachment: Attachment) {
        attachments.remove(attachment)
        scope.launch {
            val file = repository.removeAttachment(attachment.id) ?: return@launch
            ImageLoader.forget(file)
            AppGraph.attachments.delete(file)
        }
    }

    val background by animateColorAsState(
        targetValue = noteContainerColor(color),
        animationSpec = spring(stiffness = 220f),
        label = "note background",
    )

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = background,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = { leave() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { pinned = !pinned }) {
                        Icon(
                            painter = painterResource(
                                if (pinned) R.drawable.ic_pin else R.drawable.ic_pin_off,
                            ),
                            contentDescription = stringResource(R.string.action_pin),
                        )
                    }
                    IconButton(
                        onClick = {
                            picker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_add),
                            contentDescription = stringResource(R.string.attachment_add),
                        )
                    }
                    IconButton(onClick = { showColors = !showColors }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_palette),
                            contentDescription = stringResource(R.string.action_color),
                        )
                    }
                    IconButton(
                        onClick = {
                            val note = current.value
                            AppGraph.appScope.launch {
                                val id = repository.save(note)
                                if (id > 0) repository.archive(listOf(id))
                            }
                            onClose()
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_archive),
                            contentDescription = stringResource(R.string.action_archive),
                        )
                    }
                    IconButton(
                        onClick = {
                            val note = current.value
                            AppGraph.appScope.launch {
                                val id = repository.save(note)
                                if (id > 0) repository.moveToTrash(listOf(id))
                            }
                            onClose()
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_delete),
                            contentDescription = stringResource(R.string.action_delete),
                        )
                    }
                },
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            if (showColors) {
                ColorRow(selected = color, onSelect = { color = it })
            }
            EditorField(
                value = title,
                onValueChange = { title = it },
                placeholder = stringResource(R.string.editor_title),
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            AttachmentStrip(attachments = attachments, onRemove = { removeAttachment(it) })
            if (!loaded) return@Column
            when (type) {
                NoteType.NOTE -> EditorField(
                    value = body,
                    onValueChange = { body = it },
                    placeholder = stringResource(R.string.editor_body),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                )
                NoteType.LIST -> Checklist(items = items, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun EditorField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                style = style,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Checklist(items: SnapshotStateList<ListItem>, modifier: Modifier = Modifier) {
    LazyColumn(modifier = modifier.fillMaxWidth()) {
        items(items.size) { index ->
            val item = items[index]
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = item.checked,
                    onCheckedChange = { checked -> items[index] = item.copy(checked = checked) },
                )
                EditorField(
                    value = item.body,
                    onValueChange = { text -> items[index] = item.copy(body = text) },
                    placeholder = stringResource(R.string.editor_item),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { items.removeAt(index) }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.action_remove_item),
                    )
                }
            }
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { items.add(ListItem("")) }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_add),
                    contentDescription = null,
                    modifier = Modifier.padding(start = 12.dp, end = 20.dp),
                )
                Text(
                    text = stringResource(R.string.editor_add_item),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ColorRow(selected: NoteColor, onSelect: (NoteColor) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (option in NoteColor.entries) {
            val chosen = option == selected
            Surface(
                shape = CircleShape,
                color = noteContainerColor(option),
                modifier = Modifier
                    .size(38.dp)
                    .border(
                        width = if (chosen) 3.dp else 1.dp,
                        color = if (chosen) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape,
                    )
                    .clickable { onSelect(option) },
            ) {
                if (chosen) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
