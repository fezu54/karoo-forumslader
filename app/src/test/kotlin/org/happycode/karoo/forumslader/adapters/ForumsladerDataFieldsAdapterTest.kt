package org.happycode.karoo.forumslader.adapters

import android.content.Context
import io.mockk.mockk
import io.mockk.every
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics
import org.junit.Test
import kotlin.test.assertEquals

class ForumsladerDataFieldsAdapterTest {
    private val mockContext = mockk<Context>()

    @Test
    fun `should convert metrics to datafield values`() {
        // given
        val metrics = ForumsladerMetrics(
            batteryVoltage = 48.2f,
            batteryCurrent = 1.5f,
            consumerCurrent = 2.5f,
            batteryLevelPct = 75,
            speedKmh = 25.3f,
            tripDistanceKm = 12.7f,
            totalDistanceKm = 100.5f,
            temperatureCelsius = 22.5f,
            altitudeMeters = 150f
        )

        // when
        val values = ForumsladerDataFieldsAdapter.metricsToDataFieldValues(metrics)

        // then
        assertEquals(75, values[ForumsladerDataFieldsAdapter.DataFieldId.BATTERY_LEVEL])
        assertEquals(2.5f, values[ForumsladerDataFieldsAdapter.DataFieldId.CONSUMER_CURRENT])
        assertEquals(25.3f, values[ForumsladerDataFieldsAdapter.DataFieldId.SPEED])
        assertEquals(12.7f, values[ForumsladerDataFieldsAdapter.DataFieldId.TRIP_DISTANCE])
    }

    @Test
    fun `should map all metrics fields`() {
        // given
        val metrics = ForumsladerMetrics(
            batteryVoltage = 48.2f,
            batteryCurrent = 1.5f,
            consumerCurrent = 2.5f,
            batteryLevelPct = 75,
            speedKmh = 25.3f,
            tripDistanceKm = 12.7f,
            totalDistanceKm = 100.5f,
            temperatureCelsius = 22.5f,
            altitudeMeters = 150f
        )

        // when
        val values = ForumsladerDataFieldsAdapter.metricsToDataFieldValues(metrics)

        // then
        assertEquals(4, values.size)
    }

    @Test
    fun `should return localized datafield names from string resources`() {
        // given
        every { mockContext.getString(R.string.datafield_battery_level) } returns "Battery Level"
        every { mockContext.getString(R.string.datafield_consumer_current) } returns "Consumer Current"
        every { mockContext.getString(R.string.datafield_speed) } returns "Speed"
        every { mockContext.getString(R.string.datafield_trip_distance) } returns "Trip Distance"
        
        val adapter = ForumsladerDataFieldsAdapter(mockContext)

        // when
        val names = adapter.getDataFieldNames()

        // then
        assertEquals("Battery Level", names[ForumsladerDataFieldsAdapter.DataFieldId.BATTERY_LEVEL])
        assertEquals("Consumer Current", names[ForumsladerDataFieldsAdapter.DataFieldId.CONSUMER_CURRENT])
        assertEquals("Speed", names[ForumsladerDataFieldsAdapter.DataFieldId.SPEED])
        assertEquals("Trip Distance", names[ForumsladerDataFieldsAdapter.DataFieldId.TRIP_DISTANCE])
    }
}
