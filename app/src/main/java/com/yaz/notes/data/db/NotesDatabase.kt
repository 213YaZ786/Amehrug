package com.yaz.notes.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.yaz.notes.crypto.KeyVault
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

// Renamed from AmehrugDatabase, same code. Source: Room's database class,
// https://developer.android.com/training/data-storage/room (schema folder named by this full name).
@Database(
    entities = [
        NoteEntity::class,
        NoteFtsEntity::class,
        SpanEntity::class,
        ListItemEntity::class,
        LabelEntity::class,
        NoteLabelEntity::class,
        AttachmentEntity::class,
        ReminderEntity::class,
        SettingEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class NotesDatabase : RoomDatabase() {

    abstract fun notes(): NoteDao

    abstract fun labels(): LabelDao

    abstract fun settings(): SettingsDao

    companion object {
        const val NAME = "notes.db"

        /**
         * SQLCipher holds the whole file encrypted, pages and journal
         * included, with a passphrase only the Android Keystore can unwrap.
         *
         * No allowMainThreadQueries and no destructive migration, ever: a
         * failed migration must stop the app, not erase notes.
         */
        fun build(context: Context): NotesDatabase {
            System.loadLibrary("sqlcipher")
            // The client library logs to Logcat by default. Nothing of ours
            // belongs there.
            Logger.setTarget(NoopTarget())
            val factory = SupportOpenHelperFactory(KeyVault.databasePassphrase(context))
            return Room.databaseBuilder(
                context.applicationContext,
                NotesDatabase::class.java,
                NAME,
            )
                .openHelperFactory(factory)
                // One connection, so the key is derived once. Opening is slow
                // by design in SQLCipher (key derivation on each open, Zetetic:
                // https://discuss.zetetic.net/t/sqlcipher-performance-optimization/14),
                // and in WAL mode the pool opens up to four more connections
                // (AOSP SQLiteConnectionPool, size 1 without WAL; Room's
                // RoomDatabase.Builder.setJournalMode, JournalMode.TRUNCATE).
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build()
        }
    }
}
