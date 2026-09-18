package com.amehrug.app

import android.app.Application
import com.amehrug.app.backup.BackupManager
import com.amehrug.app.crypto.AttachmentStore
import com.amehrug.app.crypto.KeyVault
import com.amehrug.app.data.NoteRepository
import com.amehrug.app.data.SettingsRepository
import com.amehrug.app.data.db.AmehrugDatabase
import com.amehrug.app.diagnostics.Diagnostics
import com.amehrug.app.security.AppLock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Hand made dependency graph. Small enough that a DI library would add more
 * than it saves. Everything is created on first use.
 */
object AppGraph {

    private lateinit var app: Application

    /** Lives as long as the process. Work that must outlive a screen goes here. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val database: AmehrugDatabase by lazy { AmehrugDatabase.build(app) }

    val notes: NoteRepository by lazy { NoteRepository(database) }

    val attachments: AttachmentStore by lazy { AttachmentStore(app) }

    val settings: SettingsRepository by lazy { SettingsRepository(database) }

    val backup: BackupManager by lazy { BackupManager(app, notes, attachments) }

    /** Locked until the settings say otherwise. */
    val lock: AppLock = AppLock()

    fun init(application: Application) {
        app = application
    }

    /**
     * Opens the database off the main thread, reports what it found, empties
     * the trash after 30 days, drops media files no note points at, and wipes
     * the clear text cache left by a previous session. Counts only, never
     * content.
     */
    fun startupCheck() {
        appScope.launch {
            val log = Diagnostics.log
            val databaseFile = app.getDatabasePath(AmehrugDatabase.NAME)
            if (databaseFile.exists() && !KeyVault.exists(app)) {
                // Either a database from before encryption, or a restored
                // folder whose keys stayed on the other device.
                log.error("database", "the database file has no key on this device")
                return@launch
            }
            try {
                val started = System.nanoTime()
                val count = notes.count()
                val elapsedMs = (System.nanoTime() - started) / 1_000_000
                log.info("database", "opened encrypted in $elapsedMs ms, $count notes, schema 1")

                val purge = notes.purgeTrash()
                if (purge.notes > 0) {
                    log.info("database", "trash purged: ${purge.notes} notes")
                }
                attachments.clearCache()
                val orphans = attachments.deleteOrphans(notes.allAttachmentFiles())
                if (orphans > 0) log.info("media", "$orphans orphan files removed")
            } catch (e: Exception) {
                log.error("database", "startup check", e)
            }
        }
    }
}
