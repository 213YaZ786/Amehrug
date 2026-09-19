package com.amehrug.app.ui.notes

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.amehrug.app.AppGraph
import com.amehrug.app.R
import com.amehrug.app.diagnostics.Diagnostics
import com.amehrug.app.model.Attachment
import com.amehrug.app.model.AttachmentKind
import com.amehrug.app.model.ListItem
import com.amehrug.app.model.Note
import com.amehrug.app.model.NoteColor
import com.amehrug.app.model.NoteStyle
import com.amehrug.app.model.NoteTimestamp
import com.amehrug.app.model.NoteTimestamps
import com.amehrug.app.model.NoteType
import com.amehrug.app.model.TextSpan
import com.amehrug.app.model.TextSpans
import com.amehrug.app.ui.media.ImageLoader
import com.amehrug.app.ui.theme.noteContainerColor
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * One note, open.
 *
 * Nothing typed here is ever only in memory for long. The note is written
 * a second after the last keystroke, again the moment the app goes to the
 * background, and again when the screen is left, by the arrow or by the back
 * gesture. Losing a note because the system reclaimed the app is the thing
 * this screen exists to prevent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditorScreen(
    noteId: Long,
    newType: NoteType,
    timestamp: NoteTimestamp,
    onClose: () -> Unit,
) {
    val repository = AppGraph.notes
    var loaded by remember(noteId) { mutableStateOf(noteId == 0L) }
    var original by remember(noteId) { mutableStateOf(Note(type = newType)) }

    var title by remember(noteId) { mutableStateOf("") }
    // The body carries its selection, because styling applies to whatever is
    // selected and the field is the only place that knows.
    var body by remember(noteId) { mutableStateOf(TextFieldValue("")) }
    var spans by remember(noteId) { mutableStateOf(emptyList<TextSpan>()) }
    // A style asked for with nothing selected, waiting for the next
    // characters. -1 means nothing is waiting.
    var pending by remember(noteId) { mutableStateOf(-1) }
    // The creation date is the note's own, editable in place. While the
    // field has the focus it holds whatever is being typed, which is often
    // not a date yet, so the moment itself only moves when the text parses.
    var createdAt by remember(noteId) { mutableStateOf(0L) }
    var dateText by remember(noteId) { mutableStateOf("") }
    var editingDate by remember(noteId) { mutableStateOf(false) }
    var color by remember(noteId) { mutableStateOf(NoteColor.DEFAULT) }
    var pinned by remember(noteId) { mutableStateOf(false) }
    var type by remember(noteId) { mutableStateOf(newType) }
    val items = remember(noteId) { mutableListOf<ListItem>().toMutableStateList() }
    var showColors by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    val attachments = remember(noteId) { mutableListOf<Attachment>().toMutableStateList() }
    // The identifier the note ends up with. A picture cannot be attached to
    // a note that does not exist yet, so adding one saves it first.
    var savedId by remember(noteId) { mutableStateOf(noteId) }
    // What is already on disk, identifier stripped, so an autosave that would
    // rewrite the same thing is skipped.
    var lastWritten by remember(noteId) { mutableStateOf<Note?>(null) }
    val saveLock = remember(noteId) { Mutex() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    LaunchedEffect(noteId) {
        if (noteId != 0L) {
            val note = repository.getNote(noteId)
            if (note != null) {
                original = note
                title = note.title
                body = TextFieldValue(note.body)
                spans = note.spans
                color = note.color
                createdAt = note.createdAt
                pinned = note.pinned
                type = note.type
                items.clear()
                items.addAll(note.items)
                attachments.clear()
                attachments.addAll(note.attachments)
                savedId = note.id
                lastWritten = note.copy(id = 0L)
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
            body = body.text,
            spans = spans,
            items = items.toList(),
            labels = original.labels,
            attachments = attachments.toList(),
            reminders = original.reminders,
            pinned = pinned,
            createdAt = createdAt,
            modifiedAt = original.modifiedAt,
            deletedAt = original.deletedAt,
        ),
    )

    // Every write goes through this one function, under a lock. Without the
    // lock, two writes that overlap while the note still has no identifier
    // would each insert a row, and one note would become two.
    suspend fun store(note: Note) {
        saveLock.withLock {
            val target = if (savedId != 0L) note.copy(id = savedId) else note
            val id = repository.save(target)
            if (id > 0) {
                savedId = id
                lastWritten = target.copy(id = 0L)
            }
        }
    }

    fun save() {
        val note = current.value
        // appScope, not the screen's scope: the write must finish even though
        // the screen is already gone.
        AppGraph.appScope.launch { store(note) }
    }

    fun leave() {
        save()
        onClose()
    }

    // The back gesture used to be handled one level up, where it changed the
    // screen without writing anything. That is how a note was lost.
    BackHandler { leave() }

    // A pause of about a second after the last change is enough. It keeps the
    // database quiet while someone types and still means a sudden kill costs
    // a second of text rather than the whole note.
    LaunchedEffect(noteId, loaded) {
        if (!loaded) return@LaunchedEffect
        snapshotFlow { current.value }
            .distinctUntilChanged()
            .collectLatest { note ->
                if (note.copy(id = 0L) == lastWritten) return@collectLatest
                delay(AUTOSAVE_DELAY_MS)
                store(note)
            }
    }

    // Leaving the app is the case that has to work. onStop calls this back,
    // on a scope that outlives the screen, so the note is on disk before the
    // system is free to kill the process.
    DisposableEffect(noteId) {
        AppGraph.onPendingWrite { store(current.value) }
        onDispose { AppGraph.onPendingWrite(null) }
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
                } catch (cancel: CancellationException) {
                    // Leaving the screen cancels the coroutine, and
                    // CancellationException is an Exception. Caught below it would
                    // be reported as a failure and would break the cancellation.
                    throw cancel
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
        // safeDrawing so the keyboard counts as an edge. Everything that is
        // reached by hand lives at the bottom of this screen, so it has to
        // ride above the keyboard rather than under it.
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets),
        ) {
            // The date sits above everything, centred, and the note starts
            // right under it.
            DateField(
                shown = stampText(at = createdAt, format = timestamp),
                editing = editingDate,
                text = dateText,
                onFocus = { focused ->
                    editingDate = focused
                    dateText = if (focused) {
                        NoteTimestamps.editable(
                            at = if (createdAt > 0) createdAt else System.currentTimeMillis(),
                            zone = ZoneId.systemDefault(),
                            locale = context.resources.configuration.locales[0],
                        )
                    } else {
                        ""
                    }
                },
                onTextChange = { typed ->
                    dateText = typed
                    val moment = NoteTimestamps.parse(
                        text = typed,
                        fallback = if (createdAt > 0) createdAt else System.currentTimeMillis(),
                        zone = ZoneId.systemDefault(),
                    )
                    if (moment != null) createdAt = moment
                },
            )
            AttachmentStrip(attachments = attachments, onRemove = { removeAttachment(it) })
            if (!loaded) Spacer(modifier = Modifier.weight(1f))
            if (loaded) Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                when (type) {
                    NoteType.NOTE -> {
                        StyledEditorField(
                            value = body,
                            spans = spans,
                            onValueChange = { next ->
                                if (next.text != body.text) {
                                    spans = TextSpans.afterEdit(spans, body.text, next.text, pending)
                                    pending = -1
                                } else if (next.selection != body.selection) {
                                    // Moving the cursor drops a style that was
                                    // asked for and never used.
                                    pending = -1
                                }
                                body = next
                            },
                            placeholder = stringResource(R.string.editor_body),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState())
                                .padding(start = 20.dp, end = 8.dp),
                        )
                        StyleBar(
                            active = { kind ->
                                val selection = body.selection
                                if (selection.collapsed) {
                                    val base = if (pending >= 0) {
                                        pending
                                    } else {
                                        TextSpans.maskBefore(spans, body.text.length, selection.start)
                                    }
                                    base and kind.bit != 0
                                } else {
                                    TextSpans.covers(
                                        spans,
                                        body.text.length,
                                        selection.start,
                                        selection.end,
                                        kind,
                                    )
                                }
                            },
                            onToggle = { kind ->
                                val selection = body.selection
                                if (selection.collapsed) {
                                    val base = if (pending >= 0) {
                                        pending
                                    } else {
                                        TextSpans.maskBefore(spans, body.text.length, selection.start)
                                    }
                                    pending = base xor kind.bit
                                } else {
                                    spans = TextSpans.toggle(
                                        spans,
                                        body.text.length,
                                        selection.start,
                                        selection.end,
                                        kind,
                                    )
                                }
                            },
                        )
                    }
                    NoteType.LIST -> Checklist(items = items, modifier = Modifier.weight(1f))
                }
            }

            // Everything below here is within reach of one thumb, which is
            // the point of the layout: the colours, the title and the
            // actions all sit under the text rather than above it.
            if (showColors) {
                ColorRow(selected = color, onSelect = { color = it })
            }
            EditorField(
                value = title,
                onValueChange = { title = it },
                placeholder = stringResource(R.string.editor_title),
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                ),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
            )
            EditorDock(
                pinned = pinned,
                colorsOpen = showColors,
                onBack = { leave() },
                onPin = { pinned = !pinned },
                onImage = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                onExport = { showExport = true },
                onColors = { showColors = !showColors },
                onArchive = {
                    val note = current.value
                    AppGraph.appScope.launch {
                        store(note)
                        if (savedId > 0) repository.archive(listOf(savedId))
                    }
                    onClose()
                },
                onTrash = {
                    val note = current.value
                    AppGraph.appScope.launch {
                        store(note)
                        if (savedId > 0) repository.moveToTrash(listOf(savedId))
                    }
                    onClose()
                },
            )
        }
    }

    // The note as it stands, saved or not. Exporting what is on screen is
    // what someone means by exporting this note.
    ExportControl(
        visible = showExport,
        notes = { listOf(current.value) },
        onDismiss = { showExport = false },
    )
}

/**
 * The actions, in one pill at the bottom, the same shape as the dock on the
 * wall of notes.
 *
 * They used to be a row of icons in the top bar, which on a tall phone is
 * the one place a thumb cannot reach. Buttons shrink rather than overflow
 * when there are more of them than a narrow screen has room for, the same
 * rule the selection pill follows.
 */
@Composable
private fun EditorDock(
    pinned: Boolean,
    colorsOpen: Boolean,
    onBack: () -> Unit,
    onPin: () -> Unit,
    onImage: () -> Unit,
    onExport: () -> Unit,
    onColors: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
) {
    val actions = listOf(
        DockAction(R.drawable.ic_back, R.string.action_back, false, onBack),
        DockAction(
            if (pinned) R.drawable.ic_pin else R.drawable.ic_pin_off,
            R.string.action_pin,
            pinned,
            onPin,
        ),
        DockAction(R.drawable.ic_add, R.string.attachment_add, false, onImage),
        DockAction(R.drawable.ic_palette, R.string.action_color, colorsOpen, onColors),
        DockAction(R.drawable.ic_export, R.string.action_export, false, onExport),
        DockAction(R.drawable.ic_archive, R.string.action_archive, false, onArchive),
        DockAction(R.drawable.ic_delete, R.string.action_delete, false, onTrash),
    )
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val slot = ((maxWidth - 36.dp) / actions.size).coerceIn(38.dp, 50.dp)
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
                for (action in actions) {
                    DockButton(action, slot)
                }
            }
        }
    }
}

private data class DockAction(
    val icon: Int,
    val label: Int,
    val on: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun DockButton(action: DockAction, slot: androidx.compose.ui.unit.Dp) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier.size(slot),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = if (action.on) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                Color.Transparent
            },
            contentColor = if (action.on) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier
                .size(slot - 4.dp)
                .clickable {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    action.onClick()
                },
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Icon(
                    painter = painterResource(action.icon),
                    contentDescription = stringResource(action.label),
                )
            }
        }
    }
}

/**
 * The styles, in a column down the right hand edge, beside the text rather
 * than under it. It costs no height, which matters on a screen whose bottom
 * already carries the title and the actions.
 *
 * Each button is its own letter drawn in the style it applies, so there is
 * nothing to learn and no icon to misread. Pressed with text selected it
 * styles the selection. Pressed with nothing selected it arms the style for
 * whatever is typed next, which is how someone turns bold on before writing
 * the word rather than after.
 */
@Composable
private fun StyleBar(
    active: (NoteStyle) -> Boolean,
    onToggle: (NoteStyle) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    Column(
        modifier = Modifier.padding(end = 8.dp, top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        for (kind in STYLE_BUTTONS) {
            val on = active(kind)
            Surface(
                shape = CircleShape,
                color = if (on) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
                contentColor = if (on) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .size(44.dp)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggle(kind)
                    },
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = styleLetter(kind),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = if (kind == NoteStyle.BOLD) FontWeight.Bold else null,
                            fontStyle = if (kind == NoteStyle.ITALIC) FontStyle.Italic else null,
                            fontFamily = if (kind == NoteStyle.MONOSPACE) {
                                FontFamily.Monospace
                            } else {
                                null
                            },
                            textDecoration = if (kind == NoteStyle.STRIKETHROUGH) {
                                TextDecoration.LineThrough
                            } else {
                                null
                            },
                        ),
                    )
                }
            }
        }
    }
}

private val STYLE_BUTTONS = listOf(
    NoteStyle.BOLD,
    NoteStyle.ITALIC,
    NoteStyle.MONOSPACE,
    NoteStyle.STRIKETHROUGH,
)

private fun styleLetter(kind: NoteStyle): String = when (kind) {
    NoteStyle.BOLD -> "B"
    NoteStyle.ITALIC -> "I"
    NoteStyle.MONOSPACE -> "M"
    NoteStyle.STRIKETHROUGH -> "S"
    NoteStyle.LINK -> "L"
}

/**
 * The body field. Same as [EditorField] but carrying a selection and the
 * styles, which the plain one does not need.
 */
@Composable
private fun StyledEditorField(
    value: TextFieldValue,
    spans: List<TextSpan>,
    onValueChange: (TextFieldValue) -> Unit,
    placeholder: String,
    style: androidx.compose.ui.text.TextStyle,
    modifier: Modifier = Modifier,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    Box(modifier = modifier.fillMaxWidth()) {
        if (value.text.isEmpty()) {
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
            visualTransformation = remember(spans, linkColor) {
                SpanTransformation(spans, linkColor)
            },
            modifier = Modifier.fillMaxWidth(),
        )
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
                // Full width, so an alignment carried by the style reaches
                // the placeholder as well as the text.
                modifier = Modifier.fillMaxWidth(),
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

/**
 * The creation date, centred at the top of the note.
 *
 * Resting, it shows the date in whatever format the settings ask for.
 * Touched, it swaps to a plain day, month and year, because two of those
 * formats cannot be read back from their own text. Typing a real date moves
 * the note's date. Typing anything else changes nothing, which is what has
 * to happen while a date is still half typed.
 */
@Composable
private fun DateField(
    shown: String?,
    editing: Boolean,
    text: String,
    onFocus: (Boolean) -> Unit,
    onTextChange: (String) -> Unit,
) {
    if (shown == null && !editing) return
    BasicTextField(
        value = if (editing) text else shown.orEmpty(),
        onValueChange = onTextChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.labelLarge.copy(
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .onFocusChanged { state -> onFocus(state.isFocused) },
    )
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

/** How long typing has to stop before the note is written. */
private const val AUTOSAVE_DELAY_MS = 1_000L
