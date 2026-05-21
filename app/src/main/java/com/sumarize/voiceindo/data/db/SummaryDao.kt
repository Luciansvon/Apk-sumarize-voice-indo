package com.sumarize.voiceindo.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SummaryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(summary: SummaryEntity): Long

    @Query("SELECT * FROM summaries ORDER BY timestamp DESC")
    fun getAll(): Flow<List<SummaryEntity>>

    @Query("SELECT * FROM summaries WHERE id = :id")
    suspend fun getById(id: Long): SummaryEntity?

    @Delete
    suspend fun delete(summary: SummaryEntity)

    @Query("DELETE FROM summaries WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM summaries")
    suspend fun count(): Int
}
