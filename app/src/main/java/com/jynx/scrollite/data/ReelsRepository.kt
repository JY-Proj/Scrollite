package com.jynx.scrollite.data

import kotlinx.coroutines.flow.Flow

interface ReelsRepository {
    fun observeAll(): Flow<List<ReelEntry>>
    suspend fun insert(entry: ReelEntry)
}

class RoomReelsRepository(private val dao: ReelDao) : ReelsRepository {
    override fun observeAll(): Flow<List<ReelEntry>> = dao.observeAll()
    override suspend fun insert(entry: ReelEntry) = dao.insert(entry)
}
