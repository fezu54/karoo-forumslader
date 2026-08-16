package org.happycode.karoo.forumslader.adapters

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import org.happycode.karoo.forumslader.domain.ChargeState
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.function.Executable
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class ForumsladerDataFieldsAdapterTest {
    private val mockContext = mockk<Context>()
    private val adapter = ForumsladerDataFieldsAdapter(mockContext)

    @Test
    fun `should convert metrics to data field values`() {
        // given
        val metrics = createMetrics(
            batteryLevel = 75,
            batteryVoltage = 48.2f,
            batteryCurrent = 1.5f,
            consumerCurrent = 2.5f,
            speed = 7.03f,
            tripDistance = 12700.0,
            frequency = 17.7f,
            temperature = 22.5f,
            generatorGear = 3,
            chargeState = ChargeState.CHARGING,
            tripEnergy = 12.5,
            tourEnergy = 55.0,
            dynamoPower = 3.0f,
            odometer = 500000.0,
            dayDistance = 25000.0,
            tourDistance = 150000.0
        )

        // when
        val values = ForumsladerDataFieldsAdapter.metricsToDataFieldValues(metrics)

        // then
        assertAll(
            { assertEquals(75, values[DataFieldId.BATTERY_LEVEL]) },
            { assertEquals(48.2f, values[DataFieldId.BATTERY_VOLTAGE]) },
            { assertEquals(1500, values[DataFieldId.BATTERY_CURRENT]) },
            { assertEquals(2500, values[DataFieldId.CONSUMER_CURRENT]) },
            { assertEquals(7.03f, values[DataFieldId.SPEED]) },
            { assertEquals(12700.0, values[DataFieldId.TRIP_DISTANCE]) },
            { assertEquals(17.7f, values[DataFieldId.FREQUENCY]) },
            { assertEquals(22.5f, values[DataFieldId.TEMPERATURE]) },
            { assertEquals(3, values[DataFieldId.GENERATOR_GEAR]) },
            { assertEquals("CHARGING", values[DataFieldId.CHARGE_STATE]) },
            { assertEquals(12.5, values[DataFieldId.TRIP_ENERGY]) },
            { assertEquals(55.0, values[DataFieldId.TOUR_ENERGY]) },
            { assertEquals(3.0f, values[DataFieldId.DYNAMO_POWER]) },
            { assertEquals(500000.0, values[DataFieldId.ODOMETER]) },
            { assertEquals(25000.0, values[DataFieldId.DAY_DISTANCE]) },
            { assertEquals(150000.0, values[DataFieldId.TOUR_DISTANCE]) }
        )
    }

    @Test
    fun `should omit battery level when not provided in metrics`() {
        // given
        val metrics = createMetrics(batteryLevel = null)

        // when
        val values = ForumsladerDataFieldsAdapter.metricsToDataFieldValues(metrics)

        // then
        assertFalse(values.containsKey(DataFieldId.BATTERY_LEVEL))
        assertEquals(15, values.size)
    }

    @ParameterizedTest
    @EnumSource(ChargeState::class)
    fun `should map charge state to its name string`(state: ChargeState) {
        // given
        val metrics = createMetrics(chargeState = state)

        // when
        val values = ForumsladerDataFieldsAdapter.metricsToDataFieldValues(metrics)

        // then
        assertEquals(state.name, values[DataFieldId.CHARGE_STATE])
    }

    @Test
    fun `should return localized data field names from string resources`() {
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
        assertAll(
            idToRes.keys.map { id ->
                Executable { assertEquals("Localized $id", names[id], "Missing or wrong name for $id") }
            }
        )
    }

    private fun createMetrics(
        batteryLevel: Int? = 0,
        batteryVoltage: Float = 0f,
        batteryCurrent: Float = 0f,
        consumerCurrent: Float = 0f,
        speed: Float = 0f,
        tripDistance: Double = 0.0,
        frequency: Float = 0f,
        temperature: Float = 0f,
        generatorGear: Int = 0,
        chargeState: ChargeState = ChargeState.STANDBY,
        tripEnergy: Double = 0.0,
        tourEnergy: Double = 0.0,
        dynamoPower: Float = 0f,
        odometer: Double = 0.0,
        dayDistance: Double = 0.0,
        tourDistance: Double = 0.0
    ) = ForumsladerMetrics(
        power = ForumsladerMetrics.Power(
            batteryVoltage = batteryVoltage,
            batteryCurrent = batteryCurrent,
            consumerCurrent = consumerCurrent,
            batteryLevelPercentage = batteryLevel,
            chargeState = chargeState,
            dynamoPowerWatts = dynamoPower,
            statusMask = 0
        ),
        dynamics = ForumsladerMetrics.Dynamics(
            frequency = frequency,
            speedMetersPerSecond = speed,
            generatorGear = generatorGear
        ),
        environment = ForumsladerMetrics.Environment(
            temperatureCelsius = temperature,
            altitudeMeters = 0f
        ),
        energy = ForumsladerMetrics.Energy(
            tripWattHours = tripEnergy,
            tourWattHours = tourEnergy
        ),
        distance = ForumsladerMetrics.Distance(
            tripMeters = tripDistance,
            dayMeters = dayDistance,
            tourMeters = tourDistance,
            odometerMeters = odometer
        )
    )
}
