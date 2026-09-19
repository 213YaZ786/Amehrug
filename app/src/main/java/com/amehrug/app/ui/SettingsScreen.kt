package com.amehrug.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
 * Provisional screen, holding only what task 7 needs. The real settings
 * screen is roadmap task 19.
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
                title = { Text(stringResource(R.string.settings_title)) },
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
                .verticalScroll(rememberScrollState()),
        ) {
            SettingRow(
                title = stringResource(R.string.settings_lock),
                subtitle = if (canLock) {
                    stringResource(R.string.settings_lock_summary)
                } else {
                    stringResource(R.string.settings_lock_unavailable)
                },
            ) {
                Switch(
                    checked = settings.lockEnabled,
                    enabled = canLock,
                    onCheckedChange = { enabled ->
                        scope.launch { repository.setLockEnabled(enabled) }
                    },
                )
            }
            Text(
                text = stringResource(R.string.settings_lock_method),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            for (method in LockMethod.entries) {
                // The biometric row is offered only when a fingerprint or a
                // face is enrolled. The code row always works, since the lock
                // itself is only offered when the phone has a screen lock.
                val usable = settings.lockEnabled &&
                    (method == LockMethod.CODE || canUseBiometrics)
                SettingRow(
                    title = lockMethodLabel(method),
                    subtitle = if (method == LockMethod.BIOMETRIC && !canUseBiometrics) {
                        stringResource(R.string.settings_lock_method_none)
                    } else {
                        null
                    },
                    enabled = usable,
                    onClick = { scope.launch { repository.setLockMethod(method) } },
                ) {
                    RadioButton(
                        selected = settings.lockMethod == method,
                        enabled = usable,
                        onClick = { scope.launch { repository.setLockMethod(method) } },
                    )
                }
            }
            HorizontalDivider()
            // One row per choice ate the whole screen. The row now states
            // what is chosen and opens a strip of options only when asked.
            ChoiceSetting(
                title = stringResource(R.string.settings_timeout),
                options = LockPolicy.TIMEOUTS,
                selected = settings.lockTimeoutSeconds,
                label = { seconds -> timeoutLabel(seconds) },
                enabled = settings.lockEnabled,
                onPick = { seconds -> scope.launch { repository.setLockTimeout(seconds) } },
            )
            HorizontalDivider()
            ChoiceSetting(
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
            HorizontalDivider()
            Text(
                text = stringResource(R.string.settings_backup),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            BackupSection { title, subtitle, enabled, onClick ->
                SettingRow(title = title, subtitle = subtitle, enabled = enabled, onClick = onClick) {}
            }
            HorizontalDivider()
            TextButton(
                onClick = { AppGraph.lock.lockNow() },
                enabled = settings.lockEnabled,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.settings_lock_now))
            }
        }
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
 * A setting that is one choice out of a handful.
 *
 * Closed, it is a single row carrying the title and the current answer. Open,
 * it lays the options out as chips that wrap onto as many lines as they need,
 * so five choices cost one row of the screen instead of five. The note below
 * belongs to whatever is chosen and is left out when there is nothing to say.
 *
 * FlowRow without the overflow parameter is the stable overload. The one
 * taking overflow is still experimental and is not used here.
 */
@Composable
private fun <T> ChoiceSetting(
    title: String,
    options: List<T>,
    selected: T,
    label: @Composable (T) -> String,
    onPick: (T) -> Unit,
    note: String? = null,
    enabled: Boolean = true,
) {
    var open by rememberSaveable(title) { mutableStateOf(false) }
    val turn by animateFloatAsState(if (open) 180f else 0f, label = "chevron")
    Column(modifier = Modifier.fillMaxWidth()) {
        SettingRow(
            title = title,
            subtitle = label(selected),
            enabled = enabled,
            onClick = { open = !open },
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_expand),
                contentDescription = null,
                modifier = Modifier.rotate(turn),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = open) {
            Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    for (option in options) {
                        FilterChip(
                            selected = option == selected,
                            enabled = enabled,
                            onClick = { onPick(option) },
                            label = { Text(label(option)) },
                        )
                    }
                }
                if (note != null) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }
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
    androidx.compose.foundation.layout.Row(
        modifier = clickable.padding(16.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
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
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        trailing()
    }
}
