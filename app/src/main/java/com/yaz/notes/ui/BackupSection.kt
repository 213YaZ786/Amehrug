package com.yaz.notes.ui

import com.yaz.notes.ui.component.ZoneAlertDialog
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import com.yaz.notes.ui.component.SettingsActionTile
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.yaz.notes.AppGraph
import com.yaz.notes.R
import com.yaz.notes.crypto.BackupCrypto
import com.yaz.notes.diagnostics.Diagnostics
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private sealed interface Ask {
    data class Export(val target: Uri) : Ask

    data class Restore(val source: Uri) : Ask
}

/**
 * Backup and restore, both manual. The password never leaves this screen and
 * is not stored anywhere: a backup nobody can open is the point.
 *
 * Scheduled backups are the next task.
 *
 * Returns the actions as tiles for Settings and emits the password dialogs, so it is called once, at the
 * top of the screen, where its state outlives a section being folded. Taken from SMS's Settings, which keeps
 * its dialogs outside the tiles' grid (`when (dialog)`) and lists its tiles as SettingsActionTile:
 * /home/yghouar/Claude Code/APK/YAZ/SMS/app/src/main/java/com/yaz/sms/feature/settings/SettingsScreen.kt
 * (repo 213YaZ786/SMS, app/src/main/java/com/yaz/sms/feature/settings/SettingsScreen.kt,
 * https://github.com/213YaZ786/SMS/blob/main/app/src/main/java/com/yaz/sms/feature/settings/SettingsScreen.kt).
 */
@Composable
fun rememberBackupTiles(): List<SettingsActionTile> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf<Ask?>(null) }

    // A local function is only visible after its declaration, so this one
    // stays above every caller. Moving it down broke the build.
    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> if (uri != null) ask = Ask.Export(uri) }

    val restorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) ask = Ask.Restore(uri) }

    // Notally's own backup carries no password, so this one needs no dialog.
    val notallyPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            working = true
            scope.launch {
                try {
                    val report = AppGraph.notallyImport.importFrom(uri)
                    toast(
                        context.getString(
                            R.string.notally_done,
                            report.notes,
                            report.files,
                        ),
                    )
                } catch (cancel: CancellationException) {
                    // Leaving the screen cancels the coroutine, and
                    // CancellationException is an Exception. Caught below it would
                    // be reported as a failure and would break the cancellation.
                    throw cancel
                } catch (e: Exception) {
                    Diagnostics.log.error("import", "reading a Notally backup", e)
                    toast(context.getString(R.string.notally_failed))
                } finally {
                    working = false
                }
            }
        }
    }

    // The three actions as tiles of Settings (Modules' SettingsTiles, SMS's Settings): a tap starts the
    // action, a long press shows the full text. While one runs, the tiles say so and a tap does nothing;
    // the long press then reads the slow password note instead of the usual summary.
    val busy = stringResource(R.string.backup_working_short)
    val busySummary = stringResource(R.string.backup_working)
    val tiles = listOf(
        SettingsActionTile(
            id = "export",
            name = stringResource(R.string.backup_export_name),
            title = stringResource(R.string.backup_export),
            icon = ImageVector.vectorResource(R.drawable.ic_export),
            value = if (working) busy else stringResource(R.string.backup_export_value),
            summary = if (working) busySummary else stringResource(R.string.backup_export_summary),
            onClick = {
                if (!working) {
                    val stamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                    exportPicker.launch("notes-$stamp.amb")
                }
            },
        ),
        SettingsActionTile(
            id = "restore",
            name = stringResource(R.string.backup_restore_name),
            title = stringResource(R.string.backup_restore),
            icon = ImageVector.vectorResource(R.drawable.ic_restore),
            value = if (working) busy else stringResource(R.string.backup_restore_value),
            summary = if (working) busySummary else stringResource(R.string.backup_restore_summary),
            onClick = { if (!working) restorePicker.launch(arrayOf("*/*")) },
        ),
        SettingsActionTile(
            id = "notally",
            name = stringResource(R.string.notally_import_name),
            title = stringResource(R.string.notally_import),
            icon = ImageVector.vectorResource(R.drawable.ic_note),
            value = if (working) busy else stringResource(R.string.notally_import_value),
            summary = if (working) busySummary else stringResource(R.string.notally_import_summary),
            onClick = { if (!working) notallyPicker.launch(arrayOf("*/*")) },
        ),
    )

    when (val current = ask) {
        null -> Unit
        is Ask.Export -> PasswordDialog(
            title = stringResource(R.string.backup_export),
            description = stringResource(R.string.backup_password_new),
            confirmField = true,
            onDismiss = { ask = null },
        ) { password ->
            ask = null
            working = true
            scope.launch {
                try {
                    val report = AppGraph.backup.writeTo(
                        current.target,
                        password.toCharArray(),
                        System.currentTimeMillis(),
                    )
                    toast(context.getString(R.string.backup_done, report.notes, report.files))
                } catch (cancel: CancellationException) {
                    // Leaving the screen cancels the coroutine, and
                    // CancellationException is an Exception. Caught below it would
                    // be reported as a failure and would break the cancellation.
                    throw cancel
                } catch (e: Exception) {
                    Diagnostics.log.error("backup", "writing the backup", e)
                    toast(context.getString(R.string.backup_failed))
                } finally {
                    working = false
                }
            }
        }
        is Ask.Restore -> PasswordDialog(
            title = stringResource(R.string.backup_restore),
            description = stringResource(R.string.backup_password_open),
            confirmField = false,
            onDismiss = { ask = null },
        ) { password ->
            ask = null
            working = true
            scope.launch {
                try {
                    val report = AppGraph.backup.restoreFrom(current.source, password.toCharArray())
                    toast(context.getString(R.string.restore_done, report.notes, report.files))
                } catch (cancel: CancellationException) {
                    // Leaving the screen cancels the coroutine, and
                    // CancellationException is an Exception. Caught below it would
                    // be reported as a failure and would break the cancellation.
                    throw cancel
                } catch (e: Exception) {
                    // A wrong password and a damaged file look the same, on purpose.
                    Diagnostics.log.error("backup", "reading the backup", e)
                    toast(context.getString(R.string.restore_failed))
                } finally {
                    working = false
                }
            }
        }
    }
    return tiles
}

@Composable
private fun PasswordDialog(
    title: String,
    description: String,
    confirmField: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val longEnough = password.length >= BackupCrypto.MIN_PASSWORD
    val matching = !confirmField || password == again
    val ready = longEnough && matching

    ZoneAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(description, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.backup_password)) },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.padding(top = 16.dp),
                )
                if (confirmField) {
                    OutlinedTextField(
                        value = again,
                        onValueChange = { again = it },
                        label = { Text(stringResource(R.string.backup_password_again)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (!longEnough && password.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.backup_password_short, BackupCrypto.MIN_PASSWORD),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (!matching && again.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.backup_password_mismatch),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = ready) {
                Text(stringResource(R.string.action_continue))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
