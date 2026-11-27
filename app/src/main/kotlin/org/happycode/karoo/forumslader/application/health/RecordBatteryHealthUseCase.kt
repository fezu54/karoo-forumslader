package org.happycode.karoo.forumslader.application.health

import org.happycode.karoo.forumslader.domain.ForumsladerMetrics
import org.happycode.karoo.forumslader.domain.estimation.BatteryCapacity
import org.happycode.karoo.forumslader.domain.health.BatteryHealthAnalyzer
import org.happycode.karoo.forumslader.domain.health.BatteryHealthRepository
import org.happycode.karoo.forumslader.domain.health.BatteryHealthSnapshot
import org.happycode.karoo.forumslader.domain.health.BatteryHealthSummary

/**
 * Buffers 1 Hz battery health metrics in memory and flushes at most one snapshot per UTC day
 * to persistence, pruning entries older than [RETENTION_DAYS].
 */
class RecordBatteryHealthUseCase(
    private val repository: BatteryHealthRepository,
    private val analyzer: BatteryHealthAnalyzer = BatteryHealthAnalyzer(),
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val onSummaryUpdated: (BatteryHealthSummary?) -> Unit = {}
) {
    private val persistedByDay = mutableMapOf<Long, BatteryHealthSnapshot>()
    private var pendingSnapshot: BatteryHealthSnapshot? = null

    suspend fun loadHistory(): BatteryHealthSummary? {
        val cutoff = retentionCutoffMs(clockMs())
        repository.deleteOlderThan(cutoff)
        persistedByDay.clear()
        repository.findAll().forEach { snapshot ->
            persistedByDay[epochDayFor(snapshot.timestampEpochMs)] = snapshot
        }
        return publishCurrentSummary()
    }

    fun onMetrics(metrics: ForumsladerMetrics): BatteryHealthSummary? {
        val capacityMah = metrics.power.fullChargeCapacityMah?.takeIf(BatteryCapacity::isCredibleMah) ?: return null
        val cycleCount = metrics.power.cycleCount?.takeIf { it >= 0 } ?: return null
        val socPct = metrics.power.batteryLevelPercentage?.takeIf { it in 0..MAX_SOC_PCT } ?: return null

        pendingSnapshot = BatteryHealthSnapshot(
            timestampEpochMs = clockMs(),
            cycleCount = cycleCount,
            fullChargeCapacityMah = capacityMah,
            socPct = socPct
        )
        return publishCurrentSummary()
    }

    suspend fun flush() {
        val snapshot = pendingSnapshot ?: return
        pendingSnapshot = null

        val epochDay = epochDayFor(snapshot.timestampEpochMs)
        val cutoff = retentionCutoffMs(clockMs())
        repository.upsertForDay(epochDay, snapshot)
        repository.deleteOlderThan(cutoff)

        persistedByDay[epochDay] = snapshot
        persistedByDay.entries.removeAll { it.value.timestampEpochMs < cutoff }
        publishCurrentSummary()
    }

    private fun publishCurrentSummary(): BatteryHealthSummary? {
        val combined = buildList {
            val merged = persistedByDay.toMutableMap()
            pendingSnapshot?.let { pending ->
                merged[epochDayFor(pending.timestampEpochMs)] = pending
            }
            addAll(merged.values)
        }
        return analyzer.analyze(combined).also(onSummaryUpdated)
    }

    private fun epochDayFor(timestampMs: Long): Long = timestampMs / MILLIS_PER_DAY

    private fun retentionCutoffMs(nowMs: Long): Long = nowMs - (RETENTION_DAYS * MILLIS_PER_DAY)

    companion object {
        const val MILLIS_PER_DAY = 86_400_000L
        const val RETENTION_DAYS = 730L
        private const val MAX_SOC_PCT = 100
    }
}
