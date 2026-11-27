package org.happycode.karoo.forumslader.domain.health

data class BatteryHealthSummary(
    val currentCapacityMah: Int,
    val currentCapacityWh: Float,
    val baselineCapacityMah: Int,
    val stateOfHealthPct: Float,
    val cycleCount: Int,
    val status: BatteryHealthStatus,
    val slopeMahPerCycle: Float?,
    val estimatedCyclesRemaining: Int?,
    val history: List<BatteryHealthSnapshot>
)
