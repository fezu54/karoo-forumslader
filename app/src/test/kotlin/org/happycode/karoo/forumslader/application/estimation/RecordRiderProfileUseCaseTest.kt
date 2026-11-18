package org.happycode.karoo.forumslader.application.estimation

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import org.happycode.karoo.forumslader.domain.ChargeState
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics
import org.happycode.karoo.forumslader.domain.estimation.GradientProfile
import org.happycode.karoo.forumslader.domain.estimation.RiderProfileRepository

class RecordRiderProfileUseCaseTest : ShouldSpec({

    lateinit var repository: RiderProfileRepository
    lateinit var useCase: RecordRiderProfileUseCase

    beforeEach {
        repository = mockk(relaxed = true)
        useCase = RecordRiderProfileUseCase(repository)
    }

    fun createMetrics(
        speedMs: Float = 3.5f,
        frequencyHz: Float = 25.0f,
        dynamoWatts: Float = 3.0f,
        chargeState: ChargeState = ChargeState.DISCHARGING,
        tripMeters: Double = 1000.0,
        gear: Int = 1,
        batteryPct: Int? = 80
    ) = ForumsladerMetrics(
        power = ForumsladerMetrics.Power(
            batteryVoltage = 12.0f,
            batteryCurrent = -0.5f,
            consumerCurrent = 0.5f,
            batteryLevelPercentage = batteryPct,
            chargeState = chargeState,
            dynamoPowerWatts = dynamoWatts,
            statusMask = 0
        ),
        dynamics = ForumsladerMetrics.Dynamics(
            frequency = frequencyHz,
            speedMetersPerSecond = speedMs,
            generatorGear = gear
        ),
        environment = ForumsladerMetrics.Environment(
            temperatureCelsius = 20f,
            altitudeMeters = 100f
        ),
        energy = ForumsladerMetrics.Energy(
            tripWattHours = 0.0,
            tourWattHours = 0.0
        ),
        distance = ForumsladerMetrics.Distance(
            tripMeters = tripMeters,
            dayMeters = tripMeters,
            tourMeters = tripMeters,
            odometerMeters = tripMeters
        )
    )

    should("record and update gradient profile when rider is moving and battery is not full") {
        // given
        val metrics = createMetrics(speedMs = 4.0f, frequencyHz = 28.0f, dynamoWatts = 4.0f)
        val existingProfile = GradientProfile(
            gradientPct = 5,
            speedMetersPerSecond = 3.0f,
            frequencyHz = 22.0f,
            dynamoPowerWatts = 2.0f
        )
        coEvery { repository.findByGradient(5) } returns existingProfile

        // when
        val recorded = useCase.onMetricsAndGradient(metrics, gradientPct = 5.2f)

        // then
        recorded.shouldNotBeNull()
        recorded.gradientPct shouldBe 5
        coVerify { repository.save(recorded) }
    }

    should("ignore sample when speed is below threshold or charge state is FULL") {
        // given
        val slowMetrics = createMetrics(speedMs = 0.8f)
        val fullBatteryMetrics = createMetrics(speedMs = 5.0f, chargeState = ChargeState.FULL)
        val lowFrequencyMetrics = createMetrics(speedMs = 3.0f, frequencyHz = 3.0f)

        // when
        val resultSlow = useCase.onMetricsAndGradient(slowMetrics, gradientPct = 4.0f)
        val resultFull = useCase.onMetricsAndGradient(fullBatteryMetrics, gradientPct = 4.0f)
        val resultLowFreq = useCase.onMetricsAndGradient(lowFrequencyMetrics, gradientPct = 4.0f)
        val resultNan = useCase.onMetricsAndGradient(createMetrics(), gradientPct = Float.NaN)
        val resultNull = useCase.onMetricsAndGradient(createMetrics(), gradientPct = null)

        // then
        resultSlow.shouldBeNull()
        resultFull.shouldBeNull()
        resultLowFreq.shouldBeNull()
        resultNan.shouldBeNull()
        resultNull.shouldBeNull()
        coVerify(exactly = 0) { repository.save(any()) }
    }

    should("gate profile updates by distance traveled and batch SQLite writes until flush is called") {
        // given
        val batchedUseCase = RecordRiderProfileUseCase(
            repository = repository,
            persistImmediately = false,
            minDistanceStepMeters = 50.0
        )
        val initialProfile = GradientProfile(
            gradientPct = 0,
            speedMetersPerSecond = 5.0f,
            frequencyHz = 36.0f,
            dynamoPowerWatts = 4.0f
        )
        batchedUseCase.onProfilesLoaded(mapOf(0 to initialProfile))

        // when: riding 20m then another 35m (total 55m >= 50m)
        val step1 = batchedUseCase.onMetricsAndGradient(
            createMetrics(speedMs = 6.0f, frequencyHz = 43.2f, dynamoWatts = 5.0f, tripMeters = 100.0),
            gradientPct = 0.0f
        )
        val step2 = batchedUseCase.onMetricsAndGradient(
            createMetrics(speedMs = 6.0f, frequencyHz = 43.2f, dynamoWatts = 5.0f, tripMeters = 120.0),
            gradientPct = 0.0f
        )
        val step3 = batchedUseCase.onMetricsAndGradient(
            createMetrics(speedMs = 6.0f, frequencyHz = 43.2f, dynamoWatts = 5.0f, tripMeters = 155.0),
            gradientPct = 0.0f
        )

        // then: only step3 emits an in-memory update, and no DB write occurs until flush()
        step1.shouldBeNull()
        step2.shouldBeNull()
        step3.shouldNotBeNull()
        coVerify(exactly = 0) { repository.save(any()) }
        coVerify(exactly = 0) { repository.findByGradient(any()) }

        // when
        batchedUseCase.flush()

        // then
        val savedSlot = slot<GradientProfile>()
        coVerify(exactly = 1) { repository.save(capture(savedSlot)) }
        savedSlot.captured.gradientPct shouldBe 0
        savedSlot.captured.speedMetersPerSecond shouldBe (5.05f plusOrMinus 0.01f)
    }

    should("not credit non-recordable FULL battery distance into the next climb gradient bucket") {
        // given
        val batchedUseCase = RecordRiderProfileUseCase(
            repository = repository,
            persistImmediately = false,
            minDistanceStepMeters = 50.0
        )
        batchedUseCase.onProfilesLoaded(emptyMap())

        // when: riding 400m while FULL, then entering a 6% climb for only 10m
        batchedUseCase.onMetricsAndGradient(
            createMetrics(chargeState = ChargeState.FULL, tripMeters = 100.0),
            gradientPct = 0.0f
        )
        batchedUseCase.onMetricsAndGradient(
            createMetrics(chargeState = ChargeState.FULL, tripMeters = 500.0),
            gradientPct = 0.0f
        )
        val climbTick = batchedUseCase.onMetricsAndGradient(
            createMetrics(chargeState = ChargeState.DISCHARGING, tripMeters = 510.0),
            gradientPct = 6.0f
        )

        // then: only 10m accumulated on the climb (< 50m threshold), so no premature update
        climbTick.shouldBeNull()
    }

    should("preserve learned dynamo power when hysteresis disables generator gear or battery is near full") {
        // given
        val batchedUseCase = RecordRiderProfileUseCase(
            repository = repository,
            persistImmediately = false,
            minDistanceStepMeters = 0.0
        )
        batchedUseCase.onProfilesLoaded(
            mapOf(0 to GradientProfile(0, speedMetersPerSecond = 5.0f, frequencyHz = 36.0f, dynamoPowerWatts = 4.5f))
        )

        // when: generatorGear == 0 (hysteresis coasting) with 0 W dynamo output
        val updated = batchedUseCase.onMetricsAndGradient(
            createMetrics(speedMs = 6.0f, frequencyHz = 43.2f, dynamoWatts = 0.0f, gear = 0, batteryPct = 99),
            gradientPct = 0.0f
        )

        // then
        updated.shouldNotBeNull()
        updated.dynamoPowerWatts shouldBe 4.5f
    }
})
