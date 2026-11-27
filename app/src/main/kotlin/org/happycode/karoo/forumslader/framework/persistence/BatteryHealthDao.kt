package org.happycode.karoo.forumslader.framework.persistence

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface BatteryHealthDao {
    @Query("SELECT * FROM battery_health_history ORDER BY timestampEpochMs ASC")
    suspend fun findAll(): List<BatteryHealthEntity>

    @Upsert
    suspend fun upsert(entity: BatteryHealthEntity)

    @Query("DELETE FROM battery_health_history WHERE timestampEpochMs < :cutoffEpochMs")
    suspend fun deleteOlderThan(cutoffEpochMs: Long)
}
