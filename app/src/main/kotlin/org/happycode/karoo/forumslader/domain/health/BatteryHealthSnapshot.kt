package org.happycode.karoo.forumslader.domain.health

data class BatteryHealthSnapshot(
    val timestampEpochMs: Long,
    val cycleCount: Int,
    val fullChargeCapacityMah: Int,
    val socPct: Int
)
