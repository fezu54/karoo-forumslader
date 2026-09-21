package org.happycode.karoo.forumslader.application

import io.kotest.core.spec.style.ShouldSpec
import io.kotest.engine.spec.tempdir
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.happycode.karoo.forumslader.domain.ChargeState
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics
import java.time.Instant
import kotlin.io.path.exists
import kotlin.io.path.readLines

class CsvLoggerTest : ShouldSpec({

    fun sampleMetrics(
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

    should("write header and row when first telemetry logged") {
        //given
        val logger = CsvLogger(directory = tempdir().toPath())
        val timestamp = Instant.parse("2024-01-15T10:30:00Z")
        val metrics = sampleMetrics()

        //when
        logger.logTelemetry(metrics, timestamp)

        //then
        val csvPath = logger.getCsvPath().shouldNotBeNull()
        val lines = csvPath.readLines()
        lines shouldHaveSize 2
        lines.first() shouldBe CsvLogger.CSV_HEADER
        lines.last() shouldBe "0.000,7.20,0.350,0.120,85,120.5,25.3,12345.6,22.5,3,CHARGING,45.2,3.8"
        logger.getRowCount() shouldBe 1
        logger.getFileSize() shouldBeGreaterThan 0L
    }

    should("calculate relative elapsed seconds on subsequent telemetry records") {
        //given
        val logger = CsvLogger(directory = tempdir().toPath())
        val startTime = Instant.parse("2024-01-15T10:30:00Z")
        val secondTime = startTime.plusMillis(3250)
        val metrics = sampleMetrics()

        //when
        logger.logTelemetry(metrics, startTime)
        logger.logTelemetry(metrics, secondTime)

        //then
        val csvPath = logger.getCsvPath().shouldNotBeNull()
        val lines = csvPath.readLines()
        lines[1].split(",")[0] shouldBe "0.000"
        lines[2].split(",")[0] shouldBe "3.250"
    }

    should("reset session start time when clear invoked") {
        //given
        val logger = CsvLogger(directory = tempdir().toPath())
        val time1 = Instant.parse("2024-01-15T10:30:00Z")
        val time2 = time1.plusMillis(5000)
        val time3 = time1.plusMillis(10000)
        val metrics = sampleMetrics()

        logger.logTelemetry(metrics, time1)
        logger.logTelemetry(metrics, time2)

        //when
        logger.clear()
        logger.logTelemetry(metrics, time3)

        //then
        val csvPath = logger.getCsvPath().shouldNotBeNull()
        val lines = csvPath.readLines()
        lines[1].split(",")[0] shouldBe "0.000"
    }

    should("append rows without duplicating header on subsequent calls") {
        //given
        val logger = CsvLogger(directory = tempdir().toPath())
        val metrics = sampleMetrics()

        //when
        logger.logTelemetry(metrics)
        logger.logTelemetry(metrics)

        //then
        logger.getRowCount() shouldBe 2
        val csvPath = logger.getCsvPath().shouldNotBeNull()
        val lines = csvPath.readLines()
        lines.count { it == CsvLogger.CSV_HEADER } shouldBe 1
    }

    should("rotate file when maximum file size exceeded") {
        //given
        val tempDir = tempdir().toPath()
        val smallLimitBytes = 180L
        val logger = CsvLogger(directory = tempDir, maxFileSizeBytes = smallLimitBytes)
        val metrics = sampleMetrics()

        //when
        logger.logTelemetry(metrics) // writes header + row (~150 bytes)
        logger.logTelemetry(metrics) // triggers rotation before second row write

        //then
        val backupPath = tempDir.resolve("telemetry_backup.csv")
        backupPath.exists() shouldBe true
        val csvPath = logger.getCsvPath().shouldNotBeNull()
        csvPath.exists() shouldBe true
        logger.getRowCount() shouldBe 1
    }

    should("clear all telemetry files when clear called") {
        //given
        val logger = CsvLogger(directory = tempdir().toPath())
        logger.logTelemetry(sampleMetrics())

        //when
        logger.clear()

        //then
        logger.getRowCount() shouldBe 0
    }

    should("handle null battery level percentage gracefully") {
        //given
        val logger = CsvLogger(directory = tempdir().toPath())
        val metrics = sampleMetrics(batteryLevel = null)

        //when
        logger.logTelemetry(metrics)

        //then
        val csvPath = logger.getCsvPath().shouldNotBeNull()
        val row = csvPath.readLines()[1]
        val fields = row.split(",")
        fields[4] shouldBe ""
    }

    should("return zero rows and size when active file contains only header") {
        //given
        val logger = CsvLogger(directory = tempdir().toPath())

        //when
        val path = logger.getCsvPath()

        //then
        path.shouldNotBeNull()
        logger.getRowCount() shouldBe 0
        logger.getFileSize() shouldBeGreaterThan 0L
    }
})
