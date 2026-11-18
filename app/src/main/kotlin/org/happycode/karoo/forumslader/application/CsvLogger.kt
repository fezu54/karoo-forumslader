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
    private val flushBatchSize: Int = DEFAULT_FLUSH_BATCH_SIZE,
) {
    private val activeCsvPath = directory.resolve(FILE_NAME)
    private var sessionStartTime: Instant? = null
    private var cachedFileSizeBytes: Long = 0L
    private var hasHeader: Boolean = false
    private val pendingRows = StringBuilder()
    private var pendingRowCount: Int = 0
    private var pendingBytes: Long = 0L

    init {
        directory.createDirectories()
        cachedFileSizeBytes = if (activeCsvPath.exists()) {
            runCatching { activeCsvPath.fileSize() }.getOrDefault(0L)
        } else {
            0L
        }
        hasHeader = cachedFileSizeBytes > 0L
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

        pendingRows.append(row)
        pendingRowCount++
        pendingBytes += row.toByteArray(Charsets.UTF_8).size
        if (pendingRowCount >= flushBatchSize) {
            flushLocked()
        }
    }

    @Synchronized
    fun flush() {
        flushLocked()
    }

    @Synchronized
    fun getCsvPath(): Path? {
        ensureHeaderExists()
        flushLocked()
        return if (activeCsvPath.exists()) activeCsvPath else null
    }

    @Synchronized
    fun getRowCount(): Int {
        flushLocked()
        return if (activeCsvPath.exists()) {
            runCatching {
                activeCsvPath.useLines { lines ->
                    val count = lines.count()
                    if (count > 1) count - 1 else 0
                }
            }.getOrDefault(0)
        } else {
            0
        }
    }

    @Synchronized
    fun getFileSize(): Long = cachedFileSizeBytes + pendingBytes

    @Synchronized
    fun clear() {
        sessionStartTime = null
        cachedFileSizeBytes = 0L
        hasHeader = false
        pendingRows.clear()
        pendingRowCount = 0
        pendingBytes = 0L
        directory.listDirectoryEntries("telemetry*.csv").forEach { path ->
            path.deleteIfExists()
        }
    }

    private fun flushLocked() {
        if (pendingRowCount == 0) return
        ensureHeaderExists()
        val batch = pendingRows.toString()
        activeCsvPath.appendText(batch)
        cachedFileSizeBytes += pendingBytes
        pendingRows.clear()
        pendingRowCount = 0
        pendingBytes = 0L
    }

    private fun ensureHeaderExists() {
        if (!hasHeader) {
            val headerLine = "$CSV_HEADER\n"
            activeCsvPath.writeText(headerLine)
            cachedFileSizeBytes = headerLine.toByteArray(Charsets.UTF_8).size.toLong()
            hasHeader = true
        }
    }

    private fun rotateIfNeeded() {
        if (hasHeader && (cachedFileSizeBytes + pendingBytes) >= maxFileSizeBytes) {
            flushLocked()
            val backupPath = directory.resolve("telemetry_backup.csv")
            backupPath.deleteIfExists()
            activeCsvPath.moveTo(backupPath)
            sessionStartTime = null
            cachedFileSizeBytes = 0L
            hasHeader = false
            ensureHeaderExists()
        }
    }

    companion object {
        const val FILE_NAME = "telemetry.csv"
        const val DEFAULT_MAX_FILE_SIZE_BYTES = 5L * 1024L * 1024L
        const val DEFAULT_FLUSH_BATCH_SIZE = 20
        const val CSV_HEADER = "elapsedSeconds,batteryVoltage,batteryCurrent,consumerCurrent,batteryLevel,frequency,speed,tripDistance,temperature,gear,chargeState,tripEnergy,dynamoPower"
    }
}
