package com.amehrug.app

import android.app.Application
import android.os.Process
import android.os.SystemClock
import com.amehrug.app.diagnostics.DiagnosticLog
import com.amehrug.app.diagnostics.Diagnostics
import java.io.File

class AmehrugApplication : Application() {

    override fun onCreate() {
        super.onCreate()
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
