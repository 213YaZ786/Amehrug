package com.amehrug.app.ui.notes

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.amehrug.app.AppGraph
import com.amehrug.app.R
import com.amehrug.app.model.DockItem
import com.amehrug.app.model.Folder
import com.amehrug.app.model.Note
import com.amehrug.app.model.NoteTimestamp
import com.amehrug.app.model.NoteType
import com.amehrug.app.ui.topBarInsets
import kotlinx.coroutines.launch

/**
 * The wall of notes: a staggered grid of cards, pinned ones first, a search
 * field on top and two buttons to add a note or a list.
 *
 * Everything the screen shows comes from one Flow of the database, so a note
 * that changes anywhere appears here without a refresh.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesListScreen(
    folder: Folder,
    label: String?,
    query: String,
    onQueryChange: (String) -> Unit,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    onMenu: () -> Unit,
    showMenu: Boolean,
    dockOrder: List<DockItem>,
    timestamp: NoteTimestamp,
    onDockReorder: (List<DockItem>) -> Unit,
    onHome: () -> Unit,
    onSettings: () -> Unit,
    onOpen: (Long) -> Unit,
    onCreate: (NoteType) -> Unit,
) {
    val searchFocus = remember { FocusRequester() }
    val repository = AppGraph.notes
    val notes by produceState(
        initialValue = emptyList<Note>(),
        key1 = folder,
        key2 = query,
        key3 = label,
    ) {
        val flow = when {
            query.isNotBlank() -> repository.search(query)
            label != null -> repository.observeLabel(label)
            else -> repository.observeFolder(folder)
        }
        flow.collect { value = it }
    }
    val allLabels by produceState(initialValue = emptyList<String>()) {
        repository.observeLabels().collect { value = it }
    }
    val scope = rememberCoroutineScope()

    // Selection lives here: leaving the screen drops it, which is what
    // someone expects after opening a note.
    var selected by remember { mutableStateOf(emptySet<Long>()) }
    var asking by remember { mutableStateOf(Ask.NONE) }
    var exporting by remember { mutableStateOf(false) }
    val chosen = notes.filter { it.id in selected }
    val ids = chosen.map { it.id }

    // A sweep in progress. The grid stops answering the finger while it
    // lasts, otherwise the wall scrolls under the selection.
    var sweeping by remember { mutableStateOf(false) }
    // -1, 0 or 1. Only the direction, so the scrolling effect below is not
    // restarted on every frame.
    var edge by remember { mutableStateOf(0f) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    // Where the cards are. Plain objects, not state: they are written during
    // layout and read during a gesture, and making them observable would
    // start a layout that writes them again.
    val cards = remember { CardBounds() }
    val haptics = LocalHapticFeedback.current
    // The gesture block below is created once and outlives recomposition, so
    // it must not hold on to the first callback it was handed.
    val open by rememberUpdatedState(onOpen)

    fun clear() {
        selected = emptySet()
        asking = Ask.NONE
    }

    fun act(block: suspend () -> Unit) {
        scope.launch { block() }
        clear()
    }

    val pinned = notes.filter { it.pinned }
    val others = notes.filterNot { it.pinned }
    val gridState = rememberLazyStaggeredGridState()

    // The width decides how many columns fit. The toggle in the bar stays
    // meaningful everywhere: it picks the density, the screen picks the
    // count. Measured rather than read from a window size class, so no extra
    // library is needed and a foldable that opens is handled the same way.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val gridColumns = when {
            maxWidth >= 1200.dp -> columns * 3
            maxWidth >= 840.dp -> columns * 2
            maxWidth >= 600.dp -> columns + 1
            else -> columns
        }

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                NotesSearchBar(
                    query = query,
                    onQueryChange = onQueryChange,
                    onMenu = onMenu,
                    showMenu = showMenu,
                    folder = folder,
                    focusRequester = searchFocus,
                )
            },
        ) { insets ->
            Box(modifier = Modifier.fillMaxSize()) {
                if (notes.isEmpty()) {
                    EmptyState(folder = folder, searching = query.isNotBlank(), modifier = Modifier.padding(insets))
                } else {
                    // Selecting by hand: hold a card to start, then slide
                    // across the wall to take the neighbours. Reading the
                    // gesture once here rather than card by card is what
                    // makes the slide possible at all.
                    fun take(point: Offset) {
                        val id = cards.at(point) ?: return
                        if (id !in selected) {
                            selected = selected + id
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                    }

                    LazyVerticalStaggeredGrid(
                        columns = StaggeredGridCells.Fixed(gridColumns),
                        state = gridState,
                        userScrollEnabled = !sweeping,
                        modifier = Modifier
                            .fillMaxSize()
                            // Right before the gesture reader, with no layout
                            // modifier between them, so both speak of the
                            // same coordinates.
                            .onGloballyPositioned { cards.origin = it }
                            .pointerInput(Unit) {
                                detectSelectionGestures(
                                    onTap = { point ->
                                        val id = cards.at(point)
                                        if (id != null) {
                                            if (selected.isEmpty()) {
                                                open(id)
                                            } else {
                                                selected = toggle(selected, id)
                                            }
                                        }
                                    },
                                    onSweepStart = { point ->
                                        val id = cards.at(point)
                                        if (id != null) {
                                            sweeping = true
                                            finger = point
                                            selected = toggle(selected, id)
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                    },
                                    onSweepMove = { point ->
                                        if (sweeping) {
                                            finger = point
                                            take(point)
                                        }
                                    },
                                    onEdge = { direction ->
                                        if (sweeping) edge = direction
                                    },
                                    onSweepEnd = {
                                        sweeping = false
                                        edge = 0f
                                    },
                                )
                            },
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            end = 12.dp,
                            top = insets.calculateTopPadding() + 4.dp,
                            bottom = insets.calculateBottomPadding() + 120.dp,
                        ),
                        verticalItemSpacing = 10.dp,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (pinned.isNotEmpty() && query.isBlank()) {
                            item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                                SectionLabel(stringResource(R.string.section_pinned))
                            }
                            items(pinned, key = { "pinned-${it.id}" }) { note ->
                                DisposableEffect(note.id) {
                                    onDispose { cards.forget(note.id) }
                                }
                                NoteCard(
                                    note = note,
                                    timestamp = timestamp,
                                    selected = note.id in selected,
                                    modifier = Modifier
                                        .animateItem()
                                        .onGloballyPositioned { cards.put(note.id, it) },
                                )
                            }
                            if (others.isNotEmpty()) {
                                item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                                    SectionLabel(stringResource(R.string.section_others))
                                }
                            }
                        }
                        items(if (query.isBlank()) others else notes, key = { it.id }) { note ->
                            DisposableEffect(note.id) {
                                onDispose { cards.forget(note.id) }
                            }
                            NoteCard(
                                note = note,
                                timestamp = timestamp,
                                selected = note.id in selected,
                                modifier = Modifier
                                    .animateItem()
                                    .onGloballyPositioned { cards.put(note.id, it) },
                            )
                        }
                    }
                }

                // The dock floats over the wall, so the grid keeps its
                // full height and cards pass under the pill. While notes are
                // selected the same spot carries the actions instead, which
                // keeps every button under the thumb that just held a card.
                if (selected.isNotEmpty()) {
                    SelectionBar(
                        count = selected.size,
                        folder = folder,
                        onClose = { clear() },
                        onPin = {
                            val pin = chosen.any { !it.pinned }
                            act { repository.setPinned(ids, pin) }
                        },
                        onColor = { asking = Ask.COLOR },
                        onLabels = { asking = Ask.LABELS },
                        onArchive = { act { repository.archive(ids) } },
                        onTrash = { act { repository.moveToTrash(ids) } },
                        onRestore = { act { repository.restore(ids) } },
                        onDeleteForever = { asking = Ask.DELETE },
                        onExport = { exporting = true },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                } else {
                    if (folder == Folder.DELETED && notes.isNotEmpty()) {
                        // On the right, clear of the dock, so emptying the
                        // bin is never a neighbour of a button pressed by
                        // habit.
                        Button(
                            onClick = { asking = Ask.EMPTY },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .windowInsetsPadding(
                                    WindowInsets.safeDrawing.only(
                                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                    ),
                                )
                                .padding(end = 16.dp, bottom = 88.dp),
                        ) {
                            Text(stringResource(R.string.action_empty_trash))
                        }
                    }
                    NoteDock(
                        order = dockOrder,
                        columns = columns,
                        selected = if (query.isBlank()) DockItem.HOME else DockItem.SEARCH,
                        onAction = { item ->
                            when (item) {
                                DockItem.HOME -> onHome()
                                DockItem.SEARCH -> searchFocus.requestFocus()
                                DockItem.LAYOUT -> onColumnsChange(if (columns == 2) 1 else 2)
                                DockItem.LIST -> onCreate(NoteType.LIST)
                                DockItem.NOTE -> onCreate(NoteType.NOTE)
                                DockItem.SETTINGS -> onSettings()
                            }
                        },
                        onReorder = onDockReorder,
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
        }
    }

    // The wall keeps moving while the finger rests against an edge, and
    // whatever arrives under it joins the selection.
    LaunchedEffect(edge) {
        if (edge != 0f) {
            while (true) {
                gridState.scrollBy(edge * 26f)
                val id = cards.at(finger)
                if (id != null && id !in selected) {
                    selected = selected + id
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                withFrameNanos { }
            }
        }
    }

    // The selection is read when a format is picked, so it can be cleared
    // afterwards without the export losing its notes.
    ExportControl(
        visible = exporting,
        notes = { chosen },
        onDismiss = { exporting = false },
    )

    Dialogs(
        asking = asking,
        labelsOfSelection = chosen.flatMap { it.labels }.toSet(),
        allLabels = allLabels,
        onClose = { asking = Ask.NONE },
        onColor = { color -> act { repository.setColor(ids, color) } },
        onLabel = { name, on -> scope.launch { repository.setLabel(ids, name, on) } },
        onEmpty = {
            // Everything in the bin, not just what is selected.
            val targets = notes.map { it.id }
            act {
                val files = repository.deleteForever(targets)
                for (file in files) AppGraph.attachments.delete(file)
            }
        },
        onDelete = {
            val targets = ids
            act {
                val files = repository.deleteForever(targets)
                for (file in files) AppGraph.attachments.delete(file)
            }
        },
    )
}

private enum class Ask { NONE, COLOR, LABELS, DELETE, EMPTY }

private fun toggle(selected: Set<Long>, id: Long): Set<Long> =
    if (id in selected) selected - id else selected + id

/**
 * The pill on top, floating over the wall rather than glued to the edges.
 *
 * Written from Material pieces rather than the M3 SearchBar, whose shape
 * moves from release to release and which brings a full screen mode this app
 * does not want. Unlike a TopAppBar it applies no window inset of its own,
 * which is why topBarInsets is here: without it the pill sits under the
 * status bar.
 */
@Composable
private fun NotesSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onMenu: () -> Unit,
    showMenu: Boolean,
    folder: Folder,
    focusRequester: FocusRequester,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(topBarInsets)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            // Capped, so the pill stays a pill on a tablet instead of
            // stretching into a banner.
            modifier = Modifier.fillMaxWidth().widthIn(max = 720.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showMenu) {
                    IconButton(onClick = onMenu) {
                        Icon(
                            painter = painterResource(R.drawable.ic_menu),
                            contentDescription = stringResource(R.string.action_menu),
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Box(
                    modifier = Modifier.weight(1f),
                    // Centred while empty, which is the resting state and the
                    // one that is looked at. It moves to the start as soon as
                    // there is text to read.
                    contentAlignment = if (query.isEmpty()) Alignment.Center else Alignment.CenterStart,
                ) {
                    if (query.isEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                painter = painterResource(R.drawable.ic_search),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(folderHint(folder)),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_close),
                            contentDescription = stringResource(R.string.action_clear_search),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 6.dp, top = 10.dp, bottom = 2.dp),
    )
}

@Composable
private fun EmptyState(folder: Folder, searching: Boolean, modifier: Modifier = Modifier) {
    val message = when {
        searching -> R.string.empty_search
        folder == Folder.ARCHIVED -> R.string.empty_archive
        folder == Folder.DELETED -> R.string.empty_trash
        else -> R.string.empty_notes
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(40.dp),
        )
    }
}

@Composable
private fun Dialogs(
    asking: Ask,
    labelsOfSelection: Set<String>,
    allLabels: List<String>,
    onClose: () -> Unit,
    onColor: (com.amehrug.app.model.NoteColor) -> Unit,
    onLabel: (String, Boolean) -> Unit,
    onDelete: () -> Unit,
    onEmpty: () -> Unit,
) {
    when (asking) {
        Ask.NONE -> Unit
        Ask.COLOR -> ColorDialog(onDismiss = onClose, onPick = onColor)
        Ask.LABELS -> LabelDialog(
            labels = allLabels,
            checked = labelsOfSelection,
            onToggle = onLabel,
            onCreate = { name -> onLabel(name, true) },
            onDismiss = onClose,
        )
        Ask.DELETE -> ConfirmDialog(
            title = R.string.action_delete_forever,
            message = R.string.delete_forever_message,
            onConfirm = onDelete,
            onDismiss = onClose,
        )
        Ask.EMPTY -> ConfirmDialog(
            title = R.string.action_empty_trash,
            message = R.string.empty_trash_message,
            onConfirm = onEmpty,
            onDismiss = onClose,
        )
    }
}

private fun folderHint(folder: Folder): Int = when (folder) {
    Folder.NOTES -> R.string.search_notes
    Folder.ARCHIVED -> R.string.search_archive
    Folder.DELETED -> R.string.search_trash
}
