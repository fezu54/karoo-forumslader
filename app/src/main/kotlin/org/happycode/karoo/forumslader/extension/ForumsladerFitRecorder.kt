package org.happycode.karoo.forumslader.extension

import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DeveloperField
import io.hammerhead.karooext.models.FieldValue
import io.hammerhead.karooext.models.RideState
import io.hammerhead.karooext.models.WriteToRecordMesg
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.FitEffect
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics

class ForumsladerFitRecorder(
    karooSystem: KarooSystemService
) {
    companion object {
        private const val FIT_BASE_TYPE_FLOAT32: Short = 136

        val FIELD_VOLTAGE = DeveloperField(0, FIT_BASE_TYPE_FLOAT32, "Battery Voltage", "V")
        val FIELD_CURRENT = DeveloperField(1, FIT_BASE_TYPE_FLOAT32, "Battery Current", "A")
        val FIELD_POWER = DeveloperField(2, FIT_BASE_TYPE_FLOAT32, "Dynamo Power", "W")
        val FIELD_TEMP = DeveloperField(3, FIT_BASE_TYPE_FLOAT32, "Temperature", "C")
        val FIELD_SPEED = DeveloperField(4, FIT_BASE_TYPE_FLOAT32, "Speed", "km/h")
        val FIELD_ENERGY = DeveloperField(5, FIT_BASE_TYPE_FLOAT32, "Trip Energy", "Wh")
    }

    var rideState: RideState = RideState.Idle
        internal set

    var fitEmitter: Emitter<FitEffect>? = null

    init {
        karooSystem.addConsumer(RideState.Params) { event: RideState ->
            rideState = event
        }
    }

    fun onMetricsReceived(metrics: ForumsladerMetrics) {
        if (rideState !is RideState.Recording) return
        val emitter = fitEmitter ?: return

        val values = listOf(
            FieldValue(FIELD_VOLTAGE, metrics.power.batteryVoltage.toDouble()),
            FieldValue(FIELD_CURRENT, metrics.power.batteryCurrent.toDouble()),
            FieldValue(FIELD_POWER, metrics.power.dynamoPowerWatts.toDouble()),
            FieldValue(FIELD_TEMP, metrics.environment.temperatureCelsius.toDouble()),
            FieldValue(FIELD_SPEED, metrics.dynamics.speedMetersPerSecond * 3.6),
            FieldValue(FIELD_ENERGY, metrics.energy.tripWattHours)
        )

        emitter.onNext(WriteToRecordMesg(values))
    }
}
