package org.happycode.karoo.forumslader.domain.estimation

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.floats.shouldBeGreaterThan
import io.kotest.matchers.floats.shouldBeLessThan
import io.kotest.matchers.shouldBe

class RouteEnergyForecasterTest : ShouldSpec({

    val forecaster = RouteEnergyForecaster(
        elevationPenaltyPctPer100m = 1.5f,
        batteryCapacityWh = 13.3f,
        dynamoPowerCurve = DynamoPowerCurve()
    )

    should("offset separated climbs with inter-climb dynamo recovery during daytime riding") {
        // given: 60 km route with two 3 km steep climbs separated by 25 km of flat riding
        val climb1 = ClimbSegment(startDistanceMeters = 2_000.0, lengthMeters = 3_000.0, gradientPct = 9.0f)
        val climb2 = ClimbSegment(startDistanceMeters = 30_000.0, lengthMeters = 3_000.0, gradientPct = 9.0f)
        val profiles = mapOf(
            0 to GradientProfile(0, speedMetersPerSecond = 5.5f, frequencyHz = 39.6f, dynamoPowerWatts = 5.5f),
            9 to GradientProfile(9, speedMetersPerSecond = 1.8f, frequencyHz = 12.9f, dynamoPowerWatts = 0.6f)
        )

        // when: daytime consumer load (1.0W) vs night load (9.6W)
        val dayForecast = forecaster.forecast(
            currentLevelPct = 80f,
            routeRemainingKm = 60f,
            upcomingElevationMeters = null,
            upcomingClimbs = listOf(climb1, climb2),
            riderProfiles = profiles,
            currentConsumerPowerWatts = 1.0f,
            headwindSpeedMs = null
        )
        val nightForecast = forecaster.forecast(
            currentLevelPct = 80f,
            routeRemainingKm = 60f,
            upcomingElevationMeters = null,
            upcomingClimbs = listOf(climb1, climb2),
            riderProfiles = profiles,
            currentConsumerPowerWatts = 9.6f,
            headwindSpeedMs = null
        )

        // then: daytime retains higher effective capacity due to flat recharge between climbs
        val dayCapacity = dayForecast.adjustedCapacityPct(currentLevelPct = 80f, effectiveRatePctPerKm = 1.0f)
        val nightCapacity = nightForecast.adjustedCapacityPct(currentLevelPct = 80f, effectiveRatePctPerKm = 1.0f)
        dayCapacity shouldBeGreaterThan nightCapacity
        dayForecast.nonClimbRouteFraction shouldBe (0.9f plusOrMinus 0.01f)
        nightCapacity shouldBeLessThan 80f
    }

    should("deduct only excess climb deficit above effective rate budget to avoid night-time double counting") {
        // given: peak climb deficit is 30% over 10 km of climbs, effective rate is 1.5%/km (15% already budgeted)
        val forecast = RouteEnergyForecast(
            peakDeficitPct = 30f,
            bottleneckClimbKm = 10f,
            nonClimbRouteFraction = 0.8f
        )

        // when
        val adjustedCapacity = forecast.adjustedCapacityPct(
            currentLevelPct = 80f,
            effectiveRatePctPerKm = 1.5f
        )

        // then: excess = 30 - (10 * 1.5) = 15% -> 80 - 15 = 65% (conserves 30% climbs + 33.3km*1.5% flats = 80%)
        adjustedCapacity shouldBe (65.0f plusOrMinus 0.01f)
    }

    should("scale down elevation penalty on long gentle routes without climb segments") {
        // given: 100 km gentle rolling route with 1000m elevation (1.0% avg grade < 3% threshold)
        // when
        val forecast = forecaster.forecast(
            currentLevelPct = 80f,
            routeRemainingKm = 100f,
            upcomingElevationMeters = 1_000.0,
            upcomingClimbs = emptyList(),
            riderProfiles = emptyMap(),
            currentConsumerPowerWatts = 1.0f,
            headwindSpeedMs = null
        )

        // then: raw penalty 15% scaled by 1.0% / 3.0% = 5% -> 80 - 5 = 75%
        forecast.adjustedCapacityPct(currentLevelPct = 80f, effectiveRatePctPerKm = 1.0f) shouldBe (75.0f plusOrMinus 0.1f)
        forecast.nonClimbRouteFraction shouldBe 1f
    }

    should("apply full elevation penalty when average route gradient meets or exceeds three percent") {
        // given: 10 km route with 500m elevation (5.0% avg grade)
        // when
        val forecast = forecaster.forecast(
            currentLevelPct = 80f,
            routeRemainingKm = 10f,
            upcomingElevationMeters = 500.0,
            upcomingClimbs = emptyList(),
            riderProfiles = emptyMap(),
            currentConsumerPowerWatts = 1.0f,
            headwindSpeedMs = null
        )

        // then: full 5 * 1.5 = 7.5% penalty applied -> 72.5%
        forecast.adjustedCapacityPct(currentLevelPct = 80f, effectiveRatePctPerKm = 1.0f) shouldBe (72.5f plusOrMinus 0.01f)
    }

    should("scale climb deficit inversely when dynamic batteryCapacityWh override is supplied") {
        // given: steep climb causing a net Wh deficit
        val climb = ClimbSegment(startDistanceMeters = 1_000.0, lengthMeters = 3_000.0, gradientPct = 9.0f)
        val profiles = mapOf(
            9 to GradientProfile(9, speedMetersPerSecond = 1.8f, frequencyHz = 12.9f, dynamoPowerWatts = 0.6f)
        )

        // when: same climb evaluated with 10.0 Wh (degraded) vs 13.3 Wh (default) vs 33.3 Wh (18650)
        val degradedForecast = forecaster.forecast(
            currentLevelPct = 80f,
            routeRemainingKm = 3f,
            upcomingElevationMeters = null,
            upcomingClimbs = listOf(climb),
            riderProfiles = profiles,
            currentConsumerPowerWatts = 9.6f,
            headwindSpeedMs = null,
            batteryCapacityWh = 10.0f
        )
        val defaultForecast = forecaster.forecast(
            currentLevelPct = 80f,
            routeRemainingKm = 3f,
            upcomingElevationMeters = null,
            upcomingClimbs = listOf(climb),
            riderProfiles = profiles,
            currentConsumerPowerWatts = 9.6f,
            headwindSpeedMs = null
        )
        val largePackForecast = forecaster.forecast(
            currentLevelPct = 80f,
            routeRemainingKm = 3f,
            upcomingElevationMeters = null,
            upcomingClimbs = listOf(climb),
            riderProfiles = profiles,
            currentConsumerPowerWatts = 9.6f,
            headwindSpeedMs = null,
            batteryCapacityWh = 33.3f
        )

        // then
        degradedForecast.peakDeficitPct shouldBeGreaterThan defaultForecast.peakDeficitPct
        largePackForecast.peakDeficitPct shouldBeLessThan defaultForecast.peakDeficitPct
        (degradedForecast.peakDeficitPct / defaultForecast.peakDeficitPct) shouldBe ((13.3f / 10.0f) plusOrMinus 0.01f)
        (largePackForecast.peakDeficitPct / defaultForecast.peakDeficitPct) shouldBe ((13.3f / 33.3f) plusOrMinus 0.01f)
    }
})
