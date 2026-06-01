package org.happycode.karoo.forumslader.adapters

import org.happycode.karoo.forumslader.domain.ForumsladerMetrics
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class ForumsladerDataFieldsAdapterTest {
    @Test
    fun should_convert_metrics_to_datafield_values() {
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
    fun should_create_four_datafields() {
        // when
        val dataFields = ForumsladerDataFieldsAdapter.createDataFields()

        // then
        assertEquals(4, dataFields.size)
        assertEquals("forumslader_battery_level", dataFields[0].id)
        assertEquals("forumslader_consumer_current", dataFields[1].id)
        assertEquals("forumslader_speed", dataFields[2].id)
        assertEquals("forumslader_trip_distance", dataFields[3].id)
    }

    @Test
    fun should_have_correct_datafield_names() {
        // when
        val dataFields = ForumsladerDataFieldsAdapter.createDataFields()

        // then
        assertEquals("Battery Level", dataFields[0].name)
        assertEquals("Consumer Current", dataFields[1].name)
        assertEquals("Speed", dataFields[2].name)
        assertEquals("Trip Distance", dataFields[3].name)
    }

    @Test
    fun should_have_non_null_sensorDataTypeDefinitions() {
        // when
        val dataFields = ForumsladerDataFieldsAdapter.createDataFields()

        // then
        dataFields.forEach { dataField ->
            assertNotNull(dataField.sensorDataTypeDef)
        }
    }
}
