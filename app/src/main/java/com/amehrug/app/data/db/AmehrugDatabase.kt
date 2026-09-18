package com.amehrug.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.amehrug.app.crypto.KeyVault
import net.zetetic.database.Logger
import net.zetetic.database.NoopTarget
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

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
abstract class AmehrugDatabase : RoomDatabase() {

    abstract fun notes(): NoteDao

    abstract fun labels(): LabelDao

    abstract fun settings(): SettingsDao

    companion object {
        const val NAME = "amehrug.db"

        /**
         * SQLCipher holds the whole file encrypted, pages and journal
         * included, with a passphrase only the Android Keystore can unwrap.
         *
         * No allowMainThreadQueries and no destructive migration, ever: a
         * failed migration must stop the app, not erase notes.
         */
        fun build(context: Context): AmehrugDatabase {
            System.loadLibrary("sqlcipher")
            // The client library logs to Logcat by default. Nothing of ours
            // belongs there.
            Logger.setTarget(NoopTarget())
            val factory = SupportOpenHelperFactory(KeyVault.databasePassphrase(context))
            return Room.databaseBuilder(
                context.applicationContext,
                AmehrugDatabase::class.java,
                NAME,
            )
                .openHelperFactory(factory)
                .build()
        }
    }
}
