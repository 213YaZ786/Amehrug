package com.amehrug.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
abstract class LabelDao {

    @Query("SELECT name FROM labels ORDER BY name COLLATE NOCASE")
    abstract fun observeAll(): Flow<List<String>>

    @Query("SELECT name FROM labels ORDER BY name COLLATE NOCASE")
    abstract suspend fun all(): List<String>

    @Query("SELECT COUNT(*) FROM labels WHERE name = :name")
    abstract suspend fun exists(name: String): Int

    /** Returns -1 when the label already existed. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(row: LabelEntity): Long

    /** The note links follow through ON UPDATE CASCADE. */
    @Query("UPDATE labels SET name = :newName WHERE name = :oldName")
    abstract suspend fun rename(oldName: String, newName: String): Int

    /** The note links go with it through ON DELETE CASCADE. The notes stay. */
    @Query("DELETE FROM labels WHERE name = :name")
    abstract suspend fun delete(name: String): Int
}
