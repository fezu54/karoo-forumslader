package org.happycode.karoo.forumslader.adapters

import android.content.Context
import io.hammerhead.karooext.models.DataField
import io.hammerhead.karooext.models.SensorDataTypeDef
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ForumsladerDataFieldsAdapterTest {
    private val mockContext = org.mockito.Mockito.mock(Context::class.java)

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
        assertEquals(75, values["forumslader_battery_level"])
        assertEquals(2.5f, values["forumslader_consumer_current"])
        assertEquals(25.3f, values["forumslader_speed"])
        assertEquals(12.7f, values["forumslader_trip_distance"])
    }

    @Test
    fun `should create four datafields`() {
        // when
        val dataFields = ForumsladerDataFieldsAdapter(mockContext).createDataFields()

        // then
        assertEquals(4, dataFields.size)
        assertEquals("forumslader_battery_level", dataFields[0].id)
        assertEquals("forumslader_consumer_current", dataFields[1].id)
        assertEquals("forumslader_speed", dataFields[2].id)
        assertEquals("forumslader_trip_distance", dataFields[3].id)
    }

    @Test
    fun `should have non-null sensorDataType definitions`() {
        // when
        val dataFields = ForumsladerDataFieldsAdapter(mockContext).createDataFields()

        // then
        dataFields.forEach { dataField ->
            assertNotNull(dataField.sensorDataTypeDef)
        }
    }
}
