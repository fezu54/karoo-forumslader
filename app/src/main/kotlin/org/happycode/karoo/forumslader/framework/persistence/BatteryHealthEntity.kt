package org.happycode.karoo.forumslader.framework.persistence

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.happycode.karoo.forumslader.domain.health.BatteryHealthSnapshot

@Entity(tableName = "battery_health_history")
data class BatteryHealthEntity(
    @PrimaryKey val epochDay: Long,
    val timestampEpochMs: Long,
    val cycleCount: Int,
    val fullChargeCapacityMah: Int,
    val socPct: Int
) {
    fun toDomain(): BatteryHealthSnapshot = BatteryHealthSnapshot(
        timestampEpochMs = timestampEpochMs,
        cycleCount = cycleCount,
        fullChargeCapacityMah = fullChargeCapacityMah,
        socPct = socPct
    )

    companion object {
        fun fromDomain(epochDay: Long, snapshot: BatteryHealthSnapshot): BatteryHealthEntity =
            BatteryHealthEntity(
                epochDay = epochDay,
                timestampEpochMs = snapshot.timestampEpochMs,
                cycleCount = snapshot.cycleCount,
                fullChargeCapacityMah = snapshot.fullChargeCapacityMah,
                socPct = snapshot.socPct
            )
    }
}
