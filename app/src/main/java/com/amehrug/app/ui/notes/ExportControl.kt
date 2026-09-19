package com.amehrug.app.ui.notes

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.amehrug.app.AppGraph
import com.amehrug.app.R
import com.amehrug.app.diagnostics.Diagnostics
import com.amehrug.app.export.PdfExport
import com.amehrug.app.model.ExportFormat
import com.amehrug.app.model.Note
import com.amehrug.app.model.NoteExport
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Export, from the open note or from a selection on the wall.
 *
 * The file is created through the system picker, so nothing leaves the app
 * unless the person points at a place to put it, and no permission is
 * needed. Four launchers rather than one, because the type of file a picker
 * creates is fixed when the launcher is built.
 *
 * The notes are read at the moment a format is chosen, not when the write
 * lands, so a note edited while the picker is open exports as it was.
 */
@Composable
fun ExportControl(visible: Boolean, notes: () -> List<Note>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var chosen by remember { mutableStateOf<Pair<ExportFormat, List<Note>>?>(null) }

    // Declared above its callers on purpose: a local function is only
    // visible after its declaration.
    fun write(target: Uri) {
        val work = chosen ?: return
        chosen = null
        val app = context.applicationContext
        // appScope, so leaving the screen does not cancel a write already
        // under way.
        AppGraph.appScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val stream = app.contentResolver.openOutputStream(target)
                        ?: error("no stream for $target")
                    stream.use { out ->
                        when (work.first) {
                            ExportFormat.PDF -> PdfExport.write(work.second, out)
                            ExportFormat.TXT ->
                                out.write(NoteExport.text(work.second).toByteArray())
                            ExportFormat.MARKDOWN ->
                                out.write(NoteExport.markdown(work.second).toByteArray())
                            ExportFormat.HTML ->
                                out.write(NoteExport.html(work.second).toByteArray())
                        }
                    }
                }
                Toast.makeText(app, R.string.export_done, Toast.LENGTH_SHORT).show()
            } catch (cancel: CancellationException) {
                // CancellationException is an Exception. Caught below it
                // would be reported as a failure and would break the
                // cancellation.
                throw cancel
            } catch (e: Exception) {
                Diagnostics.log.error("export", "writing an export", e)
                Toast.makeText(app, R.string.export_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    val txt = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.TXT.mimeType),
    ) { uri -> if (uri != null) write(uri) else chosen = null }

    val markdown = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.MARKDOWN.mimeType),
    ) { uri -> if (uri != null) write(uri) else chosen = null }

    val html = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.HTML.mimeType),
    ) { uri -> if (uri != null) write(uri) else chosen = null }

    val pdf = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument(ExportFormat.PDF.mimeType),
    ) { uri -> if (uri != null) write(uri) else chosen = null }

    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.export_title)) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                for (format in ExportFormat.entries) {
                    Text(
                        text = stringResource(formatLabel(format)),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val list = notes()
                                chosen = format to list
                                onDismiss()
                                val stamp = LocalDate.now()
                                    .format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                                val name = NoteExport.fileName(list, format, stamp)
                                when (format) {
                                    ExportFormat.TXT -> txt.launch(name)
                                    ExportFormat.MARKDOWN -> markdown.launch(name)
                                    ExportFormat.HTML -> html.launch(name)
                                    ExportFormat.PDF -> pdf.launch(name)
                                }
                            }
                            .padding(vertical = 14.dp),
                    )
                }
            }
        },
    )
}

private fun formatLabel(format: ExportFormat): Int = when (format) {
    ExportFormat.TXT -> R.string.export_txt
    ExportFormat.MARKDOWN -> R.string.export_markdown
    ExportFormat.HTML -> R.string.export_html
    ExportFormat.PDF -> R.string.export_pdf
}
