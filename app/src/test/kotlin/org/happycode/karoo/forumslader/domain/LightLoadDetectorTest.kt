package org.happycode.karoo.forumslader.domain

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class LightLoadDetectorTest : ShouldSpec({

    fun createMetrics(
        batteryVoltage: Float = 12.0f,
        batteryCurrent: Float = 0.0f,
        consumerCurrent: Float = 0.0f,
        dynamoPowerWatts: Float = 0.0f
    ) = ForumsladerMetrics(
        power = ForumsladerMetrics.Power(
            batteryVoltage = batteryVoltage,
            batteryCurrent = batteryCurrent,
            consumerCurrent = consumerCurrent,
            batteryLevelPercentage = 80,
            chargeState = ChargeState.STANDBY,
            dynamoPowerWatts = dynamoPowerWatts,
            statusMask = 0
        ),
        dynamics = ForumsladerMetrics.Dynamics(
            frequency = 0f,
            speedMetersPerSecond = 0f,
            generatorGear = 0
        ),
        environment = ForumsladerMetrics.Environment(
            temperatureCelsius = 20f,
            altitudeMeters = 0f
        ),
        energy = ForumsladerMetrics.Energy(tripWattHours = 0.0, tourWattHours = 0.0),
        distance = ForumsladerMetrics.Distance(
            tripMeters = 0.0,
            dayMeters = 0.0,
            tourMeters = 0.0,
            odometerMeters = 0.0
        )
    )

    should("detect 12V light when battery discharge increases without consumer current increase") {
        // given
        val detector = LightLoadDetector()
        val initialMetrics = createMetrics(batteryCurrent = 0.0f, consumerCurrent = 0.0f)
        val lightOnMetrics = createMetrics(batteryCurrent = -0.5f, consumerCurrent = 0.0f)

        // when & then
        detector.onMetrics(initialMetrics, currentTime = Duration.ZERO) shouldBe LightLoadState.IDLE

        // Light turned on -> suspected
        detector.onMetrics(lightOnMetrics, currentTime = 1.seconds) shouldBe LightLoadState.SUSPECTED
        detector.onMetrics(lightOnMetrics, currentTime = 2.seconds) shouldBe LightLoadState.SUSPECTED
        detector.onMetrics(lightOnMetrics, currentTime = 3.seconds) shouldBe LightLoadState.SUSPECTED

        // Sustained for >= 3s (from 1s to 4s) -> detected
        detector.onMetrics(lightOnMetrics, currentTime = 4.seconds) shouldBe LightLoadState.DETECTED
        detector.state shouldBe LightLoadState.DETECTED
    }

    should("not detect 12V light when phone is plugged in mid-ride") {
        // given
        val detector = LightLoadDetector()
        val baselineMetrics = createMetrics(batteryVoltage = 12.0f, batteryCurrent = 0.0f, consumerCurrent = 0.0f)
        detector.onMetrics(baselineMetrics, currentTime = Duration.ZERO) shouldBe LightLoadState.IDLE

        // Phone plugged in: consumer current increases from 0.0A to 1.5A
        // Buck efficiency ~0.9, 5V * 1.5A / (12V * 0.9) = 0.694A discharge from battery
        val phonePluggedInMetrics = createMetrics(
            batteryVoltage = 12.0f,
            batteryCurrent = -0.694f,
            consumerCurrent = 1.5f
        )

        // when & then
        detector.onMetrics(phonePluggedInMetrics, currentTime = 1.seconds) shouldBe LightLoadState.IDLE
        detector.onMetrics(phonePluggedInMetrics, currentTime = 3.seconds) shouldBe LightLoadState.IDLE
        detector.onMetrics(phonePluggedInMetrics, currentTime = 6.seconds) shouldBe LightLoadState.IDLE
        detector.state shouldBe LightLoadState.IDLE
    }

    should("ignore transient current spikes under 3 seconds") {
        // given
        val detector = LightLoadDetector()
        val baseline = createMetrics(batteryCurrent = 0.0f)
        val spike = createMetrics(batteryCurrent = -0.5f)

        // when
        detector.onMetrics(baseline, currentTime = Duration.ZERO) shouldBe LightLoadState.IDLE
        detector.onMetrics(spike, currentTime = 1.seconds) shouldBe LightLoadState.SUSPECTED
        detector.onMetrics(spike, currentTime = 2.seconds) shouldBe LightLoadState.SUSPECTED
        // Spike ends before 3s debounce duration
        val recovered = detector.onMetrics(baseline, currentTime = 3.seconds)

        // then
        recovered shouldBe LightLoadState.IDLE
        detector.onMetrics(baseline, currentTime = 5.seconds) shouldBe LightLoadState.IDLE
        detector.state shouldBe LightLoadState.IDLE
    }

    should("reset detection when light is switched off") {
        // given
        val detector = LightLoadDetector()
        val lightOn = createMetrics(batteryCurrent = -0.5f)
        val lightOff = createMetrics(batteryCurrent = 0.0f)

        detector.onMetrics(lightOn, currentTime = 1.seconds) shouldBe LightLoadState.SUSPECTED
        detector.onMetrics(lightOn, currentTime = 4.seconds) shouldBe LightLoadState.DETECTED

        // when
        val afterSwitchOff = detector.onMetrics(lightOff, currentTime = 5.seconds)

        // then
        afterSwitchOff shouldBe LightLoadState.IDLE
        detector.state shouldBe LightLoadState.IDLE
    }

    should("suppress detection during 5 second USB plug-in grace period") {
        // given
        val detector = LightLoadDetector()
        val initial = createMetrics(consumerCurrent = 0.0f, batteryCurrent = 0.0f)
        detector.onMetrics(initial, currentTime = Duration.ZERO)

        // Step-change in consumer current triggers grace period
        val plugIn = createMetrics(consumerCurrent = 0.5f, batteryCurrent = -0.8f)
        detector.onMetrics(plugIn, currentTime = 1.seconds) shouldBe LightLoadState.IDLE

        // when - within 5 seconds grace period (until 6s)
        detector.onMetrics(plugIn, currentTime = 3.seconds) shouldBe LightLoadState.IDLE
        detector.onMetrics(plugIn, currentTime = 5.seconds) shouldBe LightLoadState.IDLE

        // then - after grace period expired (at 6.1s), now can enter SUSPECTED
        detector.onMetrics(plugIn, currentTime = 7.seconds) shouldBe LightLoadState.SUSPECTED
    }

    should("account for dynamo power when calculating unaccounted load") {
        // given
        val detector = LightLoadDetector()
        // 12V battery, dynamo generates 6W (0.5A at 12V).
        // Headlight draws 0.8A, so net battery current is -0.3A (0.5A dynamo - 0.8A headlight).
        val metricsWithDynamo = createMetrics(
            batteryVoltage = 12.0f,
            batteryCurrent = -0.3f,
            consumerCurrent = 0.0f,
            dynamoPowerWatts = 6.0f
        )

        // when
        val unaccountedLoad = detector.calculateUnaccountedLoad(metricsWithDynamo)

        // then: -(-0.3) - 0 + (6 / 12) = 0.3 + 0.5 = 0.8A
        unaccountedLoad shouldBe 0.8f
    }

    should("handle zero battery voltage safely without throwing") {
        // given
        val detector = LightLoadDetector()
        val zeroVoltMetrics = createMetrics(batteryVoltage = 0.0f, batteryCurrent = -0.5f)

        // when
        val load = detector.calculateUnaccountedLoad(zeroVoltMetrics)
        val state = detector.onMetrics(zeroVoltMetrics, currentTime = 1.seconds)

        // then
        load shouldBe 0.0f
        state shouldBe LightLoadState.IDLE
    }

    should("reset state when reset is called") {
        // given
        val detector = LightLoadDetector()
        val lightOn = createMetrics(batteryCurrent = -0.5f)
        detector.onMetrics(lightOn, currentTime = 1.seconds)
        detector.onMetrics(lightOn, currentTime = 4.seconds) shouldBe LightLoadState.DETECTED

        // when
        detector.reset()

        // then
        detector.state shouldBe LightLoadState.IDLE
    }
})
