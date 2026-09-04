package org.happycode.karoo.forumslader.application

import org.happycode.karoo.forumslader.domain.ForumsladerMetrics
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.io.path.appendText
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.fileSize
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.moveTo
import kotlin.io.path.useLines
import kotlin.io.path.writeText

class CsvLogger(
    private val directory: Path,
    private val maxFileSizeBytes: Long = DEFAULT_MAX_FILE_SIZE_BYTES,
) {
    private val activeCsvPath = directory.resolve(FILE_NAME)
    private var sessionStartTime: Instant? = null

    init {
        directory.createDirectories()
    }

    @Synchronized
    fun logTelemetry(metrics: ForumsladerMetrics, timestamp: Instant = Instant.now()) {
        rotateIfNeeded()
        ensureHeaderExists()

        val startTime = sessionStartTime ?: timestamp.also { sessionStartTime = it }
        val elapsedSeconds = Duration.between(startTime, timestamp).toMillis() / 1000.0

        val row = buildString {
            append(String.format(Locale.US, "%.3f", elapsedSeconds))
            append(",")
            append(String.format(Locale.US, "%.2f", metrics.power.batteryVoltage))
            append(",")
            append(String.format(Locale.US, "%.3f", metrics.power.batteryCurrent))
            append(",")
            append(String.format(Locale.US, "%.3f", metrics.power.consumerCurrent))
            append(",")
            append(metrics.power.batteryLevelPercentage?.toString() ?: "")
            append(",")
            append(String.format(Locale.US, "%.1f", metrics.dynamics.frequency))
            append(",")
            append(String.format(Locale.US, "%.1f", metrics.dynamics.speedMetersPerSecond * 3.6f))
            append(",")
            append(String.format(Locale.US, "%.1f", metrics.distance.tripMeters))
            append(",")
            append(String.format(Locale.US, "%.1f", metrics.environment.temperatureCelsius))
            append(",")
            append(metrics.dynamics.generatorGear)
            append(",")
            append(metrics.power.chargeState.name)
            append(",")
            append(String.format(Locale.US, "%.1f", metrics.energy.tripWattHours))
            append(",")
            append(String.format(Locale.US, "%.1f", metrics.power.dynamoPowerWatts))
            append("\n")
        }

        activeCsvPath.appendText(row)
    }

    @Synchronized
    fun getCsvPath(): Path? {
        ensureHeaderExists()
        return if (activeCsvPath.exists()) activeCsvPath else null
    }

    @Synchronized
    fun getRowCount(): Int =
        if (activeCsvPath.exists()) {
            runCatching {
                activeCsvPath.useLines { lines ->
                    val count = lines.count()
                    if (count > 1) count - 1 else 0
                }
            }.getOrDefault(0)
        } else {
            0
        }

    @Synchronized
    fun getFileSize(): Long =
        if (activeCsvPath.exists()) runCatching { activeCsvPath.fileSize() }.getOrDefault(0L) else 0L

    @Synchronized
    fun clear() {
        sessionStartTime = null
        directory.listDirectoryEntries("telemetry*.csv").forEach { path ->
            path.deleteIfExists()
        }
    }

    private fun ensureHeaderExists() {
        if ((!activeCsvPath.exists()) || (activeCsvPath.fileSize() == 0L)) {
            activeCsvPath.writeText("$CSV_HEADER\n")
        }
    }

    private fun rotateIfNeeded() {
        if (activeCsvPath.exists() && (activeCsvPath.fileSize() >= maxFileSizeBytes)) {
            val backupPath = directory.resolve("telemetry_backup.csv")
            backupPath.deleteIfExists()
            activeCsvPath.moveTo(backupPath)
            sessionStartTime = null
            ensureHeaderExists()
        }
    }

    companion object {
        const val FILE_NAME = "telemetry.csv"
        const val DEFAULT_MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L
        const val CSV_HEADER = "elapsedSeconds,batteryVoltage,batteryCurrent,consumerCurrent,batteryLevel,frequency,speed,tripDistance,temperature,gear,chargeState,tripEnergy,dynamoPower"
    }
}
