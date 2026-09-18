package com.amehrug.app.data.db

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
abstract class SettingsDao {

    @Query("SELECT * FROM settings")
    abstract fun observeAll(): Flow<List<SettingEntity>>

    @Query("SELECT * FROM settings")
    abstract suspend fun all(): List<SettingEntity>

    @Upsert
    abstract suspend fun put(row: SettingEntity)
}
