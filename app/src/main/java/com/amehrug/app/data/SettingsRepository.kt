package com.amehrug.app.data

import com.amehrug.app.data.db.AmehrugDatabase
import com.amehrug.app.data.db.SettingEntity
import com.amehrug.app.model.AppSettings
import com.amehrug.app.model.DockItem
import com.amehrug.app.model.LockMethod
import com.amehrug.app.model.NoteTimestamp
import com.amehrug.app.model.SettingsCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(database: AmehrugDatabase) {

    private val dao = database.settings()

    val settings: Flow<AppSettings> = dao.observeAll().map { rows -> decode(rows) }

    suspend fun current(): AppSettings = decode(dao.all())

    suspend fun setLockEnabled(enabled: Boolean) = put(SettingsCodec.encodeLockEnabled(enabled))

    suspend fun setLockMethod(method: LockMethod) = put(SettingsCodec.encodeLockMethod(method))

    suspend fun setLockTimeout(seconds: Int) = put(SettingsCodec.encodeLockTimeout(seconds))

    suspend fun setNoteTimestamp(format: NoteTimestamp) =
        put(SettingsCodec.encodeNoteTimestamp(format))

    suspend fun setDockOrder(order: List<DockItem>) = put(SettingsCodec.encodeDockOrder(order))

    /**
     * The departure time survives the process being killed, which is the
     * whole point: swiping the app out of the recents list kills it, and
     * without this the timeout was never honoured once.
     */
    suspend fun lockLeftAt(): Long? = SettingsCodec.decodeLockLeftAt(
        dao.all().firstOrNull { it.name == SettingsCodec.LOCK_LEFT_AT }?.value,
    )

    suspend fun setLockLeftAt(value: Long?) = put(SettingsCodec.encodeLockLeftAt(value))

    private suspend fun put(pair: Pair<String, String>) =
        dao.put(SettingEntity(name = pair.first, value = pair.second))

    private fun decode(rows: List<SettingEntity>): AppSettings =
        SettingsCodec.decode(rows.associate { it.name to it.value })
}
