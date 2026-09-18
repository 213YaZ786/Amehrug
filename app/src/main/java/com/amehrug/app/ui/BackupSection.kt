package com.amehrug.app.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.amehrug.app.AppGraph
import com.amehrug.app.R
import com.amehrug.app.crypto.BackupCrypto
import com.amehrug.app.diagnostics.Diagnostics
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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
 */
@Composable
fun BackupSection(rowContent: @Composable (String, String?, Boolean, () -> Unit) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf(false) }
    var ask by remember { mutableStateOf<Ask?>(null) }

    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri -> if (uri != null) ask = Ask.Export(uri) }

    val restorePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) ask = Ask.Restore(uri) }

    fun toast(message: String) = Toast.makeText(context, message, Toast.LENGTH_LONG).show()

    rowContent(
        stringResource(R.string.backup_export),
        stringResource(R.string.backup_export_summary),
        !working,
    ) {
        val stamp = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        exportPicker.launch("amehrug-$stamp.amb")
    }
    rowContent(
        stringResource(R.string.backup_restore),
        stringResource(R.string.backup_restore_summary),
        !working,
    ) {
        restorePicker.launch(arrayOf("*/*"))
    }
    if (working) {
        Column(modifier = Modifier.padding(16.dp)) {
            CircularProgressIndicator()
            Text(
                text = stringResource(R.string.backup_working),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

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

    AlertDialog(
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
