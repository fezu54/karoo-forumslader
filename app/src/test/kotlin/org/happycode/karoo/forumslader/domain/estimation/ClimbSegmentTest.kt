package org.happycode.karoo.forumslader.domain.estimation

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.floats.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class ClimbSegmentTest : ShouldSpec({

    should("return null when climb is already behind current route distance") {
        // given
        val climb = ClimbSegment(
            startDistanceMeters = 1000.0,
            lengthMeters = 500.0,
            gradientPct = 5.0f
        )

        // when
        val remaining = climb.remainingFrom(distanceAlongRouteMeters = 1500.0)

        // then
        remaining.shouldBeNull()
    }

    should("trim climb length when rider is currently mid-climb") {
        // given
        val climb = ClimbSegment(
            startDistanceMeters = 1000.0,
            lengthMeters = 500.0,
            gradientPct = 5.0f
        )

        // when
        val remaining = climb.remainingFrom(distanceAlongRouteMeters = 1200.0)

        // then
        remaining.shouldNotBeNull()
        remaining.startDistanceMeters shouldBe 1200.0
        remaining.lengthMeters shouldBe 300.0
        remaining.gradientPct shouldBe 5.0f
    }

    should("predict net energy drain when consumer power exceeds predicted dynamo power") {
        // given
        val climb = ClimbSegment(
            startDistanceMeters = 0.0,
            lengthMeters = 3600.0,
            gradientPct = 7.0f
        )
        val profile = GradientProfile(
            gradientPct = 7,
            speedMetersPerSecond = 2.0f,
            frequencyHz = 14.4f,
            dynamoPowerWatts = 1.0f
        )
        val dynamoCurve = DynamoPowerCurve()

        // when
        val netBalanceWh = climb.predictNetEnergyBalanceWh(
            profile = profile,
            baseProfile = profile,
            dynamoPowerCurve = dynamoCurve,
            consumerPowerWatts = 5.0f
        )

        // then
        netBalanceWh shouldBe (-2.0f plusOrMinus 0.01f)
    }
})
