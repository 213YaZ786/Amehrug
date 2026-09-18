package com.amehrug.app.ui.notes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.amehrug.app.AppGraph
import com.amehrug.app.R
import com.amehrug.app.model.Folder
import com.amehrug.app.model.Note
import com.amehrug.app.model.NoteType
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
    onOpen: (Long) -> Unit,
    onCreate: (NoteType) -> Unit,
) {
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
    val chosen = notes.filter { it.id in selected }
    val ids = chosen.map { it.id }

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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
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
                )
            } else {
                NotesSearchBar(
                    query = query,
                    onQueryChange = onQueryChange,
                    columns = columns,
                    onColumnsChange = onColumnsChange,
                    onMenu = onMenu,
                    folder = folder,
                )
            }
        },
        floatingActionButton = {
            if (folder == Folder.NOTES && selected.isEmpty()) {
                CreateButtons(onCreate = onCreate)
            }
        },
    ) { insets ->
        if (notes.isEmpty()) {
            EmptyState(folder = folder, searching = query.isNotBlank(), modifier = Modifier.padding(insets))
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(columns),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = insets.calculateTopPadding() + 4.dp,
                    bottom = insets.calculateBottomPadding() + 96.dp,
                ),
                verticalItemSpacing = 10.dp,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (pinned.isNotEmpty() && query.isBlank()) {
                    item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                        SectionLabel(stringResource(R.string.section_pinned))
                    }
                    items(pinned, key = { "pinned-${it.id}" }) { note ->
                        NoteCard(
                            note = note,
                            selected = note.id in selected,
                            onClick = {
                                if (selected.isEmpty()) {
                                    onOpen(note.id)
                                } else {
                                    selected = toggle(selected, note.id)
                                }
                            },
                            onLongClick = { selected = toggle(selected, note.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (others.isNotEmpty()) {
                        item(span = androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan.FullLine) {
                            SectionLabel(stringResource(R.string.section_others))
                        }
                    }
                }
                items(if (query.isBlank()) others else notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        selected = note.id in selected,
                        onClick = {
                            if (selected.isEmpty()) {
                                onOpen(note.id)
                            } else {
                                selected = toggle(selected, note.id)
                            }
                        },
                        onLongClick = { selected = toggle(selected, note.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }

    Dialogs(
        asking = asking,
        labelsOfSelection = chosen.flatMap { it.labels }.toSet(),
        allLabels = allLabels,
        onClose = { asking = Ask.NONE },
        onColor = { color -> act { repository.setColor(ids, color) } },
        onLabel = { name, on -> scope.launch { repository.setLabel(ids, name, on) } },
        onDelete = {
            val targets = ids
            act {
                val files = repository.deleteForever(targets)
                for (file in files) AppGraph.attachments.delete(file)
            }
        },
    )
}

private enum class Ask { NONE, COLOR, LABELS, DELETE }

private fun toggle(selected: Set<Long>, id: Long): Set<Long> =
    if (id in selected) selected - id else selected + id

/**
 * Keep's pill on top. Written from Material pieces rather than the M3
 * SearchBar, whose shape moves from release to release and which brings a
 * full screen mode this app does not want.
 */
@Composable
private fun NotesSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    onMenu: () -> Unit,
    folder: Folder,
) {
    val keyboard = LocalSoftwareKeyboardController.current
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onMenu) {
                Icon(
                    painter = painterResource(R.drawable.ic_menu),
                    contentDescription = stringResource(R.string.action_menu),
                )
            }
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                if (query.isEmpty()) {
                    Text(
                        text = stringResource(folderHint(folder)),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                    modifier = Modifier.fillMaxWidth(),
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
            IconButton(onClick = { onColumnsChange(if (columns == 2) 1 else 2) }) {
                Icon(
                    painter = painterResource(
                        if (columns == 2) R.drawable.ic_list else R.drawable.ic_grid,
                    ),
                    contentDescription = stringResource(R.string.action_layout),
                )
            }
        }
    }
}

@Composable
private fun CreateButtons(onCreate: (NoteType) -> Unit) {
    val appear = remember { MutableTransitionState(false).apply { targetState = true } }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedVisibility(
            visibleState = appear,
            enter = scaleIn(spring(dampingRatio = 0.55f, stiffness = 380f)) + fadeIn(),
            exit = scaleOut() + fadeOut(),
        ) {
            FloatingActionButton(
                onClick = { onCreate(NoteType.LIST) },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_checklist),
                    contentDescription = stringResource(R.string.action_new_list),
                )
            }
        }
        FloatingActionButton(onClick = { onCreate(NoteType.NOTE) }) {
            Icon(
                painter = painterResource(R.drawable.ic_add),
                contentDescription = stringResource(R.string.action_new_note),
            )
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
    }
}

private fun folderHint(folder: Folder): Int = when (folder) {
    Folder.NOTES -> R.string.search_notes
    Folder.ARCHIVED -> R.string.search_archive
    Folder.DELETED -> R.string.search_trash
}
