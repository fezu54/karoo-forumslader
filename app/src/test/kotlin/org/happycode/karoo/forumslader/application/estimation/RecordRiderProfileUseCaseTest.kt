package org.happycode.karoo.forumslader.application.estimation

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
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
        chargeState: ChargeState = ChargeState.DISCHARGING
    ) = ForumsladerMetrics(
        power = ForumsladerMetrics.Power(
            batteryVoltage = 12.0f,
            batteryCurrent = -0.5f,
            consumerCurrent = 0.5f,
            batteryLevelPercentage = 80,
            chargeState = chargeState,
            dynamoPowerWatts = dynamoWatts,
            statusMask = 0
        ),
        dynamics = ForumsladerMetrics.Dynamics(
            frequency = frequencyHz,
            speedMetersPerSecond = speedMs,
            generatorGear = 1
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
            tripMeters = 1000.0,
            dayMeters = 1000.0,
            tourMeters = 1000.0,
            odometerMeters = 1000.0
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
})
