package com.amehrug.app.diagnostics

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.amehrug.app.BuildConfigInfo
import com.amehrug.app.R
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var entries by remember { mutableStateOf(Diagnostics.log.snapshot()) }
    val refresh = { entries = Diagnostics.log.snapshot() }

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> if (uri != null) saveTo(context, uri) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_back),
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    TextButton(onClick = refresh) { Text(stringResource(R.string.action_refresh)) }
                },
            )
        },
        bottomBar = {
            BottomAppBar {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    TextButton(onClick = { copyAll(context) }) {
                        Text(stringResource(R.string.action_copy_all))
                    }
                    TextButton(onClick = { saveLauncher.launch(FILE_NAME) }) {
                        Text(stringResource(R.string.action_save_txt))
                    }
                    TextButton(onClick = {
                        Diagnostics.log.clear()
                        refresh()
                    }) {
                        Text(stringResource(R.string.action_clear))
                    }
                }
            }
        },
    ) { insets ->
        if (entries.isEmpty()) {
            Text(
                text = stringResource(R.string.diagnostics_empty),
                modifier = Modifier.padding(insets).padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val zone = remember { ZoneId.systemDefault() }
            LazyColumn(modifier = Modifier.fillMaxSize().padding(insets)) {
                items(entries.asReversed()) { entry ->
                    Text(
                        text = DiagnosticLog.formatEntry(entry, zone),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (entry.level == Level.INFO) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

private const val FILE_NAME = "amehrug-diagnostics.txt"

private fun exportText(): String = Diagnostics.log.export(BuildConfigInfo.header())

private fun copyAll(context: Context) {
    val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("Amehrug diagnostics", exportText()))
    // Android 13 and later show their own confirmation.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
        Toast.makeText(context, R.string.diagnostics_copied, Toast.LENGTH_SHORT).show()
    }
}

private fun saveTo(context: Context, uri: Uri) {
    val appContext = context.applicationContext
    val text = exportText()
    Diagnostics.io.execute {
        val ok = try {
            appContext.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(text.toByteArray(Charsets.UTF_8))
                true
            } ?: false
        } catch (e: Exception) {
            Diagnostics.log.error("diagnostics", "saving log", e)
            false
        }
        appContext.mainExecutor.execute {
            val message = if (ok) R.string.diagnostics_saved else R.string.diagnostics_save_failed
            Toast.makeText(appContext, message, Toast.LENGTH_SHORT).show()
        }
    }
}
