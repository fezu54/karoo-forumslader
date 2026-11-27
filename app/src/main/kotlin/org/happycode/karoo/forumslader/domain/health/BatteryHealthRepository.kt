package org.happycode.karoo.forumslader.domain.health

interface BatteryHealthRepository {
    suspend fun findAll(): List<BatteryHealthSnapshot>
    suspend fun upsertForDay(epochDay: Long, snapshot: BatteryHealthSnapshot)
    suspend fun deleteOlderThan(cutoffEpochMs: Long)
}
