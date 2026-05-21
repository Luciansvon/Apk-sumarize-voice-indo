package com.sumarize.voiceindo.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "summaries")
data class SummaryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val transcript: String,
    val summary: String,
    val durationSeconds: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val audioPath: String? = null
)
