package com.amehrug.app

import android.os.Bundle
import android.os.Process
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.PermanentDrawerSheet
import androidx.compose.material3.PermanentNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.amehrug.app.diagnostics.Diagnostics
import com.amehrug.app.diagnostics.DiagnosticsScreen
import com.amehrug.app.model.AppSettings
import com.amehrug.app.model.Folder
import com.amehrug.app.model.NoteType
import com.amehrug.app.ui.LockScreen
import com.amehrug.app.ui.SettingsScreen
import com.amehrug.app.ui.notes.NoteEditorScreen
import com.amehrug.app.ui.notes.NotesListScreen
import com.amehrug.app.ui.theme.AmehrugTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Hides notes from screenshots, screen recording and the recents
        // preview. Always on for now. Roadmap task 19 turns it into a
        // setting that stays on by default.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        setContent {
            AmehrugTheme {
                AmehrugApp()
            }
        }
        if (savedInstanceState == null) {
            val sinceStart = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()
            Diagnostics.log.info("start", "process to activity $sinceStart ms")
        }
    }

    override fun onStart() {
        super.onStart()
        AppGraph.lock.onForeground()
    }

    override fun onStop() {
        super.onStop()
        // Before anything else: whatever is being typed reaches the disk.
        AppGraph.flushPendingWrite()
        val leftAt = AppGraph.lock.onBackground()
        if (leftAt != null) {
            // appScope, because the activity may be gone before this lands.
            AppGraph.appScope.launch {
                try {
                    AppGraph.settings.setLockLeftAt(leftAt)
                } catch (e: Exception) {
                    Diagnostics.log.error("lock", "writing the departure time", e)
                }
            }
        }
    }
}

private sealed interface SettingsState {
    data object Loading : SettingsState

    data object Failed : SettingsState

    data class Ready(val settings: AppSettings) : SettingsState
}

@Composable
private fun AmehrugApp() {
    // Opening the database happens here, off the main thread.
    val state by produceState<SettingsState>(SettingsState.Loading) {
        try {
            // Read once, then wiped: a window granted by a departure time
            // is spent the moment it is used, so a process killed while in
            // front cannot leave an old one lying around to be reused.
            val restored = AppGraph.settings.lockLeftAt()
            AppGraph.lock.restore(restored)
            if (restored != null) AppGraph.settings.setLockLeftAt(null)
            AppGraph.settings.settings.collect { settings ->
                AppGraph.lock.onSettings(settings)
                value = SettingsState.Ready(settings)
            }
        } catch (cancel: CancellationException) {
            // Leaving the screen cancels the coroutine, and
            // CancellationException is an Exception. Caught below it would
            // be reported as a failure and would break the cancellation.
            throw cancel
        } catch (e: Exception) {
            Diagnostics.log.error("settings", "reading settings", e)
            value = SettingsState.Failed
        }
    }
    val locked by AppGraph.lock.locked.collectAsState()

    when (val current = state) {
        SettingsState.Loading -> MessageScreen(stringResource(R.string.opening))
        // Nothing is shown when the settings cannot be read: the lock state
        // is unknown, so the notes stay hidden.
        SettingsState.Failed -> MessageScreen(stringResource(R.string.open_failed))
        is SettingsState.Ready ->
            if (locked) {
                LockScreen(
                    method = current.settings.lockMethod,
                    onUnlocked = { AppGraph.lock.unlock() },
                )
            } else {
                AmehrugRoot(current.settings)
            }
    }
}

private sealed interface Screen {
    data object Notes : Screen

    data class Editor(val noteId: Long, val type: NoteType) : Screen

    data object Settings : Screen

    data object Diagnostics : Screen
}

@Composable
private fun AmehrugRoot(settings: AppSettings) {
    // Saved as plain strings and numbers, which survive a rotation without a
    // custom saver.
    var screenName by rememberSaveable { mutableStateOf(NOTES) }
    var editorId by rememberSaveable { mutableStateOf(0L) }
    var editorType by rememberSaveable { mutableStateOf(NoteType.NOTE.name) }
    var folderName by rememberSaveable { mutableStateOf(Folder.NOTES.name) }
    var labelFilter by rememberSaveable { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    var columns by rememberSaveable { mutableStateOf(2) }

    val folder = Folder.entries.firstOrNull { it.name == folderName } ?: Folder.NOTES
    val screen: Screen = when (screenName) {
        SETTINGS -> Screen.Settings
        DIAGNOSTICS -> Screen.Diagnostics
        EDITOR -> Screen.Editor(
            noteId = editorId,
            type = NoteType.entries.firstOrNull { it.name == editorType } ?: NoteType.NOTE,
        )
        else -> Screen.Notes
    }

    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val labels by produceState(initialValue = emptyList<String>()) {
        AppGraph.notes.observeLabels().collect { value = it }
    }

    // The editor handles its own back, because it has to write the note
    // first. Everything else just goes home.
    BackHandler(enabled = screenName != NOTES && screenName != EDITOR) { screenName = NOTES }

    // One number the diagnostic log can be read for, when a bar looks like
    // it is sitting under the status bar. Zero here means the window is not
    // handing insets to Compose at all, which is a different bug from a
    // component that forgets to apply them.
    val density = LocalDensity.current
    val safeTop = WindowInsets.safeDrawing.getTop(density)
    LaunchedEffect(safeTop) {
        Diagnostics.log.info("insets", "safe drawing top $safeTop px")
    }

    // The sheet scrolls. Without it, a few labels push Settings and
    // Diagnostics off the bottom of the screen, where nothing can reach them.
    val drawerContent: @Composable () -> Unit = {
        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(start = 28.dp, top = 24.dp, bottom = 16.dp),
            )
            DrawerRow(R.string.folder_notes, R.drawable.ic_note, folder == Folder.NOTES) {
                folderName = Folder.NOTES.name
                labelFilter = ""
                screenName = NOTES
                scope.launch { drawer.close() }
            }
            DrawerRow(R.string.folder_archive, R.drawable.ic_archive, folder == Folder.ARCHIVED) {
                folderName = Folder.ARCHIVED.name
                labelFilter = ""
                screenName = NOTES
                scope.launch { drawer.close() }
            }
            DrawerRow(R.string.folder_trash, R.drawable.ic_delete, folder == Folder.DELETED) {
                folderName = Folder.DELETED.name
                labelFilter = ""
                screenName = NOTES
                scope.launch { drawer.close() }
            }
            if (labels.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.drawer_labels),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 28.dp, top = 16.dp, bottom = 4.dp),
                )
                for (label in labels) {
                    DrawerRow(
                        label = label,
                        icon = R.drawable.ic_label,
                        selected = labelFilter == label,
                    ) {
                        labelFilter = label
                        folderName = Folder.NOTES.name
                        screenName = NOTES
                        scope.launch { drawer.close() }
                    }
                }
            }
            DrawerRow(R.string.settings_title, R.drawable.ic_settings, screenName == SETTINGS) {
                screenName = SETTINGS
                scope.launch { drawer.close() }
            }
            DrawerRow(R.string.diagnostics_title, R.drawable.ic_bug, screenName == DIAGNOSTICS) {
                screenName = DIAGNOSTICS
                scope.launch { drawer.close() }
            }
        }
    }

    // showMenu is false when the drawer is already on screen: a hamburger
    // that opens what is open makes no sense.
    val body: @Composable (Boolean) -> Unit = { showMenu ->
        // One spring for every screen change, so opening a note and coming
        // back feel like the same movement played forwards and backwards.
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                val enter = scaleIn(
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f),
                    initialScale = 0.92f,
                ) + fadeIn(spring(stiffness = 420f))
                val exit = scaleOut(
                    animationSpec = spring(dampingRatio = 0.9f, stiffness = 380f),
                    targetScale = 0.96f,
                ) + fadeOut(spring(stiffness = 420f))
                enter togetherWith exit
            },
            label = "screen",
        ) { current ->
            when (current) {
                Screen.Notes -> NotesListScreen(
                    folder = folder,
                    label = labelFilter.ifEmpty { null },
                    query = query,
                    onQueryChange = { query = it },
                    columns = columns,
                    onColumnsChange = { columns = it },
                    onMenu = { scope.launch { drawer.open() } },
                    showMenu = showMenu,
                    dockOrder = settings.dockOrder,
                    timestamp = settings.noteTimestamp,
                    onDockReorder = { order ->
                        AppGraph.appScope.launch { AppGraph.settings.setDockOrder(order) }
                    },
                    onHome = {
                        folderName = Folder.NOTES.name
                        labelFilter = ""
                        query = ""
                        screenName = NOTES
                    },
                    onSettings = { screenName = SETTINGS },
                    onOpen = { id ->
                        editorId = id
                        editorType = NoteType.NOTE.name
                        screenName = EDITOR
                    },
                    onCreate = { type ->
                        editorId = 0L
                        editorType = type.name
                        screenName = EDITOR
                    },
                )
                is Screen.Editor -> NoteEditorScreen(
                    noteId = current.noteId,
                    newType = current.type,
                    timestamp = settings.noteTimestamp,
                    onClose = { screenName = NOTES },
                )
                Screen.Settings -> SettingsScreen(settings = settings, onBack = { screenName = NOTES })
                Screen.Diagnostics -> DiagnosticsScreen(onBack = { screenName = NOTES })
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // A drawer that has to be pulled open every time wastes a tablet.
        // Past 840dp it stays on screen instead, which is where Material
        // puts the boundary between a phone layout and an expanded one.
        if (maxWidth >= 840.dp) {
            PermanentNavigationDrawer(
                drawerContent = {
                    PermanentDrawerSheet(modifier = Modifier.width(300.dp)) { drawerContent() }
                },
            ) {
                body(false)
            }
        } else {
            ModalNavigationDrawer(
                drawerState = drawer,
                gesturesEnabled = screenName == NOTES,
                drawerContent = { ModalDrawerSheet { drawerContent() } },
            ) {
                body(true)
            }
        }
    }
}

private const val NOTES = "notes"
private const val EDITOR = "editor"
private const val SETTINGS = "settings"
private const val DIAGNOSTICS = "diagnostics"

@Composable
private fun DrawerRow(label: Int, icon: Int, selected: Boolean, onClick: () -> Unit) {
    DrawerRow(stringResource(label), icon, selected, onClick)
}

@Composable
private fun DrawerRow(label: String, icon: Int, selected: Boolean, onClick: () -> Unit) {
    NavigationDrawerItem(
        label = { Text(label) },
        icon = {
            Icon(painter = painterResource(icon), contentDescription = null)
        },
        selected = selected,
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
    )
}

@Composable
private fun MessageScreen(message: String) {
    Scaffold(modifier = Modifier.fillMaxSize()) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator()
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
