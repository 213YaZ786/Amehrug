package com.yaz.notes.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.yaz.notes.AppGraph
import com.yaz.notes.BuildConfigInfo
import com.yaz.notes.R
import com.yaz.notes.model.AppSettings
import com.yaz.notes.model.LockMethod
import com.yaz.notes.model.LockPolicy
import com.yaz.notes.model.NoteTimestamp
import com.yaz.notes.navigation.LocalReadableInset
import com.yaz.notes.navigation.WidthClass
import com.yaz.notes.security.AppLock
import com.yaz.notes.ui.component.ChoiceDialog
import com.yaz.notes.ui.component.FloatingAction
import com.yaz.notes.ui.component.FloatingFrame
import com.yaz.notes.ui.component.FloatingTop
import com.yaz.notes.ui.component.LargeTitle
import com.yaz.notes.ui.component.SettingsActionTile
import com.yaz.notes.ui.component.SettingsFoldedSections
import com.yaz.notes.ui.component.SettingsHeaderSpacer
import com.yaz.notes.ui.component.SettingsListDetail
import com.yaz.notes.ui.component.SettingsSection
import com.yaz.notes.ui.component.SettingsSectionTitle
import com.yaz.notes.ui.component.SettingsSwitchTile
import com.yaz.notes.ui.component.SettingsTile
import com.yaz.notes.ui.component.SettingsTileDialogs
import com.yaz.notes.ui.component.SettingsTileGrid
import kotlinx.coroutines.launch

private enum class OpenDialog { NONE, TIMESTAMP, LOCK_METHOD, TIMEOUT }

/**
 * The settings, as tiles: SMS's Settings as it is, on Modules' SettingsTiles. A large centred title folds
 * into the small one as the page scrolls; on a phone the sections are folded one under another and one opens
 * at a time; from a Medium window the sections form a list on the left and the chosen one's page is on the
 * right. A tap on a tile toggles or opens its choice, a long press explains it (Android Quick Settings'
 * QSTileViewImpl). The window's width is read with BoxWithConstraints and WidthClass.of, as SMS does.
 *
 * Ported from SMS's SettingsScreen (our own app, same layout and flow: sectionId / openId saved across a
 * turn of the screen, `tileId` for the long press, `when (dialog)` for the choices):
 * https://github.com/213YaZ786/SMS/blob/main/app/src/main/java/com/yaz/sms/feature/settings/SettingsScreen.kt
 * and Modules' SettingsTiles (ui/component/SettingsTiles.kt), whose model is AOSP SystemUI's QSTileViewImpl:
 * https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/qs/tileimpl/QSTileViewImpl.kt
 */
@Composable
fun SettingsScreen(settings: AppSettings, onBack: () -> Unit, onDiagnostics: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val canLock = remember { AppLock.available(context) }
    val canUseBiometrics = remember { AppLock.biometricsAvailable(context) }
    val repository = AppGraph.settings

    var dialog by rememberSaveable { mutableStateOf(OpenDialog.NONE) }
    // The tile whose long press is open (its id), "" when none.
    var tileId by rememberSaveable { mutableStateOf("") }
    var sectionId by rememberSaveable { mutableStateOf("appearance") }
    // On a phone every section starts folded; the one open stays open across a turn of the screen.
    var openId by rememberSaveable { mutableStateOf("") }
    // The page's scroll, held here so a drag on the header drives it: the folded list on a phone, the chosen
    // section's page on a wide window (it starts at the top again for each section).
    val compactScroll = rememberScrollState()
    val detailScroll = rememberSaveable(sectionId, saver = ScrollState.Saver) { ScrollState(0) }

    val appearance = SettingsSection("appearance", stringResource(R.string.settings_appearance), ImageVector.vectorResource(R.drawable.ic_palette))
    val security = SettingsSection("security", stringResource(R.string.settings_security), SettingsIcons.Lock)
    val backup = SettingsSection("backup", stringResource(R.string.settings_backup), ImageVector.vectorResource(R.drawable.ic_export))
    val troubleshooting = SettingsSection("troubleshooting", stringResource(R.string.settings_troubleshooting), ImageVector.vectorResource(R.drawable.ic_bug))
    val backIcon = ImageVector.vectorResource(R.drawable.ic_back)
    val sections = listOf(appearance, security, backup, troubleshooting)
    val section = sections.first { it.id == sectionId }
    val openSection = sections.firstOrNull { it.id == openId }

    // The backup actions and their password dialogs (BackupSection.kt).
    val backupTiles = rememberBackupTiles()

    val dateText = timestampLabel(settings.noteTimestamp)
    val methodLabel = lockMethodLabel(settings.lockMethod)
    val timeoutValue = timeoutLabel(settings.lockTimeoutSeconds)
    val hijriNote = stringResource(R.string.settings_timestamp_hijri_summary)
    val noBiometrics = stringResource(R.string.settings_lock_method_none)

    // Every tile of a section, the switches first, then what opens a choice, a page or the log.
    val tiles: Map<SettingsSection, List<SettingsTile>> = mapOf(
        appearance to listOf(
            SettingsSwitchTile(
                id = "glass", name = stringResource(R.string.settings_glass), title = stringResource(R.string.settings_glass),
                icon = SettingsIcons.AutoAwesome, summary = stringResource(R.string.settings_glass_summary),
                checked = settings.glass,
                onChange = { on -> scope.launch { repository.setGlass(on) } }
            ),
            SettingsActionTile(
                id = "timestamp", name = stringResource(R.string.settings_timestamp_name), title = stringResource(R.string.settings_timestamp),
                icon = SettingsIcons.Schedule, value = dateText,
                summary = if (settings.noteTimestamp == NoteTimestamp.HIJRI) dateText + "\n" + hijriNote else dateText,
                onClick = { dialog = OpenDialog.TIMESTAMP }
            )
        ),
        security to listOfNotNull(
            SettingsSwitchTile(
                id = "lock", name = stringResource(R.string.settings_lock_name), title = stringResource(R.string.settings_lock),
                icon = SettingsIcons.Lock,
                summary = stringResource(if (canLock) R.string.settings_lock_summary else R.string.settings_lock_unavailable),
                checked = settings.lockEnabled, enabled = canLock,
                onChange = { enabled -> scope.launch { repository.setLockEnabled(enabled) } }
            ),
            // What depends on the lock hides with it, as SMS's Relays hide with the encrypted chat off.
            if (settings.lockEnabled) SettingsActionTile(
                id = "method", name = stringResource(R.string.settings_lock_method), title = stringResource(R.string.settings_lock_method),
                icon = SettingsIcons.Fingerprint, value = methodLabel,
                summary = if (canUseBiometrics) methodLabel else methodLabel + "\n" + noBiometrics,
                onClick = { dialog = OpenDialog.LOCK_METHOD }
            ) else null,
            if (settings.lockEnabled) SettingsActionTile(
                id = "timeout", name = stringResource(R.string.settings_timeout_name), title = stringResource(R.string.settings_timeout),
                icon = SettingsIcons.Timer, value = timeoutValue, summary = timeoutValue,
                onClick = { dialog = OpenDialog.TIMEOUT }
            ) else null,
            if (settings.lockEnabled) SettingsActionTile(
                id = "lockNow", name = stringResource(R.string.settings_lock_now), title = stringResource(R.string.settings_lock_now),
                icon = SettingsIcons.Smartphone, value = stringResource(R.string.settings_lock_now_value),
                summary = stringResource(R.string.settings_lock_now_summary),
                onClick = { AppGraph.lock.lockNow() }
            ) else null
        ),
        backup to backupTiles,
        troubleshooting to listOf(
            // For reporting a problem, not for everyday use: the diagnostics screen.
            SettingsActionTile(
                id = "diagnostics", name = stringResource(R.string.diagnostics_title), title = stringResource(R.string.diagnostics_title),
                icon = ImageVector.vectorResource(R.drawable.ic_bug), value = stringResource(R.string.settings_diagnostics_value),
                summary = stringResource(R.string.settings_diagnostics_summary),
                onClick = onDiagnostics
            ),
            // The version, as SMS's "version" tile: no onClick, a tap shows the text too.
            SettingsActionTile(
                id = "version", name = stringResource(R.string.settings_version), title = stringResource(R.string.settings_version),
                icon = SettingsIcons.Info, value = BuildConfigInfo.VERSION, summary = "Notes " + BuildConfigInfo.VERSION
            )
        )
    )

    // One section's page: its name, then its tiles.
    val page: @Composable (SettingsSection) -> Unit = { which ->
        SettingsSectionTitle(which.title)
        val list = tiles[which].orEmpty()
        if (list.isNotEmpty()) SettingsTileGrid(list) { tileId = it.id }
    }

    // The window decides: a list and a page side by side from Medium up, as a phone held upright
    // keeps one page. The panes take the whole width, so the readable inset is dropped.
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = WidthClass.of(maxWidth) != WidthClass.COMPACT
        CompositionLocalProvider(LocalReadableInset provides if (wide) 0.dp else LocalReadableInset.current) {
            // The header as SMS's Settings has it (feature/settings/SettingsScreen.kt): Modules' FloatingFrame
            // with a back FloatingAction in FloatingTop and the large centred title that folds with the scroll.
            FloatingFrame(
                bottom = 0.dp,
                scroll = if (wide) detailScroll else compactScroll,
                top = {
                    FloatingTop(
                        stringResource(R.string.settings_title),
                        leading = { FloatingAction(backIcon, stringResource(R.string.action_back), onBack) }
                    )
                },
                largeTitle = LargeTitle(stringResource(R.string.settings_title))
            ) { padding ->
                if (wide) {
                    SettingsListDetail(
                        padding = padding,
                        sections = sections,
                        section = section,
                        onSection = { sectionId = it.id },
                        detailScroll = detailScroll,
                        detail = { page(section) }
                    )
                } else {
                    // Scrolls at full width, rows pushed in by the readable inset, so the
                    // margins of a tablet scroll like the rest.
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(compactScroll)
                            .padding(horizontal = LocalReadableInset.current)
                    ) {
                        SettingsHeaderSpacer(padding)
                        Spacer(Modifier.height(24.dp))
                        SettingsFoldedSections(sections, open = openSection, onOpen = { openId = it?.id.orEmpty() }) { which ->
                            val list = tiles[which].orEmpty()
                            if (list.isNotEmpty()) SettingsTileGrid(list) { tileId = it.id }
                        }
                        Spacer(Modifier.height(24.dp))
                        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
                    }
                }
            }
        }
    }

    tiles.values.flatten().firstOrNull { it.id == tileId }?.let { tile ->
        SettingsTileDialogs(tile, onDismiss = { tileId = "" })
    }

    when (dialog) {
        OpenDialog.TIMESTAMP -> ChoiceDialog(
            title = stringResource(R.string.settings_timestamp),
            options = NoteTimestamp.entries.map { it to timestampLabel(it) },
            selected = settings.noteTimestamp,
            onPick = { format -> scope.launch { repository.setNoteTimestamp(format) } },
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.LOCK_METHOD -> ChoiceDialog(
            title = stringResource(R.string.settings_lock_method),
            // The biometric choice is only offered when a fingerprint or a face is enrolled. The code always
            // works, since the lock itself is only offered when the phone has a screen lock.
            options = LockMethod.entries
                .filter { it == LockMethod.CODE || canUseBiometrics }
                .map { it to lockMethodLabel(it) },
            selected = settings.lockMethod,
            text = if (canUseBiometrics) null else noBiometrics,
            onPick = { method -> scope.launch { repository.setLockMethod(method) } },
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.TIMEOUT -> ChoiceDialog(
            title = stringResource(R.string.settings_timeout),
            options = LockPolicy.TIMEOUTS.map { it to timeoutLabel(it) },
            selected = settings.lockTimeoutSeconds,
            onPick = { seconds -> scope.launch { repository.setLockTimeout(seconds) } },
            onDismiss = { dialog = OpenDialog.NONE }
        )
        OpenDialog.NONE -> Unit
    }
}

@Composable
private fun lockMethodLabel(method: LockMethod): String = when (method) {
    LockMethod.BIOMETRIC -> stringResource(R.string.settings_lock_method_biometric)
    LockMethod.CODE -> stringResource(R.string.settings_lock_method_code)
}

@Composable
private fun timestampLabel(format: NoteTimestamp): String = when (format) {
    NoteTimestamp.OFF -> stringResource(R.string.settings_timestamp_off)
    NoteTimestamp.DATE_TIME -> stringResource(R.string.settings_timestamp_date_time)
    NoteTimestamp.NUMERIC -> stringResource(R.string.settings_timestamp_numeric)
    NoteTimestamp.RELATIVE -> stringResource(R.string.settings_timestamp_relative)
    NoteTimestamp.HIJRI -> stringResource(R.string.settings_timestamp_hijri)
}

@Composable
private fun timeoutLabel(seconds: Int): String = when {
    seconds == 0 -> stringResource(R.string.timeout_immediately)
    seconds < 60 -> pluralStringResource(R.plurals.timeout_seconds, seconds, seconds)
    else -> pluralStringResource(R.plurals.timeout_minutes, seconds / 60, seconds / 60)
}

/**
 * The tiles' icons that Notes has no drawable for: Google's Material Icons, filled style, 24 dp grid,
 * Apache License 2.0 (https://github.com/google/material-design-icons), the paths as our SMS app keeps them
 * (app/src/main/java/com/yaz/sms/ui/icon/AppIcons.kt, https://github.com/213YaZ786/SMS) and Contacts for the
 * fingerprint (app/src/main/java/com/yaz/contacts/ui/icon/AppIcons.kt, https://github.com/213YaZ786/Contacts).
 * Built from a path string with PathParser, as AppIcons.build does.
 */
private object SettingsIcons {
    val Lock: ImageVector by lazy {
        build("Lock", "M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z")
    }

    val Schedule: ImageVector by lazy {
        build(
            "Schedule",
            "M11.99 2C6.47 2 2 6.48 2 12s4.47 10 9.99 10C17.52 22 22 17.52 22 12S17.52 2 11.99 2zM12 20c-4.42 0-8-3.58-8-8" +
                "s3.58-8 8-8 8 3.58 8 8-3.58 8-8 8zM12.5 7H11v6l5.25 3.15.75-1.23-4.5-2.67z"
        )
    }

    val Timer: ImageVector by lazy {
        build(
            "Timer",
            "M19.03,7.39l1.42-1.42c-0.43-0.51-0.9-0.99-1.41-1.41l-1.42,1.42C16.07,4.74,14.12,4,12,4c-4." +
                "97,0-9,4.03-9,9 c0,4.97,4.02,9,9,9s9-4.03,9-9C21,10.88,20.26,8.93,19.03,7.39z M13,14h-2V8h" +
                "2V14zM9 1h6v2H9z"
        )
    }

    val AutoAwesome: ImageVector by lazy {
        build("AutoAwesome", "M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9zm-7.5.5L9 4 6.5 9.5 1 12l5.5 2.5L9 20l2.5-5.5L17 12l-5.5-2.5zM19 15l-1.25 2.75L15 19l2.75 1.25L19 23l1.25-2.75L23 19l-2.75-1.25L19 15z")
    }

    val Info: ImageVector by lazy {
        build(
            "Info",
            "M11,7h2v2h-2zM11,11h2v6h-2zM12,2C6.48,2 2,6.48 2,12s4.48,10 10,10 10,-4.48 10,-10" +
                "S17.52,2 12,2zM12,20c-4.41,0 -8,-3.59 -8,-8s3.59,-8 8,-8 8,3.59 8,8 -3.59,8 -8,8z"
        )
    }

    val Smartphone: ImageVector by lazy {
        build("Smartphone", "M17 1.01 7 1c-1.1 0-2 .9-2 2v18c0 1.1.9 2 2 2h10c1.1 0 2-.9 2-2V3c0-1.1-.9-1.99-2-1.99zM17 19H7V5h10v14z")
    }

    val Fingerprint: ImageVector by lazy {
        build(
            "Fingerprint",
            "M17.81 4.47c-.08 0-.16-.02-.23-.06C15.66 3.42 14 3 12.01 3c-1.98 0-3.86.47-5.57 1.41-.24.13-.54.04-.68-.2-.13-.24-.04-.55.2-.68C7.82 2.52 9.86 2 12.01 2c2.13 0 3.99.47 6.03 1.52.25.13.34.43.21.67-.09.18-.26.28-.44.28zM3.5 9.72c-.1 0-.2-.03-.29-.09-.23-.16-.28-.47-.12-.7.99-1.4 2.25-2.5 3.75-3.27C9.98 4.04 14 4.03 17.15 5.65c1.5.77 2.76 1.86 3.75 3.25.16.22.11.54-.12.7-.23.16-.54.11-.7-.12-.9-1.26-2.04-2.25-3.39-2.94-2.87-1.47-6.54-1.47-9.4.01-1.36.7-2.5 1.7-3.4 2.96-.08.14-.23.21-.39.21zm6.25 12.07c-.13 0-.26-.05-.35-.15-.87-.87-1.34-1.43-2.01-2.64-.69-1.23-1.05-2.73-1.05-4.34 0-2.97 2.54-5.39 5.66-5.39s5.66 2.42 5.66 5.39c0 .28-.22.5-.5.5s-.5-.22-.5-.5c0-2.42-2.09-4.39-4.66-4.39-2.57 0-4.66 1.97-4.66 4.39 0 1.44.32 2.77.93 3.85.64 1.15 1.08 1.64 1.85 2.42.19.2.19.51 0 .71-.11.1-.24.15-.37.15zm7.17-1.85c-1.19 0-2.24-.3-3.1-.89-1.49-1.01-2.38-2.65-2.38-4.39 0-.28.22-.5.5-.5s.5.22.5.5c0 1.41.72 2.74 1.94 3.56.71.48 1.54.71 2.54.71.24 0 .64-.03 1.04-.1.27-.05.53.13.58.41.05.27-.13.53-.41.58-.57.11-1.07.12-1.21.12zM14.91 22c-.04 0-.09-.01-.13-.02-1.59-.44-2.63-1.03-3.72-2.1-1.4-1.39-2.17-3.24-2.17-5.22 0-1.62 1.38-2.94 3.08-2.94 1.7 0 3.08 1.32 3.08 2.94 0 1.07.93 1.94 2.08 1.94s2.08-.87 2.08-1.94c0-3.77-3.25-6.83-7.25-6.83-2.84 0-5.44 1.58-6.61 4.03-.39.81-.59 1.76-.59 2.8 0 .78.07 2.01.67 3.61.1.26-.03.55-.29.64-.26.1-.55-.04-.64-.29-.49-1.31-.73-2.61-.73-3.96 0-1.2.23-2.29.68-3.24 1.33-2.79 4.28-4.6 7.51-4.6 4.55 0 8.25 3.51 8.25 7.83 0 1.62-1.38 2.94-3.08 2.94s-3.08-1.32-3.08-2.94c0-1.07-.93-1.94-2.08-1.94s-2.08.87-2.08 1.94c0 1.71.66 3.31 1.87 4.51.95.94 1.86 1.46 3.27 1.85.27.07.42.35.35.61-.05.23-.26.38-.47.38z"
        )
    }

    // SMS's AppIcons.build (https://github.com/213YaZ786/SMS, app/src/main/java/com/yaz/sms/ui/icon/AppIcons.kt).
    private fun build(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            addPath(
                pathData = PathParser().parsePathString(pathData).toNodes(),
                fill = SolidColor(Color.Black)
            )
        }.build()
}
