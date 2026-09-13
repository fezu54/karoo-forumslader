package org.happycode.karoo.forumslader.extension

import android.util.Log
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.DeveloperField
import io.hammerhead.karooext.models.FieldValue
import io.hammerhead.karooext.models.FitEffect
import io.hammerhead.karooext.models.WriteToRecordMesg
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics

class ForumsladerFitRecorder(
    @Volatile var fitEmitter: Emitter<FitEffect>? = null,
    private val timeProvider: () -> Long = System::currentTimeMillis,
) {
    companion object {
        private const val TAG = "FL_FIT"
        private const val FIT_BASE_TYPE_FLOAT32: Short = 136
        private const val THROTTLE_INTERVAL_MS = 1000L

        val FIELD_VOLTAGE = DeveloperField(0, FIT_BASE_TYPE_FLOAT32, "Battery Voltage", "V")
        val FIELD_CURRENT = DeveloperField(1, FIT_BASE_TYPE_FLOAT32, "Battery Current", "A")
        val FIELD_POWER = DeveloperField(2, FIT_BASE_TYPE_FLOAT32, "Dynamo Power", "W")
        val FIELD_TEMP = DeveloperField(3, FIT_BASE_TYPE_FLOAT32, "Temperature", "C")
        val FIELD_SPEED = DeveloperField(4, FIT_BASE_TYPE_FLOAT32, "Speed", "km/h")
        val FIELD_ENERGY = DeveloperField(5, FIT_BASE_TYPE_FLOAT32, "Trip Energy", "Wh")
    }

    private var lastEmitTimestampMs: Long = 0L

    fun onMetricsReceived(metrics: ForumsladerMetrics) {
        val emitter = fitEmitter ?: return

        val now = timeProvider()
        if (now - lastEmitTimestampMs < THROTTLE_INTERVAL_MS) return
        lastEmitTimestampMs = now

        val values = listOf(
            FieldValue(FIELD_VOLTAGE, metrics.power.batteryVoltage.toDouble()),
            FieldValue(FIELD_CURRENT, metrics.power.batteryCurrent.toDouble()),
            FieldValue(FIELD_POWER, metrics.power.dynamoPowerWatts.toDouble()),
            FieldValue(FIELD_TEMP, metrics.environment.temperatureCelsius.toDouble()),
            FieldValue(FIELD_SPEED, metrics.dynamics.speedMetersPerSecond * 3.6),
            FieldValue(FIELD_ENERGY, metrics.energy.tripWattHours)
        )

        Log.d(TAG, "Emitting FIT record with ${values.size} fields")
        emitter.onNext(WriteToRecordMesg(values))
    }
}

