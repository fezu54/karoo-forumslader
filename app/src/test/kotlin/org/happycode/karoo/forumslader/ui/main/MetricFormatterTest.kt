package org.happycode.karoo.forumslader.ui.main

import android.content.Context
import io.hammerhead.karooext.models.UserProfile
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import java.util.Locale

class MetricFormatterTest : ShouldSpec({
    val context = mockk<Context>()

    fun createFormatter(
        isImperialDistance: Boolean = false,
        isImperialTemperature: Boolean = false
    ) = MetricFormatter(
        locale = Locale.US,
        isImperialDistance = isImperialDistance,
        isImperialTemperature = isImperialTemperature,
        context
    )

    beforeEach {
        every { context.getString(R.string.charge_state_standby) } returns "Standby"
        every { context.getString(R.string.charge_state_charging) } returns "Charging"
        every { context.getString(R.string.charge_state_discharging) } returns "Discharging"
        every { context.getString(R.string.charge_state_full) } returns "Full"
        every { context.getString(R.string.battery_range_calculating) } returns "Calculating…"
        every { context.getString(R.string.status_not_available) } returns "N/A"
    }

    should("format missing value as dashes when value is null") {
        // given
        val formatter = createFormatter()

        // when
        val formatted = formatter.format(DataFieldId.SPEED, null)

        // then
        formatted shouldBe "---"
    }

    should("format speed in metric when isImperialDistance is false") {
        // given
        val formatter = createFormatter(isImperialDistance = false)

        // when
        val formatted = formatter.format(DataFieldId.SPEED, 10.0)

        // then
        formatted shouldBe "36.0 km/h"
    }

    should("format speed in imperial when isImperialDistance is true") {
        // given
        val formatter = createFormatter(isImperialDistance = true)

        // when
        val formatted = formatter.format(DataFieldId.SPEED, 10.0)

        // then
        formatted shouldBe "22.4 mph"
    }

    should("format trip distance in metric") {
        // given
        val formatter = createFormatter(isImperialDistance = false)

        // when
        val formatted = formatter.format(DataFieldId.TRIP_DISTANCE, 1500.0)

        // then
        formatted shouldBe "1.50 km"
    }

    should("format trip distance in imperial") {
        // given
        val formatter = createFormatter(isImperialDistance = true)

        // when
        val formatted = formatter.format(DataFieldId.TRIP_DISTANCE, 1500.0)

        // then
        formatted shouldBe "0.93 mi"
    }

    should("format battery level as percentage") {
        // given
        val formatter = createFormatter()

        // when
        val formatted = formatter.format(DataFieldId.BATTERY_LEVEL, 85.0)

        // then
        formatted shouldBe "85%"
    }

    should("format battery voltage") {
        // given
        val formatter = createFormatter()

        // when
        val formatted = formatter.format(DataFieldId.BATTERY_VOLTAGE, 12.5)

        // then
        formatted shouldBe "12.5 V"
    }

    should("format battery current") {
        // given
        val formatter = createFormatter()

        // when
        val formatted = formatter.format(DataFieldId.BATTERY_CURRENT, 500.0)

        // then
        formatted shouldBe "500 mA"
    }

    should("format consumer current") {
        // given
        val formatter = createFormatter()

        // when
        val formatted = formatter.format(DataFieldId.CONSUMER_CURRENT, 300.0)

        // then
        formatted shouldBe "300 mA"
    }

    should("format temperature in metric when isImperialTemperature is false") {
        // given
        val formatter = createFormatter(isImperialTemperature = false)

        // when
        val formatted = formatter.format(DataFieldId.TEMPERATURE, 25.0)

        // then
        formatted shouldBe "25.0 °C"
    }

    should("format temperature in imperial when isImperialTemperature is true") {
        // given
        val formatter = createFormatter(isImperialTemperature = true)

        // when
        val formatted = formatter.format(DataFieldId.TEMPERATURE, 25.0)

        // then
        formatted shouldBe "77.0 °F"
    }

    should("format generator gear") {
        // given
        val formatter = createFormatter()

        // when
        val formatted = formatter.format(DataFieldId.GENERATOR_GEAR, 3.0)

        // then
        formatted shouldBe "3"
    }

    should("format charge state as standby when raw value is 0") {
        // given
        val formatter = createFormatter()

        // when
        val formatted = formatter.format(DataFieldId.CHARGE_STATE, 0.0)

        // then
        formatted shouldBe "Standby"
    }

    should("format charge state as charging when raw value is 1") {
        // given
        val formatter = createFormatter()

        // when
        val formatted = formatter.format(DataFieldId.CHARGE_STATE, 1.0)

        // then
        formatted shouldBe "Charging"
    }

    should("format charge state as discharging when raw value is 2") {
        // given
        val formatter = createFormatter()

        // when
        val formatted = formatter.format(DataFieldId.CHARGE_STATE, 2.0)

        // then
        formatted shouldBe "Discharging"
    }

    should("format charge state as full when raw value is 3") {
        // given
        val formatter = createFormatter()

        // when
        val formatted = formatter.format(DataFieldId.CHARGE_STATE, 3.0)

        // then
        formatted shouldBe "Full"
    }

    should("format unknown charge state as dashes") {
        // given
        val formatter = createFormatter()

        // when
        val formatted = formatter.format(DataFieldId.CHARGE_STATE, 99.0)

        // then
        formatted shouldBe "---"
    }

    should("format trip energy") {
        // given
        val formatter = createFormatter()

        // when
        val formatted = formatter.format(DataFieldId.TRIP_ENERGY, 10.5)

        // then
        formatted shouldBe "10.5 Wh"
    }

    should("format tour energy") {
        // given
        val formatter = createFormatter()

        // when
        val formatted = formatter.format(DataFieldId.TOUR_ENERGY, 10.5)

        // then
        formatted shouldBe "10.5 Wh"
    }

    should("format dynamo power") {
        // given
        val formatter = createFormatter()

        // when
        val formatted = formatter.format(DataFieldId.DYNAMO_POWER, 5.2)

        // then
        formatted shouldBe "5.2 W"
    }

    should("format odometer in metric") {
        // given
        val formatter = createFormatter(isImperialDistance = false)

        // when
        val formatted = formatter.format(DataFieldId.ODOMETER, 15500.0)

        // then
        formatted shouldBe "15.50 km"
    }

    should("format day distance in imperial") {
        // given
        val formatter = createFormatter(isImperialDistance = true)

        // when
        val formatted = formatter.format(DataFieldId.DAY_DISTANCE, 15500.0)

        // then
        formatted shouldBe "9.63 mi"
    }

    should("format tour distance in metric") {
        // given
        val formatter = createFormatter(isImperialDistance = false)

        // when
        val formatted = formatter.format(DataFieldId.TOUR_DISTANCE, 15500.0)

        // then
        formatted shouldBe "15.50 km"
    }

    should("format battery range in metric") {
        // given
        val formatter = createFormatter(isImperialDistance = false)

        // when
        val formatted = formatter.format(DataFieldId.BATTERY_RANGE, 45000.0)

        // then
        formatted shouldBe "45.00 km"
    }

    should("format battery range in imperial") {
        // given
        val formatter = createFormatter(isImperialDistance = true)

        // when
        val formatted = formatter.format(DataFieldId.BATTERY_RANGE, 45000.0)

        // then
        formatted shouldBe "27.96 mi"
    }

    should("format battery range as charging when charging sentinel is received") {
        // given
        val formatter = createFormatter()

        // when
        val chargingSentinel = formatter.format(DataFieldId.BATTERY_RANGE, DataFieldId.BATTERY_RANGE_CHARGING)
        val positiveInfinity = formatter.format(DataFieldId.BATTERY_RANGE, Double.POSITIVE_INFINITY)

        // then
        chargingSentinel shouldBe "Charging"
        positiveInfinity shouldBe "Charging"
    }

    should("format battery range as calculating when calculating sentinel is received") {
        // given
        val formatter = createFormatter()

        // when
        val formatted = formatter.format(DataFieldId.BATTERY_RANGE, DataFieldId.BATTERY_RANGE_CALCULATING)

        // then
        formatted shouldBe "Calculating…"
    }

    should("format battery range as not available when invalid negative value is received") {
        // given
        val formatter = createFormatter()

        // when
        val formatted = formatter.format(DataFieldId.BATTERY_RANGE, -99.0)

        // then
        formatted shouldBe "N/A"
    }

    should("format frequency") {
        // given
        val formatter = createFormatter()

        // when
        val formatted = formatter.format(DataFieldId.FREQUENCY, 50.5)

        // then
        formatted shouldBe "50.5 Hz"
    }

    should("format fallback unknown field") {
        // given
        val formatter = createFormatter()

        // when
        val formatted = formatter.format("UNKNOWN_FIELD", 42.0)

        // then
        formatted shouldBe "42.0"
    }

    should("construct from user profile") {
        // given
        val userProfile = UserProfile(
            weight = 70f,
            preferredUnit = UserProfile.PreferredUnit(
                distance = UserProfile.PreferredUnit.UnitType.IMPERIAL,
                elevation = UserProfile.PreferredUnit.UnitType.IMPERIAL,
                temperature = UserProfile.PreferredUnit.UnitType.IMPERIAL,
                weight = UserProfile.PreferredUnit.UnitType.IMPERIAL
            ),
            maxHr = 190,
            restingHr = 60,
            heartRateZones = emptyList(),
            ftp = 250,
            powerZones = emptyList()
        )

        // when
        val formatter = MetricFormatter.from(context, userProfile, Locale.US)

        // then
        formatter.format(DataFieldId.TEMPERATURE, 25.0) shouldBe "77.0 °F"
        formatter.format(DataFieldId.SPEED, 10.0) shouldBe "22.4 mph"
    }

    should("construct from null user profile") {
        // when
        val formatter = MetricFormatter.from(context, null, Locale.US)

        // then
        formatter.format(DataFieldId.TEMPERATURE, 25.0) shouldBe "25.0 °C"
        formatter.format(DataFieldId.SPEED, 10.0) shouldBe "36.0 km/h"
    }

    should("construct from metric user profile") {
        // given
        val userProfile = UserProfile(
            weight = 70f,
            preferredUnit = UserProfile.PreferredUnit(
                distance = UserProfile.PreferredUnit.UnitType.METRIC,
                elevation = UserProfile.PreferredUnit.UnitType.METRIC,
                temperature = UserProfile.PreferredUnit.UnitType.METRIC,
                weight = UserProfile.PreferredUnit.UnitType.METRIC
            ),
            maxHr = 190,
            restingHr = 60,
            heartRateZones = emptyList(),
            ftp = 250,
            powerZones = emptyList()
        )

        // when
        val formatter = MetricFormatter.from(context, userProfile, Locale.US)

        // then
        formatter.format(DataFieldId.TEMPERATURE, 25.0) shouldBe "25.0 °C"
        formatter.format(DataFieldId.SPEED, 10.0) shouldBe "36.0 km/h"
    }
})
