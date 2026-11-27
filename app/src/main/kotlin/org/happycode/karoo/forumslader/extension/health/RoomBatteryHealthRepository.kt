package org.happycode.karoo.forumslader.extension.health

import org.happycode.karoo.forumslader.domain.health.BatteryHealthRepository
import org.happycode.karoo.forumslader.domain.health.BatteryHealthSnapshot
import org.happycode.karoo.forumslader.framework.persistence.BatteryHealthDao
import org.happycode.karoo.forumslader.framework.persistence.BatteryHealthEntity

class RoomBatteryHealthRepository(
    private val dao: BatteryHealthDao
) : BatteryHealthRepository {
    override suspend fun findAll(): List<BatteryHealthSnapshot> =
        dao.findAll().map { it.toDomain() }

    override suspend fun upsertForDay(epochDay: Long, snapshot: BatteryHealthSnapshot) {
        dao.upsert(BatteryHealthEntity.fromDomain(epochDay, snapshot))
    }

    override suspend fun deleteOlderThan(cutoffEpochMs: Long) {
        dao.deleteOlderThan(cutoffEpochMs)
    }
}
