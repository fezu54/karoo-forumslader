package org.happycode.karoo.forumslader.ui.main

import android.content.Context
import io.hammerhead.karooext.models.UserProfile
import io.mockk.every
import io.mockk.mockk
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class MetricFormatterTest {
    private lateinit var context: Context

    @Before
    fun setup() {
        context = mockk()
        every { context.getString(R.string.charge_state_standby) } returns "Standby"
        every { context.getString(R.string.charge_state_charging) } returns "Charging"
        every { context.getString(R.string.charge_state_discharging) } returns "Discharging"
        every { context.getString(R.string.charge_state_full) } returns "Full"
    }

    @Test
    fun `should format missing value as dashes`() {
        val formatter = MetricFormatter(Locale.US,
            isImperialDistance = false,
            isImperialTemperature = false,
            context = context
        )
        assertEquals("---", formatter.format(DataFieldId.SPEED, null))
    }

    @Test
    fun `should format speed in metric when isImperialDistance is false`() {
        val formatter = MetricFormatter(Locale.US,
            isImperialDistance = false,
            isImperialTemperature = false,
            context = context
        )
        assertEquals("36.0 km/h", formatter.format(DataFieldId.SPEED, 10.0))
    }

    @Test
    fun `should format speed in imperial when isImperialDistance is true`() {
        val formatter = MetricFormatter(Locale.US,
            isImperialDistance = true,
            isImperialTemperature = false,
            context = context
        )
        assertEquals("22.4 mph", formatter.format(DataFieldId.SPEED, 10.0))
    }

    @Test
    fun `should format trip distance in metric`() {
        val formatter = MetricFormatter(Locale.US,
            isImperialDistance = false,
            isImperialTemperature = false,
            context = context
        )
        assertEquals("1.50 km", formatter.format(DataFieldId.TRIP_DISTANCE, 1500.0))
    }

    @Test
    fun `should format trip distance in imperial`() {
        val formatter = MetricFormatter(Locale.US,
            isImperialDistance = true,
            isImperialTemperature = false,
            context = context
        )
        assertEquals("0.93 mi", formatter.format(DataFieldId.TRIP_DISTANCE, 1500.0))
    }

    @Test
    fun `should format battery level`() {
        val formatter = MetricFormatter(Locale.US, isImperialDistance = false, isImperialTemperature = false, context = context)
        assertEquals("85%", formatter.format(DataFieldId.BATTERY_LEVEL, 85.0))
    }

    @Test
    fun `should format battery voltage`() {
        val formatter = MetricFormatter(Locale.US,
            isImperialDistance = false,
            isImperialTemperature = false,
            context = context
        )
        assertEquals("12.5 V", formatter.format(DataFieldId.BATTERY_VOLTAGE, 12.5))
    }

    @Test
    fun `should format battery current`() {
        val formatter = MetricFormatter(Locale.US,
            isImperialDistance = false,
            isImperialTemperature = false,
            context = context
        )
        assertEquals("500 mA", formatter.format(DataFieldId.BATTERY_CURRENT, 500.0))
    }

    @Test
    fun `should format consumer current`() {
        val formatter = MetricFormatter(Locale.US,
            isImperialDistance = false,
            isImperialTemperature = false,
            context = context
        )
        assertEquals("300 mA", formatter.format(DataFieldId.CONSUMER_CURRENT, 300.0))
    }

    @Test
    fun `should format temperature in metric when isImperialTemperature is false`() {
        val formatter = MetricFormatter(Locale.US,
            isImperialDistance = false,
            isImperialTemperature = false,
            context = context
        )
        assertEquals("25.0 °C", formatter.format(DataFieldId.TEMPERATURE, 25.0))
    }

    @Test
    fun `should format temperature in imperial when isImperialTemperature is true`() {
        val formatter = MetricFormatter(Locale.US,
            isImperialDistance = false,
            isImperialTemperature = true,
            context = context
        )
        assertEquals("77.0 °F", formatter.format(DataFieldId.TEMPERATURE, 25.0))
    }

    @Test
    fun `should format generator gear`() {
        val formatter = MetricFormatter(Locale.US,
            isImperialDistance = false,
            isImperialTemperature = false,
            context = context
        )
        assertEquals("3", formatter.format(DataFieldId.GENERATOR_GEAR, 3.0))
    }

    @Test
    fun `should format charge state standby`() {
        val formatter = MetricFormatter(Locale.US,
            isImperialDistance = false,
            isImperialTemperature = false,
            context = context
        )
        assertEquals("Standby", formatter.format(DataFieldId.CHARGE_STATE, 0.0))
    }

    @Test
    fun `should format charge state charging`() {
        val formatter = MetricFormatter(Locale.US,
            isImperialDistance = false,
            isImperialTemperature = false,
            context = context
        )
        assertEquals("Charging", formatter.format(DataFieldId.CHARGE_STATE, 1.0))
    }

    @Test
    fun `should format charge state discharging`() {
        val formatter = MetricFormatter(Locale.US,
            isImperialDistance = false,
            isImperialTemperature = false,
            context = context
        )
        assertEquals("Discharging", formatter.format(DataFieldId.CHARGE_STATE, 2.0))
    }

    @Test
    fun `should format charge state full`() {
        val formatter = MetricFormatter(Locale.US,
            isImperialDistance = false,
            isImperialTemperature = false,
            context = context
        )
        assertEquals("Full", formatter.format(DataFieldId.CHARGE_STATE, 3.0))
    }

    @Test
    fun `should format unknown charge state`() {
        val formatter = MetricFormatter(Locale.US,
            isImperialDistance = false,
            isImperialTemperature = false,
            context = context
        )
        assertEquals("---", formatter.format(DataFieldId.CHARGE_STATE, 99.0))
    }

    @Test
    fun `should format trip energy`() {
        val formatter = MetricFormatter(Locale.US,
            isImperialDistance = false,
            isImperialTemperature = false,
            context = context
        )
        assertEquals("10.5 Wh", formatter.format(DataFieldId.TRIP_ENERGY, 10.5))
    }
    
    @Test
    fun `should format tour energy`() {
        val formatter = MetricFormatter(Locale.US,
            isImperialDistance = false,
            isImperialTemperature = false,
            context = context
        )
        assertEquals("10.5 Wh", formatter.format(DataFieldId.TOUR_ENERGY, 10.5))
    }

    @Test
    fun `should format dynamo power`() {
        val formatter = MetricFormatter(Locale.US,
            isImperialDistance = false,
            isImperialTemperature = false,
            context = context
        )
        assertEquals("5.2 W", formatter.format(DataFieldId.DYNAMO_POWER, 5.2))
    }

    @Test
    fun `should format odometer in metric`() {
        val formatter = MetricFormatter(Locale.US,
            isImperialDistance = false,
            isImperialTemperature = false,
            context = context
        )
        assertEquals("15.50 km", formatter.format(DataFieldId.ODOMETER, 15500.0))
    }
    
    @Test
    fun `should format day distance in imperial`() {
        val formatter = MetricFormatter(Locale.US,
            isImperialDistance = true,
            isImperialTemperature = false,
            context = context
        )
        assertEquals("9.63 mi", formatter.format(DataFieldId.DAY_DISTANCE, 15500.0))
    }

    @Test
    fun `should format tour distance in metric`() {
        val formatter = MetricFormatter(Locale.US,
            isImperialDistance = false,
            isImperialTemperature = false,
            context = context
        )
        assertEquals("15.50 km", formatter.format(DataFieldId.TOUR_DISTANCE, 15500.0))
    }

    @Test
    fun `should format frequency`() {
        val formatter = MetricFormatter(Locale.US,
            isImperialDistance = false,
            isImperialTemperature = false,
            context = context
        )
        assertEquals("50.5 Hz", formatter.format(DataFieldId.FREQUENCY, 50.5))
    }

    @Test
    fun `should format fallback unknown field`() {
        val formatter = MetricFormatter(Locale.US,
            isImperialDistance = false,
            isImperialTemperature = false,
            context = context
        )
        assertEquals("42.0", formatter.format("UNKNOWN_FIELD", 42.0))
    }
    
    @Test
    fun `should construct from user profile`() {
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
        
        val formatter = MetricFormatter.from(context, userProfile, Locale.US)
        assertEquals("77.0 °F", formatter.format(DataFieldId.TEMPERATURE, 25.0))
        assertEquals("22.4 mph", formatter.format(DataFieldId.SPEED, 10.0))
    }
    
    @Test
    fun `should construct from null user profile`() {
        val formatter = MetricFormatter.from(context, null, Locale.US)
        assertEquals("25.0 °C", formatter.format(DataFieldId.TEMPERATURE, 25.0))
        assertEquals("36.0 km/h", formatter.format(DataFieldId.SPEED, 10.0))
    }

    @Test
    fun `should construct from metric user profile`() {
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
        
        val formatter = MetricFormatter.from(context, userProfile, Locale.US)
        assertEquals("25.0 °C", formatter.format(DataFieldId.TEMPERATURE, 25.0))
        assertEquals("36.0 km/h", formatter.format(DataFieldId.SPEED, 10.0))
    }
}
