package com.jynx.scrollite.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageSessionDao {

    @Insert
    suspend fun insert(session: UsageSession): Long

    /**
     * Total time in [sections] overlapping [fromMs, toMs), with sessions that
     * cross either edge (e.g. midnight) clipped so only the part inside counts.
     */
    @Query(
        """
        SELECT COALESCE(SUM(MIN(endMs, :toMs) - MAX(startMs, :fromMs)), 0)
        FROM usage_sessions
        WHERE endMs > :fromMs AND startMs < :toMs AND section IN (:sections)
        """
    )
    suspend fun totalBetween(fromMs: Long, toMs: Long, sections: List<String>): Long

    @Query(
        """
        SELECT COALESCE(SUM(MIN(endMs, :toMs) - MAX(startMs, :fromMs)), 0)
        FROM usage_sessions
        WHERE endMs > :fromMs AND startMs < :toMs AND section IN (:sections)
        """
    )
    fun observeTotalBetween(fromMs: Long, toMs: Long, sections: List<String>): Flow<Long>

    @Query("SELECT * FROM usage_sessions WHERE endMs > :fromMs AND startMs < :toMs ORDER BY startMs")
    suspend fun sessionsBetween(fromMs: Long, toMs: Long): List<UsageSession>

    /** Every app that has ever had a session — the dashboard's app filter. */
    @Query("SELECT DISTINCT platform FROM usage_sessions")
    fun observeTrackedPlatforms(): Flow<List<Platform>>

    @Query("SELECT * FROM usage_sessions WHERE endMs > :fromMs AND startMs < :toMs ORDER BY startMs")
    fun observeSessionsBetween(fromMs: Long, toMs: Long): Flow<List<UsageSession>>
}
