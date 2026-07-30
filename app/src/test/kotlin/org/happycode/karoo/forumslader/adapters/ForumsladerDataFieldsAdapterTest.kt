package org.happycode.karoo.forumslader.adapters

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import org.happycode.karoo.forumslader.domain.ChargeState.CHARGING
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


class ForumsladerDataFieldsAdapterTest {
    private val mockContext = mockk<Context>()

    @Test
    fun `should convert metrics to data field values`() {
        // given
        val metrics = ForumsladerMetrics(
            power = ForumsladerMetrics.Power(
                batteryVoltage = 48.2f,
                batteryCurrent = 1.5f,
                consumerCurrent = 2.5f,
                batteryLevelPct = 75,
                chargeState = CHARGING,
                dynamoPowerW = 3.0f
            ),
            dynamics = ForumsladerMetrics.Dynamics(
                frequency = 17.7f,
                speedMs = 7.03f, // 25.3 km/h / 3.6
                generatorGear = 3
            ),
            environment = ForumsladerMetrics.Environment(
                temperatureCelsius = 22.5f,
                altitudeMeters = 150f
            ),
            energy = ForumsladerMetrics.Energy(
                tripWh = 12.5,
                tourWh = 55.0
            ),
            distance = ForumsladerMetrics.Distance(
                tripMeters = 12700.0,
                dayMeters = 25000.0,
                tourMeters = 150000.0,
                odometerMeters = 500000.0
            )
        )

        // when
        val values = ForumsladerDataFieldsAdapter.metricsToDataFieldValues(metrics)

        // then
        assertEquals(75, values[DataFieldId.BATTERY_LEVEL])
        assertEquals(48.2f, values[DataFieldId.BATTERY_VOLTAGE])
        assertEquals(1500, values[DataFieldId.BATTERY_CURRENT])
        assertEquals(2500, values[DataFieldId.CONSUMER_CURRENT])
        assertEquals(7.03f, values[DataFieldId.SPEED])
        assertEquals(12700.0, values[DataFieldId.TRIP_DISTANCE])
        assertEquals(17.7f, values[DataFieldId.FREQUENCY])
        assertEquals(22.5f, values[DataFieldId.TEMPERATURE])
        assertEquals(3, values[DataFieldId.GENERATOR_GEAR])
        assertEquals("CHARGING", values[DataFieldId.CHARGE_STATE])
        assertEquals(12.5, values[DataFieldId.TRIP_ENERGY])
        assertEquals(55.0, values[DataFieldId.TOUR_ENERGY])
        assertEquals(3.0f, values[DataFieldId.DYNAMO_POWER])
        assertEquals(500000.0, values[DataFieldId.ODOMETER])
        assertEquals(25000.0, values[DataFieldId.DAY_DISTANCE])
        assertEquals(150000.0, values[DataFieldId.TOUR_DISTANCE])
    }

    @Test
    fun `should map all metrics fields`() {
        // given
        val metrics = ForumsladerMetrics(
            power = ForumsladerMetrics.Power(
                batteryVoltage = 48.2f,
                batteryCurrent = 1.5f,
                consumerCurrent = 2.5f,
                batteryLevelPct = 75,
                chargeState = CHARGING,
                dynamoPowerW = 3.0f
            ),
            dynamics = ForumsladerMetrics.Dynamics(
                frequency = 17.7f,
                speedMs = 7.03f,
                generatorGear = 3
            ),
            environment = ForumsladerMetrics.Environment(
                temperatureCelsius = 22.5f,
                altitudeMeters = 150f
            ),
            energy = ForumsladerMetrics.Energy(
                tripWh = 12.5,
                tourWh = 55.0
            ),
            distance = ForumsladerMetrics.Distance(
                tripMeters = 12700.0,
                dayMeters = 25000.0,
                tourMeters = 150000.0,
                odometerMeters = 500000.0
            )
        )

        // when
        val values = ForumsladerDataFieldsAdapter.metricsToDataFieldValues(metrics)

        // then
        assertEquals(16, values.size)
    }

    @Test
    fun `should return localized data field names from string resources`() {
        // given
        every { mockContext.getString(R.string.datafield_battery_level) } returns "Battery Level"
        every { mockContext.getString(R.string.datafield_battery_voltage) } returns "Battery Voltage"
        every { mockContext.getString(R.string.datafield_battery_current) } returns "Battery Current (mA)"
        every { mockContext.getString(R.string.datafield_consumer_current) } returns "Consumer Current (mA)"
        every { mockContext.getString(R.string.datafield_speed) } returns "Speed"
        every { mockContext.getString(R.string.datafield_trip_distance) } returns "Trip Distance"
        every { mockContext.getString(R.string.datafield_frequency) } returns "Frequency"
        every { mockContext.getString(R.string.datafield_temperature) } returns "Temperature"
        every { mockContext.getString(R.string.datafield_generator_gear) } returns "Generator Gear"
        every { mockContext.getString(R.string.datafield_charge_state) } returns "Charge State"
        every { mockContext.getString(R.string.datafield_trip_energy) } returns "Trip Energy"
        every { mockContext.getString(R.string.datafield_tour_energy) } returns "Tour Energy"
        every { mockContext.getString(R.string.datafield_dynamo_power) } returns "Dynamo Power"
        every { mockContext.getString(R.string.datafield_odometer) } returns "Odometer"
        every { mockContext.getString(R.string.datafield_day_distance) } returns "Day Distance"
        every { mockContext.getString(R.string.datafield_tour_distance) } returns "Tour Distance"

        val adapter = ForumsladerDataFieldsAdapter(mockContext)

        // when
        val names = adapter.getDataFieldNames()

        // then
        assertEquals("Battery Level", names[DataFieldId.BATTERY_LEVEL])
        assertEquals("Battery Voltage", names[DataFieldId.BATTERY_VOLTAGE])
        assertEquals("Battery Current (mA)", names[DataFieldId.BATTERY_CURRENT])
        assertEquals("Consumer Current (mA)", names[DataFieldId.CONSUMER_CURRENT])
        assertEquals("Speed", names[DataFieldId.SPEED])
        assertEquals("Trip Distance", names[DataFieldId.TRIP_DISTANCE])
        assertEquals("Frequency", names[DataFieldId.FREQUENCY])
        assertEquals("Temperature", names[DataFieldId.TEMPERATURE])
        assertEquals("Generator Gear", names[DataFieldId.GENERATOR_GEAR])
        assertEquals("Charge State", names[DataFieldId.CHARGE_STATE])
        assertEquals("Trip Energy", names[DataFieldId.TRIP_ENERGY])
        assertEquals("Tour Energy", names[DataFieldId.TOUR_ENERGY])
        assertEquals("Dynamo Power", names[DataFieldId.DYNAMO_POWER])
        assertEquals("Odometer", names[DataFieldId.ODOMETER])
        assertEquals("Day Distance", names[DataFieldId.DAY_DISTANCE])
        assertEquals("Tour Distance", names[DataFieldId.TOUR_DISTANCE])
    }
}
