package org.happycode.karoo.forumslader.adapters

import org.happycode.karoo.forumslader.domain.ForumsladerMetrics
import org.junit.Test
import kotlin.test.assertEquals

class ForumsladerDataFieldsAdapterTest {
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
}
