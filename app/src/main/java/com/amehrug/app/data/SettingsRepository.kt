package com.amehrug.app.data

import com.amehrug.app.data.db.AmehrugDatabase
import com.amehrug.app.data.db.SettingEntity
import com.amehrug.app.model.AppSettings
import com.amehrug.app.model.SettingsCodec
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SettingsRepository(database: AmehrugDatabase) {

    private val dao = database.settings()

    val settings: Flow<AppSettings> = dao.observeAll().map { rows -> decode(rows) }

    suspend fun current(): AppSettings = decode(dao.all())

    suspend fun setLockEnabled(enabled: Boolean) = put(SettingsCodec.encodeLockEnabled(enabled))

    suspend fun setLockTimeout(seconds: Int) = put(SettingsCodec.encodeLockTimeout(seconds))

    private suspend fun put(pair: Pair<String, String>) =
        dao.put(SettingEntity(name = pair.first, value = pair.second))

    private fun decode(rows: List<SettingEntity>): AppSettings =
        SettingsCodec.decode(rows.associate { it.name to it.value })
}
