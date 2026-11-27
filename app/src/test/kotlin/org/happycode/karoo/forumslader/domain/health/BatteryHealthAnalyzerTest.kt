package org.happycode.karoo.forumslader.domain.health

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class BatteryHealthAnalyzerTest : ShouldSpec({

    val analyzer = BatteryHealthAnalyzer()

    fun snapshot(
        day: Long,
        cycles: Int,
        mah: Int,
        soc: Int = 85
    ) = BatteryHealthSnapshot(
        timestampEpochMs = day * 86_400_000L,
        cycleCount = cycles,
        fullChargeCapacityMah = mah,
        socPct = soc
    )

    should("return null when snapshot history is empty") {
        // given / when / then
        analyzer.analyze(emptyList()).shouldBeNull()
    }

    should("return 100 percent SoH and null slope for a single snapshot") {
        // given
        val single = snapshot(day = 1, cycles = 5, mah = 1200)

        // when
        val summary = analyzer.analyze(listOf(single)).shouldNotBeNull()

        // then
        summary.currentCapacityMah shouldBe 1200
        summary.currentCapacityWh shouldBe (13.32f plusOrMinus 0.01f)
        summary.baselineCapacityMah shouldBe 1200
        summary.stateOfHealthPct shouldBe (100f plusOrMinus 0.01f)
        summary.cycleCount shouldBe 5
        summary.status shouldBe BatteryHealthStatus.GOOD
        summary.slopeMahPerCycle.shouldBeNull()
        summary.estimatedCyclesRemaining.shouldBeNull()
        summary.history shouldBe listOf(single)
    }

    should("compute max-observed baseline, SoH, OLS slope, and remaining cycles to 60 percent threshold") {
        // given: linear degradation of -2 mAh/cycle from 1200 mAh baseline
        // 60% of 1200 = 720 mAh -> at cycle 100 (1000 mAh), remaining cycles = (1000 - 720) / 2 = 140
        val snapshots = listOf(
            snapshot(day = 1, cycles = 0, mah = 1200),
            snapshot(day = 10, cycles = 50, mah = 1100),
            snapshot(day = 20, cycles = 100, mah = 1000)
        )

        // when
        val summary = analyzer.analyze(snapshots).shouldNotBeNull()

        // then
        summary.baselineCapacityMah shouldBe 1200
        summary.currentCapacityMah shouldBe 1000
        summary.stateOfHealthPct shouldBe (83.33f plusOrMinus 0.1f)
        summary.status shouldBe BatteryHealthStatus.GOOD
        summary.slopeMahPerCycle.shouldNotBeNull() shouldBe (-2.0f plusOrMinus 0.01f)
        summary.estimatedCyclesRemaining shouldBe 140
    }

    should("classify status across GOOD, DEGRADING, and REPLACE_SOON thresholds") {
        // given / when
        val goodAt80 = analyzer.analyze(
            listOf(snapshot(1, 0, 1000), snapshot(2, 10, 800))
        ).shouldNotBeNull()
        val degradingAt79 = analyzer.analyze(
            listOf(snapshot(1, 0, 1000), snapshot(2, 10, 799))
        ).shouldNotBeNull()
        val degradingAt60 = analyzer.analyze(
            listOf(snapshot(1, 0, 1000), snapshot(2, 10, 600))
        ).shouldNotBeNull()
        val replaceSoonBelow60 = analyzer.analyze(
            listOf(snapshot(1, 0, 1000), snapshot(2, 10, 599))
        ).shouldNotBeNull()

        // then
        goodAt80.status shouldBe BatteryHealthStatus.GOOD
        degradingAt79.status shouldBe BatteryHealthStatus.DEGRADING
        degradingAt60.status shouldBe BatteryHealthStatus.DEGRADING
        replaceSoonBelow60.status shouldBe BatteryHealthStatus.REPLACE_SOON
        replaceSoonBelow60.estimatedCyclesRemaining shouldBe 0
    }

    should("reset baseline and active history when cycleCount drops significantly indicating battery replacement") {
        // given: old degraded pack at 150 cycles replaced by new 1250 mAh pack at 2 cycles
        val old1 = snapshot(day = 1, cycles = 10, mah = 1200)
        val old2 = snapshot(day = 50, cycles = 150, mah = 700)
        val new1 = snapshot(day = 51, cycles = 2, mah = 1250)
        val new2 = snapshot(day = 60, cycles = 22, mah = 1225)

        // when
        val summary = analyzer.analyze(listOf(old1, old2, new1, new2)).shouldNotBeNull()

        // then
        summary.baselineCapacityMah shouldBe 1250
        summary.currentCapacityMah shouldBe 1225
        summary.cycleCount shouldBe 22
        summary.stateOfHealthPct shouldBe (98.0f plusOrMinus 0.1f)
        summary.status shouldBe BatteryHealthStatus.GOOD
        summary.history shouldBe listOf(new1, new2)
    }

    should("not reset baseline on minor cycle count jitter below threshold") {
        // given: drop of 2 cycles (< 5 threshold)
        val snapshots = listOf(
            snapshot(day = 1, cycles = 100, mah = 1200),
            snapshot(day = 2, cycles = 98, mah = 1150)
        )

        // when
        val summary = analyzer.analyze(snapshots).shouldNotBeNull()

        // then
        summary.baselineCapacityMah shouldBe 1200
        summary.history.size shouldBe 2
    }

    should("return null remaining cycles when slope is flat or upward or cycle counts do not vary") {
        // given
        val flatHistory = listOf(snapshot(1, 10, 1150), snapshot(2, 20, 1160))
        val sameCycleHistory = listOf(snapshot(1, 10, 1200), snapshot(2, 10, 1180))

        // when
        val flatSummary = analyzer.analyze(flatHistory).shouldNotBeNull()
        val sameCycleSummary = analyzer.analyze(sameCycleHistory).shouldNotBeNull()

        // then
        flatSummary.estimatedCyclesRemaining.shouldBeNull()
        sameCycleSummary.slopeMahPerCycle.shouldBeNull()
        sameCycleSummary.estimatedCyclesRemaining.shouldBeNull()
    }

    should("sort unsorted snapshots chronologically before analyzing") {
        // given
        val newer = snapshot(day = 20, cycles = 50, mah = 1100)
        val older = snapshot(day = 5, cycles = 10, mah = 1200)

        // when
        val summary = analyzer.analyze(listOf(newer, older)).shouldNotBeNull()

        // then
        summary.history shouldBe listOf(older, newer)
        summary.baselineCapacityMah shouldBe 1200
        summary.currentCapacityMah shouldBe 1100
    }

    should("return null when active history has non-positive baseline capacity") {
        // given / when / then
        analyzer.analyze(listOf(snapshot(day = 1, cycles = 0, mah = 0))).shouldBeNull()
        analyzer.analyze(listOf(snapshot(day = 1, cycles = 0, mah = -50))).shouldBeNull()
    }
})
