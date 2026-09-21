package org.happycode.karoo.forumslader.adapters

import android.content.Context
import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.ShouldSpec
import io.kotest.matchers.maps.shouldNotContainKey
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import org.happycode.karoo.forumslader.domain.ChargeState
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics

class ForumsladerDataFieldsAdapterTest : ShouldSpec({

    val mockContext = mockk<Context>()
    val adapter = ForumsladerDataFieldsAdapter(mockContext)

    fun createMetrics(
        batteryLevelPercentage: Int? = 0,
        batteryVoltage: Float = 0f,
        batteryCurrent: Float = 0f,
        consumerCurrent: Float = 0f,
        speedMetersPerSecond: Float = 0f,
        tripMeters: Double = 0.0,
        frequency: Float = 0f,
        temperatureCelsius: Float = 0f,
        generatorGear: Int = 0,
        chargeState: ChargeState = ChargeState.STANDBY,
        tripWattHours: Double = 0.0,
        tourWattHours: Double = 0.0,
        dynamoPowerWatts: Float = 0f,
        odometerMeters: Double = 0.0,
        dayMeters: Double = 0.0,
        tourMeters: Double = 0.0
    ) = ForumsladerMetrics(
        power = ForumsladerMetrics.Power(
            batteryVoltage = batteryVoltage,
            batteryCurrent = batteryCurrent,
            consumerCurrent = consumerCurrent,
            batteryLevelPercentage = batteryLevelPercentage,
            chargeState = chargeState,
            dynamoPowerWatts = dynamoPowerWatts,
            statusMask = 0
        ),
        dynamics = ForumsladerMetrics.Dynamics(
            frequency = frequency,
            speedMetersPerSecond = speedMetersPerSecond,
            generatorGear = generatorGear
        ),
        environment = ForumsladerMetrics.Environment(
            temperatureCelsius = temperatureCelsius,
            altitudeMeters = 0f
        ),
        energy = ForumsladerMetrics.Energy(
            tripWattHours = tripWattHours,
            tourWattHours = tourWattHours
        ),
        distance = ForumsladerMetrics.Distance(
            tripMeters = tripMeters,
            dayMeters = dayMeters,
            tourMeters = tourMeters,
            odometerMeters = odometerMeters
        )
    )

    should("convert metrics to data field values") {
        // given
        val metrics = createMetrics(
            batteryLevelPercentage = 75,
            batteryVoltage = 48.2f,
            batteryCurrent = 1.5f,
            consumerCurrent = 2.5f,
            speedMetersPerSecond = 7.03f,
            tripMeters = 12700.0,
            frequency = 17.7f,
            temperatureCelsius = 22.5f,
            generatorGear = 3,
            chargeState = ChargeState.CHARGING,
            tripWattHours = 12.5,
            tourWattHours = 55.0,
            dynamoPowerWatts = 3.0f,
            odometerMeters = 500000.0,
            dayMeters = 25000.0,
            tourMeters = 150000.0
        )

        // when
        val values = ForumsladerDataFieldsAdapter.metricsToDataFieldValues(metrics)

        // then
        assertSoftly {
            values.size shouldBe 16
            values[DataFieldId.BATTERY_LEVEL] shouldBe 75
            values[DataFieldId.BATTERY_VOLTAGE] shouldBe 48.2f
            values[DataFieldId.BATTERY_CURRENT] shouldBe 1500
            values[DataFieldId.CONSUMER_CURRENT] shouldBe 2500
            values[DataFieldId.SPEED] shouldBe 7.03f
            values[DataFieldId.TRIP_DISTANCE] shouldBe 12700.0
            values[DataFieldId.FREQUENCY] shouldBe 17.7f
            values[DataFieldId.TEMPERATURE] shouldBe 22.5f
            values[DataFieldId.GENERATOR_GEAR] shouldBe 3
            values[DataFieldId.CHARGE_STATE] shouldBe "CHARGING"
            values[DataFieldId.TRIP_ENERGY] shouldBe 12.5
            values[DataFieldId.TOUR_ENERGY] shouldBe 55.0
            values[DataFieldId.DYNAMO_POWER] shouldBe 3.0f
            values[DataFieldId.ODOMETER] shouldBe 500000.0
            values[DataFieldId.DAY_DISTANCE] shouldBe 25000.0
            values[DataFieldId.TOUR_DISTANCE] shouldBe 150000.0
        }
    }

    should("omit battery level when not provided in metrics") {
        // given
        val metrics = createMetrics(batteryLevelPercentage = null)

        // when
        val values = ForumsladerDataFieldsAdapter.metricsToDataFieldValues(metrics)

        // then
        values.shouldNotContainKey(DataFieldId.BATTERY_LEVEL)
        values.size shouldBe 15
    }

    ChargeState.entries.forEach { state ->
        should("map charge state ${state.name} to its name string") {
            // given
            val metrics = createMetrics(chargeState = state)

            // when
            val values = ForumsladerDataFieldsAdapter.metricsToDataFieldValues(metrics)

            // then
            values[DataFieldId.CHARGE_STATE] shouldBe state.name
        }
    }

    should("return localized data field names from string resources") {
        // given
        val idToRes = mapOf(
            DataFieldId.BATTERY_LEVEL to R.string.datafield_battery_level,
            DataFieldId.BATTERY_VOLTAGE to R.string.datafield_battery_voltage,
            DataFieldId.BATTERY_CURRENT to R.string.datafield_battery_current,
            DataFieldId.CONSUMER_CURRENT to R.string.datafield_consumer_current,
            DataFieldId.SPEED to R.string.datafield_speed,
            DataFieldId.TRIP_DISTANCE to R.string.datafield_trip_distance,
            DataFieldId.FREQUENCY to R.string.datafield_frequency,
            DataFieldId.TEMPERATURE to R.string.datafield_temperature,
            DataFieldId.GENERATOR_GEAR to R.string.datafield_generator_gear,
            DataFieldId.CHARGE_STATE to R.string.datafield_charge_state,
            DataFieldId.TRIP_ENERGY to R.string.datafield_trip_energy,
            DataFieldId.TOUR_ENERGY to R.string.datafield_tour_energy,
            DataFieldId.DYNAMO_POWER to R.string.datafield_dynamo_power,
            DataFieldId.ODOMETER to R.string.datafield_odometer,
            DataFieldId.DAY_DISTANCE to R.string.datafield_day_distance,
            DataFieldId.TOUR_DISTANCE to R.string.datafield_tour_distance,
            DataFieldId.BATTERY_RANGE to R.string.datafield_battery_range
        )

        idToRes.forEach { (id, resId) ->
            every { mockContext.getString(resId) } returns "Localized $id"
        }

        // when
        val names = adapter.getDataFieldNames()

        // then
        val expectedNames = idToRes.mapValues { (id, _) -> "Localized $id" }
        names shouldBe expectedNames
    }
})
