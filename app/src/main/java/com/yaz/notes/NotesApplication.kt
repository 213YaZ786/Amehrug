package com.yaz.notes

import android.app.Application
import android.os.Process
import android.os.SystemClock
import com.yaz.notes.diagnostics.DiagnosticLog
import com.yaz.notes.diagnostics.Diagnostics
import java.io.File

// Renamed from AmehrugApplication, same code. Source: this suite's SMS (com.yaz.sms) naming and
// Android's Application class, https://developer.android.com/reference/android/app/Application
class NotesApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // The isolated DecoderService starts here as well: it gets nothing of the
        // app (no database, no keys, no log), as in every app of the Modules base.
        if (Process.isIsolated()) return
        BuildConfigInfo.init(this)
        val log = Diagnostics.log
        val sinceStart = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()
        log.info("start", "process to application $sinceStart ms")
        restorePreviousCrash()
        installCrashRecorder()
        AppGraph.init(this)
        AppGraph.startupCheck()
    }

    // noBackupFilesDir is private to the app and never backed up.
    private fun crashFile() = File(noBackupFilesDir, CRASH_FILE)

    private fun restorePreviousCrash() {
        Diagnostics.io.execute {
            val file = crashFile()
            try {
                if (file.isFile) {
                    Diagnostics.log.restored("previous crash", file.readText())
                    file.delete()
                }
            } catch (e: Exception) {
                Diagnostics.log.error("start", "reading previous crash", e)
            }
        }
    }

    private fun installCrashRecorder() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            // Written on the crashing thread: the process is about to die.
            try {
                crashFile().writeText(
                    "thread ${thread.name}, version ${BuildConfigInfo.VERSION}\n" +
                        DiagnosticLog.describe(throwable),
                )
            } catch (_: Exception) {
                // Nothing else can be done at this point.
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private companion object {
        const val CRASH_FILE = "last-crash.txt"
    }
}
