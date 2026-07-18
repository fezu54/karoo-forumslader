package org.happycode.karoo.forumslader.adapters

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test


class ForumsladerDataFieldsAdapterTest {
    private val mockContext = mockk<Context>()

    @Test
    fun `should convert metrics to data field values`() {
        // given
        val metrics = ForumsladerMetrics(
            batteryVoltage = 48.2f,
            batteryCurrent = 1.5f,
            consumerCurrent = 2.5f,
            batteryLevelPct = 75,
            frequency = 17.7f,
            speedMs = 7.03f, // 25.3 km/h / 3.6
            tripDistanceMeters = 12700.0,
            totalDistanceMeters = 100500.0,
            temperatureCelsius = 22.5f,
            altitudeMeters = 150f
        )

        // when
        val values = ForumsladerDataFieldsAdapter.metricsToDataFieldValues(metrics)

        // then
        assertEquals(75, values[ForumsladerDataFieldsAdapter.DataFieldId.BATTERY_LEVEL])
        assertEquals(2.5f, values[ForumsladerDataFieldsAdapter.DataFieldId.CONSUMER_CURRENT])
        assertEquals(7.03f, values[ForumsladerDataFieldsAdapter.DataFieldId.SPEED])
        assertEquals(12700.0, values[ForumsladerDataFieldsAdapter.DataFieldId.TRIP_DISTANCE])
        assertEquals(17.7f, values[ForumsladerDataFieldsAdapter.DataFieldId.FREQUENCY])
    }

    @Test
    fun `should map all metrics fields`() {
        // given
        val metrics = ForumsladerMetrics(
            batteryVoltage = 48.2f,
            batteryCurrent = 1.5f,
            consumerCurrent = 2.5f,
            batteryLevelPct = 75,
            frequency = 17.7f,
            speedMs = 7.03f,
            tripDistanceMeters = 12700.0,
            totalDistanceMeters = 100500.0,
            temperatureCelsius = 22.5f,
            altitudeMeters = 150f
        )

        // when
        val values = ForumsladerDataFieldsAdapter.metricsToDataFieldValues(metrics)

        // then
        assertEquals(5, values.size)
    }

    @Test
    fun `should return localized data field names from string resources`() {
        // given
        every { mockContext.getString(R.string.datafield_battery_level) } returns "Battery Level"
        every { mockContext.getString(R.string.datafield_consumer_current) } returns "Consumer Current"
        every { mockContext.getString(R.string.datafield_speed) } returns "Speed"
        every { mockContext.getString(R.string.datafield_trip_distance) } returns "Trip Distance"
        every { mockContext.getString(R.string.datafield_frequency) } returns "Frequency"

        val adapter = ForumsladerDataFieldsAdapter(mockContext)

        // when
        val names = adapter.getDataFieldNames()

        // then
        assertEquals("Battery Level", names[ForumsladerDataFieldsAdapter.DataFieldId.BATTERY_LEVEL])
        assertEquals("Consumer Current", names[ForumsladerDataFieldsAdapter.DataFieldId.CONSUMER_CURRENT])
        assertEquals("Speed", names[ForumsladerDataFieldsAdapter.DataFieldId.SPEED])
        assertEquals("Trip Distance", names[ForumsladerDataFieldsAdapter.DataFieldId.TRIP_DISTANCE])
        assertEquals("Frequency", names[ForumsladerDataFieldsAdapter.DataFieldId.FREQUENCY])
    }
}
