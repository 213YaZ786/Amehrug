package com.amehrug.app.diagnostics

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** The one log of the process, and the thread that writes its files. */
object Diagnostics {
    val log = DiagnosticLog()
    val io: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "amehrug-diagnostics").apply { isDaemon = true }
    }
}
