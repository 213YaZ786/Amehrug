package com.amehrug.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

// Name and value, so a new setting never needs a migration.
// Column names avoid "key", which reads badly in SQL.
@Entity(tableName = "settings")
data class SettingEntity(
    @PrimaryKey val name: String,
    val value: String,
)
