package com.amehrug.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amehrug.app.AppGraph
import com.amehrug.app.R
import com.amehrug.app.model.AppSettings
import com.amehrug.app.model.LockMethod
import com.amehrug.app.model.LockPolicy
import com.amehrug.app.model.NoteTimestamp
import com.amehrug.app.security.AppLock
import kotlinx.coroutines.launch

/**
 * The settings, in sections.
 *
 * Every section is a rounded container with its own heading, and the rows
 * inside it are what that heading covers. Nothing here is a long list of
 * radio buttons any more: a choice states what is chosen and opens a dialog,
 * so a screen that used to need two scrolls now fits on one.
 *
 * The date format sits under Appearance rather than in a section of its own.
 * It is the only thing in the app that changes what a note looks like, and a
 * heading with one row under it and nothing else would say less than this
 * does.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: AppSettings, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val canLock = remember { AppLock.available(context) }
    val canUseBiometrics = remember { AppLock.biometricsAvailable(context) }
    val repository = AppGraph.settings

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { insets ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(insets)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 12.dp),
            )

            SectionHeader(stringResource(R.string.settings_appearance))
            SettingsCard {
                ChoiceRow(
                    title = stringResource(R.string.settings_timestamp),
                    options = NoteTimestamp.entries,
                    selected = settings.noteTimestamp,
                    label = { format -> timestampLabel(format) },
                    note = if (settings.noteTimestamp == NoteTimestamp.HIJRI) {
                        stringResource(R.string.settings_timestamp_hijri_summary)
                    } else {
                        null
                    },
                    onPick = { format -> scope.launch { repository.setNoteTimestamp(format) } },
                )
            }

            SectionHeader(stringResource(R.string.settings_security))
            SettingsCard {
                SettingRow(
                    title = stringResource(R.string.settings_lock),
                    subtitle = if (canLock) {
                        stringResource(R.string.settings_lock_summary)
                    } else {
                        stringResource(R.string.settings_lock_unavailable)
                    },
                    enabled = canLock,
                ) {
                    Switch(
                        checked = settings.lockEnabled,
                        enabled = canLock,
                        onCheckedChange = { enabled ->
                            scope.launch { repository.setLockEnabled(enabled) }
                        },
                    )
                }
                ChoiceRow(
                    title = stringResource(R.string.settings_lock_method),
                    options = LockMethod.entries,
                    selected = settings.lockMethod,
                    label = { method -> lockMethodLabel(method) },
                    // The biometric choice is only offered when a
                    // fingerprint or a face is enrolled. The code always
                    // works, since the lock itself is only offered when the
                    // phone has a screen lock.
                    usable = { method -> method == LockMethod.CODE || canUseBiometrics },
                    note = if (!canUseBiometrics) {
                        stringResource(R.string.settings_lock_method_none)
                    } else {
                        null
                    },
                    enabled = settings.lockEnabled,
                    onPick = { method -> scope.launch { repository.setLockMethod(method) } },
                )
                ChoiceRow(
                    title = stringResource(R.string.settings_timeout),
                    options = LockPolicy.TIMEOUTS,
                    selected = settings.lockTimeoutSeconds,
                    label = { seconds -> timeoutLabel(seconds) },
                    enabled = settings.lockEnabled,
                    onPick = { seconds -> scope.launch { repository.setLockTimeout(seconds) } },
                )
                Button(
                    onClick = { AppGraph.lock.lockNow() },
                    enabled = settings.lockEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp)
                        .height(52.dp),
                ) {
                    Text(stringResource(R.string.settings_lock_now))
                }
            }

            SectionHeader(stringResource(R.string.settings_backup))
            SettingsCard {
                BackupSection { title, subtitle, enabled, onClick ->
                    SettingRow(
                        title = title,
                        subtitle = subtitle,
                        enabled = enabled,
                        onClick = onClick,
                    ) {}
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

/** The container a section's rows sit in. */
@Composable
private fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(modifier = Modifier.fillMaxWidth(), content = content)
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String?,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val base = Modifier.fillMaxWidth()
    val clickable = if (onClick != null && enabled) base.clickable(onClick = onClick) else base
    Row(
        modifier = clickable.padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}

/**
 * A setting that is one choice out of a handful.
 *
 * The row says what is chosen. The dialog is where the choosing happens,
 * which keeps the screen the length of its settings rather than the length
 * of all their options.
 */
@Composable
private fun <T> ChoiceRow(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
    note: String? = null,
    usable: (T) -> Boolean = { true },
    enabled: Boolean = true,
) {
    var open by remember { mutableStateOf(false) }
    val subtitle = if (note != null) {
        label(selected) + "\n" + note
    } else {
        label(selected)
    }
    SettingRow(
        title = title,
        subtitle = subtitle,
        enabled = enabled,
        onClick = { open = true },
    ) {}
    if (open) {
        ChoiceDialog(
            title = title,
            options = options,
            selected = selected,
            label = label,
            usable = usable,
            onPick = { value ->
                open = false
                onPick(value)
            },
            onDismiss = { open = false },
        )
    }
}

@Composable
private fun <T> ChoiceDialog(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    usable: (T) -> Boolean,
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                for (option in options) {
                    val allowed = usable(option)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = allowed) { onPick(option) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = option == selected,
                            enabled = allowed,
                            onClick = { onPick(option) },
                        )
                        Text(
                            text = label(option),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (allowed) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
            }
        },
    )
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
