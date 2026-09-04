package org.happycode.karoo.forumslader.application

import org.happycode.karoo.forumslader.domain.ChargeState
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.io.path.createTempDirectory
import kotlin.io.path.exists
import kotlin.io.path.readLines
import kotlin.io.path.readText

class CsvLoggerTest {

    private fun sampleMetrics(
        voltage: Float = 7.2f,
        batteryCurrent: Float = 0.35f,
        consumerCurrent: Float = 0.12f,
        batteryLevel: Int? = 85,
        frequency: Float = 120.5f,
        speedMps: Float = 7.02778f, // ~25.3 km/h
        tripMeters: Double = 12345.6,
        tempCelsius: Float = 22.5f,
        gear: Int = 3,
        chargeState: ChargeState = ChargeState.CHARGING,
        tripWh: Double = 45.2,
        dynamoWatts: Float = 3.8f
    ): ForumsladerMetrics = ForumsladerMetrics(
        power = ForumsladerMetrics.Power(
            batteryVoltage = voltage,
            batteryCurrent = batteryCurrent,
            consumerCurrent = consumerCurrent,
            batteryLevelPercentage = batteryLevel,
            chargeState = chargeState,
            dynamoPowerWatts = dynamoWatts,
            statusMask = 0
        ),
        dynamics = ForumsladerMetrics.Dynamics(
            frequency = frequency,
            speedMetersPerSecond = speedMps,
            generatorGear = gear
        ),
        environment = ForumsladerMetrics.Environment(
            temperatureCelsius = tempCelsius,
            altitudeMeters = 100f
        ),
        energy = ForumsladerMetrics.Energy(
            tripWattHours = tripWh,
            tourWattHours = 90.0
        ),
        distance = ForumsladerMetrics.Distance(
            tripMeters = tripMeters,
            dayMeters = 12345.6,
            tourMeters = 20000.0,
            odometerMeters = 50000.0
        )
    )

    @Test
    fun `should write header and row when first telemetry logged`() {
        //given
        val tempDir = createTempDirectory()
        val logger = CsvLogger(directory = tempDir)
        val timestamp = Instant.parse("2024-01-15T10:30:00Z")
        val metrics = sampleMetrics()

        //when
        logger.logTelemetry(metrics, timestamp)

        //then
        val csvPath = logger.getCsvPath()
        assertNotNull(csvPath)
        val lines = csvPath!!.readLines()
        assertEquals(2, lines.size)
        assertEquals(CsvLogger.CSV_HEADER, lines[0])
        assertEquals("0.000,7.20,0.350,0.120,85,120.5,25.3,12345.6,22.5,3,CHARGING,45.2,3.8", lines[1])
        assertEquals(1, logger.getRowCount())
        assertTrue(logger.getFileSize() > 0)
    }

    @Test
    fun `should calculate relative elapsed seconds on subsequent telemetry records`() {
        //given
        val tempDir = createTempDirectory()
        val logger = CsvLogger(directory = tempDir)
        val startTime = Instant.parse("2024-01-15T10:30:00Z")
        val secondTime = startTime.plusMillis(3250)
        val metrics = sampleMetrics()

        //when
        logger.logTelemetry(metrics, startTime)
        logger.logTelemetry(metrics, secondTime)

        //then
        val lines = logger.getCsvPath()!!.readLines()
        assertEquals("0.000", lines[1].split(",")[0])
        assertEquals("3.250", lines[2].split(",")[0])
    }

    @Test
    fun `should reset session start time when clear invoked`() {
        //given
        val tempDir = createTempDirectory()
        val logger = CsvLogger(directory = tempDir)
        val time1 = Instant.parse("2024-01-15T10:30:00Z")
        val time2 = time1.plusMillis(5000)
        val time3 = time1.plusMillis(10000)
        val metrics = sampleMetrics()

        logger.logTelemetry(metrics, time1)
        logger.logTelemetry(metrics, time2)
        assertEquals("5.000", logger.getCsvPath()!!.readLines()[2].split(",")[0])

        //when
        logger.clear()
        logger.logTelemetry(metrics, time3)

        //then
        val lines = logger.getCsvPath()!!.readLines()
        assertEquals("0.000", lines[1].split(",")[0])
    }

    @Test
    fun `should append rows without duplicating header on subsequent calls`() {
        //given
        val tempDir = createTempDirectory()
        val logger = CsvLogger(directory = tempDir)
        val metrics = sampleMetrics()

        //when
        logger.logTelemetry(metrics)
        logger.logTelemetry(metrics)

        //then
        assertEquals(2, logger.getRowCount())
        val csvContent = logger.getCsvPath()!!.readText()
        assertEquals(1, csvContent.split(CsvLogger.CSV_HEADER).size - 1)
    }

    @Test
    fun `should rotate file when maximum file size exceeded`() {
        //given
        val tempDir = createTempDirectory()
        val smallLimitBytes = 180L
        val logger = CsvLogger(directory = tempDir, maxFileSizeBytes = smallLimitBytes)
        val metrics = sampleMetrics()

        //when
        logger.logTelemetry(metrics) // writes header + row (~150 bytes)
        logger.logTelemetry(metrics) // triggers rotation before second row write

        //then
        val backupPath = tempDir.resolve("telemetry_backup.csv")
        assertTrue(backupPath.exists())
        assertTrue(logger.getCsvPath()!!.exists())
        assertEquals(1, logger.getRowCount())
    }

    @Test
    fun `should clear all telemetry files when clear called`() {
        //given
        val tempDir = createTempDirectory()
        val logger = CsvLogger(directory = tempDir)
        logger.logTelemetry(sampleMetrics())

        //when
        logger.clear()

        //then
        assertEquals(0, logger.getRowCount())
    }

    @Test
    fun `should handle null battery level percentage gracefully`() {
        //given
        val tempDir = createTempDirectory()
        val logger = CsvLogger(directory = tempDir)
        val metrics = sampleMetrics(batteryLevel = null)

        //when
        logger.logTelemetry(metrics)

        //then
        val row = logger.getCsvPath()!!.readLines()[1]
        val fields = row.split(",")
        assertEquals("", fields[4])
    }

    @Test
    fun `should return zero rows and size when active file contains only header`() {
        //given
        val tempDir = createTempDirectory()
        val logger = CsvLogger(directory = tempDir)

        //then (no telemetry logged)
        assertEquals(0, logger.getRowCount())

        //when (ensure header exists via getCsvPath)
        val path = logger.getCsvPath()

        //then (file exists with only header)
        assertNotNull(path)
        assertEquals(0, logger.getRowCount())
        assertTrue(logger.getFileSize() > 0)
    }
}
