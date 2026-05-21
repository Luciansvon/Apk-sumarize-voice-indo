package com.sumarize.voiceindo.data.repository

import com.sumarize.voiceindo.data.db.SummaryDao
import com.sumarize.voiceindo.data.db.SummaryEntity
import kotlinx.coroutines.flow.Flow

class SummaryRepository(private val dao: SummaryDao) {
    fun getAll(): Flow<List<SummaryEntity>> = dao.getAll()
    suspend fun getById(id: Long): SummaryEntity? = dao.getById(id)
    suspend fun save(entity: SummaryEntity): Long = dao.insert(entity)
    suspend fun deleteById(id: Long) = dao.deleteById(id)
}
