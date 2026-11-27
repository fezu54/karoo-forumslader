package org.happycode.karoo.forumslader.application.health

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.happycode.karoo.forumslader.domain.ChargeState
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics
import org.happycode.karoo.forumslader.domain.health.BatteryHealthRepository
import org.happycode.karoo.forumslader.domain.health.BatteryHealthSnapshot
import org.happycode.karoo.forumslader.domain.health.BatteryHealthSummary

class RecordBatteryHealthUseCaseTest : ShouldSpec({

    fun createMetrics(
        capacityMah: Int? = 1140,
        cycleCount: Int? = 127,
        socPct: Int? = 85
    ) = ForumsladerMetrics(
        power = ForumsladerMetrics.Power(
            batteryVoltage = 11.8f,
            batteryCurrent = -0.2f,
            consumerCurrent = 0.2f,
            batteryLevelPercentage = socPct,
            chargeState = ChargeState.DISCHARGING,
            dynamoPowerWatts = 0f,
            statusMask = 0,
            fullChargeCapacityMah = capacityMah,
            cycleCount = cycleCount
        ),
        dynamics = ForumsladerMetrics.Dynamics(0f, 0f, 0),
        environment = ForumsladerMetrics.Environment(20f, 0f),
        energy = ForumsladerMetrics.Energy(0.0, 0.0),
        distance = ForumsladerMetrics.Distance(0.0, 0.0, 0.0, 0.0)
    )

    should("load persisted history, prune entries older than 730 days, and emit summary") {
        runTest {
            // given
            val nowMs = 1_000L * RecordBatteryHealthUseCase.MILLIS_PER_DAY
            val expectedCutoff = nowMs - (RecordBatteryHealthUseCase.RETENTION_DAYS * RecordBatteryHealthUseCase.MILLIS_PER_DAY)
            val saved = BatteryHealthSnapshot(nowMs - 10_000L, cycleCount = 40, fullChargeCapacityMah = 1180, socPct = 90)
            val repository = mockk<BatteryHealthRepository>(relaxed = true)
            coEvery { repository.findAll() } returns listOf(saved)

            var emitted: BatteryHealthSummary? = null
            val useCase = RecordBatteryHealthUseCase(
                repository = repository,
                clockMs = { nowMs },
                onSummaryUpdated = { emitted = it }
            )

            // when
            val loaded = useCase.loadHistory()

            // then
            coVerify(exactly = 1) { repository.deleteOlderThan(expectedCutoff) }
            coVerify(exactly = 1) { repository.findAll() }
            loaded.shouldNotBeNull().currentCapacityMah shouldBe 1180
            emitted shouldBe loaded
        }
    }

    should("buffer valid metrics in memory with zero repository writes and ignore invalid readings") {
        runTest {
            // given
            val nowMs = 500L * RecordBatteryHealthUseCase.MILLIS_PER_DAY
            val repository = mockk<BatteryHealthRepository>(relaxed = true)
            var emitted: BatteryHealthSummary? = null
            val useCase = RecordBatteryHealthUseCase(
                repository = repository,
                clockMs = { nowMs },
                onSummaryUpdated = { emitted = it }
            )

            // when: invalid capacity / negative cycle / null soc
            useCase.onMetrics(createMetrics(capacityMah = 0, cycleCount = 10, socPct = 80)).shouldBeNull()
            useCase.onMetrics(createMetrics(capacityMah = 449, cycleCount = 10, socPct = 80)).shouldBeNull()
            useCase.onMetrics(createMetrics(capacityMah = 6001, cycleCount = 10, socPct = 80)).shouldBeNull()
            useCase.onMetrics(createMetrics(capacityMah = 1140, cycleCount = -1, socPct = 80)).shouldBeNull()
            useCase.onMetrics(createMetrics(capacityMah = 1140, cycleCount = 10, socPct = null)).shouldBeNull()

            // and when: valid reading arrives
            val liveSummary = useCase.onMetrics(createMetrics(capacityMah = 1140, cycleCount = 127, socPct = 85))

            // then: zero SQLite writes during 1 Hz telemetry, live summary updated
            coVerify(exactly = 0) { repository.upsertForDay(any(), any()) }
            liveSummary.shouldNotBeNull().currentCapacityMah shouldBe 1140
            emitted shouldBe liveSummary
        }
    }

    should("flush pending snapshot once per UTC day and prune expired records") {
        runTest {
            // given
            val day = 20_000L
            var nowMs = day * RecordBatteryHealthUseCase.MILLIS_PER_DAY + 3_600_000L
            val repository = mockk<BatteryHealthRepository>(relaxed = true)
            coEvery { repository.findAll() } returns emptyList()

            val useCase = RecordBatteryHealthUseCase(
                repository = repository,
                clockMs = { nowMs }
            )
            useCase.onMetrics(createMetrics(capacityMah = 1150, cycleCount = 126, socPct = 80))
            nowMs += 60_000L
            useCase.onMetrics(createMetrics(capacityMah = 1140, cycleCount = 127, socPct = 85))

            // when: first flush persists latest reading for epochDay
            useCase.flush()

            // then
            coVerify(exactly = 1) {
                repository.upsertForDay(
                    day,
                    BatteryHealthSnapshot(
                        timestampEpochMs = nowMs,
                        cycleCount = 127,
                        fullChargeCapacityMah = 1140,
                        socPct = 85
                    )
                )
            }
            coVerify(exactly = 1) {
                repository.deleteOlderThan(nowMs - (RecordBatteryHealthUseCase.RETENTION_DAYS * RecordBatteryHealthUseCase.MILLIS_PER_DAY))
            }

            // when: second flush with no new metrics
            useCase.flush()

            // then: no duplicate write
            coVerify(exactly = 1) { repository.upsertForDay(any(), any()) }
        }
    }
})
