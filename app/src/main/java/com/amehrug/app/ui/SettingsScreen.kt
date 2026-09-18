package com.amehrug.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amehrug.app.AppGraph
import com.amehrug.app.R
import com.amehrug.app.model.AppSettings
import com.amehrug.app.model.LockPolicy
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
            HorizontalDivider()
            Text(
                text = stringResource(R.string.settings_timeout),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp),
            )
            for (seconds in LockPolicy.TIMEOUTS) {
                SettingRow(
                    title = timeoutLabel(seconds),
                    subtitle = null,
                    enabled = settings.lockEnabled,
                    onClick = { scope.launch { repository.setLockTimeout(seconds) } },
                ) {
                    RadioButton(
                        selected = settings.lockTimeoutSeconds == seconds,
                        enabled = settings.lockEnabled,
                        onClick = { scope.launch { repository.setLockTimeout(seconds) } },
                    )
                }
            }
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
private fun timeoutLabel(seconds: Int): String = when {
    seconds == 0 -> stringResource(R.string.timeout_immediately)
    seconds < 60 -> pluralStringResource(R.plurals.timeout_seconds, seconds, seconds)
    else -> pluralStringResource(R.plurals.timeout_minutes, seconds / 60, seconds / 60)
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
