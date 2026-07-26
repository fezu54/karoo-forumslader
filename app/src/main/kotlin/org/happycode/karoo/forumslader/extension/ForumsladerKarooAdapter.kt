package org.happycode.karoo.forumslader.extension

import android.Manifest
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.ConnectionStatus
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.DeviceEvent
import io.hammerhead.karooext.models.OnConnectionStatus
import io.hammerhead.karooext.models.OnDataPoint
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics
import org.happycode.karoo.forumslader.model.ForumsladerConfig
import org.happycode.karoo.forumslader.model.ForumsladerParser
import org.happycode.karoo.forumslader.model.ForumsladerVersion

class ForumsladerKarooAdapter(
    context: Context,
    val address: String,
    displayName: String? = null
) : ForumsladerBleListener {
    private val config = ForumsladerConfig(context)
    private val parser = ForumsladerParser(config)
    private var currentEmitter: Emitter<DeviceEvent>? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private val bleManager = ForumsladerBleManager(context, address, config, this)
    private val protocol = ForumsladerProtocol(bleManager, parser, mainHandler)

    val device: Device = Device(
        extension = "karoo-forumslader",
        uid = "fl-$address",
        dataTypes = listOf(
            DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_battery_level"),
            DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_battery_voltage"),
            DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_battery_current"),
            DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_consumer_current"),
            DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_speed"),
            DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_trip_distance"),
            DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_frequency"),
            DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_temperature"),
            DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_generator_gear"),
            DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_charge_state"),
            DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_trip_energy"),
            DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_tour_energy"),
            DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_dynamo_power"),
            DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_odometer"),
            DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_day_distance"),
            DataType.dataTypeId(extension = "karoo-forumslader", typeId = "fl_tour_distance")
        ),
        displayName = displayName ?: "Forumslader"
    )

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN])
    fun connect(emitter: Emitter<DeviceEvent>) {
        currentEmitter = emitter
        emitter.setCancellable {
            currentEmitter = null
            protocol.stopParameterRequestLoop()
            parser.resetConfigLoaded()
            bleManager.stop()
        }
        bleManager.start()
    }

    override fun onConnectionStateChanged(connected: Boolean, searching: Boolean) {
        val status = if (connected) ConnectionStatus.CONNECTED else if (searching) ConnectionStatus.SEARCHING else ConnectionStatus.DISCONNECTED
        currentEmitter?.onNext(OnConnectionStatus(status = status))
        if (!connected) {
            protocol.stopParameterRequestLoop()
            parser.resetConfigLoaded()
        }
    }

    override fun onVersionDetected(version: ForumsladerVersion) {
        parser.version = version
        config.version = version
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onNotificationsEnabled() {
        protocol.startParameterRequestLoop()
    }

    override fun onDataReceived(data: ByteArray) {
        val emitter = currentEmitter ?: return
        parser.processIncomingBytes(data)?.let { metrics ->
            if (config.lockedMacAddress != address) {
                Log.i("FL_BLE", "Locking MAC address to $address after first successful data reception")
                config.lockedMacAddress = address
            }
            emitMetrics(emitter, metrics)
        }
    }

    private fun emitMetrics(emitter: Emitter<DeviceEvent>, metrics: ForumsladerMetrics) = listOf(
        "fl_battery_level" to metrics.power.batteryLevelPct.toDouble(),
        "fl_battery_voltage" to metrics.power.batteryVoltage.toDouble(),
        "fl_battery_current" to metrics.power.batteryCurrent.toDouble(),
        "fl_consumer_current" to metrics.power.consumerCurrent.toDouble(),
        "fl_speed" to metrics.dynamics.speedMs.toDouble(),
        "fl_trip_distance" to metrics.distance.tripMeters,
        "fl_frequency" to metrics.dynamics.frequency.toDouble(),
        "fl_temperature" to metrics.environment.temperatureCelsius.toDouble(),
        "fl_generator_gear" to metrics.dynamics.generatorGear.toDouble(),
        "fl_charge_state" to metrics.power.chargeState.ordinal.toDouble(),
        "fl_trip_energy" to metrics.energy.tripWh,
        "fl_tour_energy" to metrics.energy.tourWh,
        "fl_dynamo_power" to metrics.power.dynamoPowerW.toDouble(),
        "fl_odometer" to metrics.distance.odometerMeters,
        "fl_day_distance" to metrics.distance.dayMeters,
        "fl_tour_distance" to metrics.distance.tourMeters
    ).forEach { (typeId, value) ->
        emitter.onNext(
            OnDataPoint(
                dataPoint = DataPoint(
                    dataTypeId = DataType.dataTypeId(
                        extension = "karoo-forumslader",
                        typeId = typeId
                    ),
                    values = mapOf(DataType.Field.SINGLE to value),
                    sourceId = device.uid
                )
            )
        )
    }
}
