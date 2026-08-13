package org.happycode.karoo.forumslader.extension

import android.Manifest
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.annotation.RequiresPermission
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.internal.Emitter
import io.hammerhead.karooext.models.ConnectionStatus
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.Device
import io.hammerhead.karooext.models.DeviceEvent
import io.hammerhead.karooext.models.InRideAlert
import io.hammerhead.karooext.models.OnConnectionStatus
import io.hammerhead.karooext.models.OnDataPoint
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_BATTERY_LOW_THRESHOLD
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_HIGH_TEMP_THRESHOLD
import org.happycode.karoo.forumslader.application.BatteryEstimateStore
import org.happycode.karoo.forumslader.domain.BatteryEstimator
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.domain.BatteryLowRule
import org.happycode.karoo.forumslader.domain.ForumsladerAlert
import org.happycode.karoo.forumslader.domain.ForumsladerAlertManager
import org.happycode.karoo.forumslader.domain.ForumsladerMetrics
import org.happycode.karoo.forumslader.domain.HighTemperatureRule
import org.happycode.karoo.forumslader.domain.StatusBitmaskRule
import org.happycode.karoo.forumslader.model.ForumsladerConfig
import org.happycode.karoo.forumslader.model.ForumsladerParser
import org.happycode.karoo.forumslader.model.ForumsladerVersion
import io.hammerhead.karooext.models.FitEffect
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState

class ForumsladerKarooAdapter(
    private val context: Context,
    val address: String,
    displayName: String? = null
) : ForumsladerBleListener {
    companion object {
        private const val EXTENSION_ID = "karoo-forumslader"

        private val METRICS_REGISTRY = listOf<Pair<String, ForumsladerMetrics.() -> Number?>>(
            "fl_battery_voltage" to { power.batteryVoltage },
            "fl_battery_current" to { power.batteryCurrent * 1000.0 },
            "fl_consumer_current" to { power.consumerCurrent * 1000.0 },
            "fl_speed" to { dynamics.speedMetersPerSecond },
            "fl_trip_distance" to { distance.tripMeters },
            "fl_frequency" to { dynamics.frequency },
            "fl_temperature" to { environment.temperatureCelsius },
            "fl_generator_gear" to { dynamics.generatorGear },
            "fl_charge_state" to { power.chargeState.ordinal },
            "fl_trip_energy" to { energy.tripWattHours },
            "fl_tour_energy" to { energy.tourWattHours },
            "fl_dynamo_power" to { power.dynamoPowerWatts },
            "fl_odometer" to { distance.odometerMeters },
            "fl_day_distance" to { distance.dayMeters },
            "fl_tour_distance" to { distance.tourMeters },
            "fl_battery_level" to { power.batteryLevelPercentage },
            "fl_battery_range" to { null } // Handled dynamically in emitMetrics
        )
    }

    private val config = ForumsladerConfig(context)
    private val parser = ForumsladerParser(config)
    private var currentEmitter: Emitter<DeviceEvent>? = null
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private val bleManager = ForumsladerBleManager(context, address, config, this)
    private val protocol = ForumsladerProtocol(bleManager, parser, mainHandler)

    private val karooSystem = KarooSystemService(context.applicationContext)
    private val sharedPrefs = context.getSharedPreferences(
        org.happycode.karoo.forumslader.PreferencesConstants.PREFS_NAME,
        Context.MODE_PRIVATE
    )
    private val alertManager = ForumsladerAlertManager(
        listOf(
            BatteryLowRule({ sharedPrefs.getInt(KEY_BATTERY_LOW_THRESHOLD, 20) }),
            HighTemperatureRule({ sharedPrefs.getFloat(KEY_HIGH_TEMP_THRESHOLD, 50f) }),
            StatusBitmaskRule(0x8, ForumsladerAlert.ShortCircuit),
            StatusBitmaskRule(0x800000, ForumsladerAlert.SystemInterrupt)
        )
    )

    private val fitRecorder = ForumsladerFitRecorder(karooSystem)
    private val batteryEstimator = BatteryEstimator()

    init {
        karooSystem.connect {}
        
        karooSystem.addConsumer { event: OnNavigationState ->
            val state = event.state
            if (state is OnNavigationState.NavigationState.NavigatingRoute) {
                val totalElevation = state.climbs.sumOf { it.totalElevation }
                batteryEstimator.onRouteRemaining(state.routeDistance, totalElevation)
            } else {
                batteryEstimator.onRouteRemaining(null, null)
            }
            BatteryEstimateStore.updateEstimate(batteryEstimator.getEstimate())
        }
        
        karooSystem.addConsumer(OnStreamState.StartStreaming(DataType.dataTypeId("karoo-headwind", "headwindSpeed"))) { event: OnStreamState ->
            val state = event.state
            if (state is StreamState.Streaming) {
                val windSpeed = state.dataPoint.values[DataType.Field.SINGLE]
                batteryEstimator.onHeadwindSpeed(windSpeed?.toFloat())
            } else {
                batteryEstimator.onHeadwindSpeed(null)
            }
            BatteryEstimateStore.updateEstimate(batteryEstimator.getEstimate())
        }
    }

    val device: Device = Device(
        extension = EXTENSION_ID,
        uid = "fl-$address",
        dataTypes = METRICS_REGISTRY.map { (id, _) ->
            DataType.dataTypeId(extension = EXTENSION_ID, typeId = id)
        },
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
            karooSystem.disconnect()
            BatteryEstimateStore.clear()
        }
        bleManager.start()
    }

    override fun onConnectionStateChanged(connected: Boolean, searching: Boolean) {
        val status =
            if (connected) ConnectionStatus.CONNECTED else if (searching) ConnectionStatus.SEARCHING else ConnectionStatus.DISCONNECTED
        currentEmitter?.onNext(OnConnectionStatus(status = status))
        if (!connected) {
            protocol.stopParameterRequestLoop()
            parser.resetConfigLoaded()
            BatteryEstimateStore.clear()
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
                Log.i(
                    "FL_BLE",
                    "Locking MAC address to $address after first successful data reception"
                )
                config.lockedMacAddress = address
            }
            batteryEstimator.onMetrics(metrics)
            BatteryEstimateStore.updateEstimate(batteryEstimator.getEstimate())
            emitMetrics(emitter, metrics)
            evaluateAlerts(metrics)
            fitRecorder.onMetricsReceived(metrics)
        }
    }

    private fun evaluateAlerts(metrics: ForumsladerMetrics) {
        val alerts = alertManager.evaluate(metrics)

        alerts.forEach { alert ->
            val detail = when (alert) {
                is ForumsladerAlert.BatteryLow -> context.getString(R.string.alert_battery_low_detail, alert.percentage)
                is ForumsladerAlert.ShortCircuit -> context.getString(R.string.alert_short_circuit_detail)
                is ForumsladerAlert.SystemInterrupt -> context.getString(R.string.alert_system_interrupt_detail)
                is ForumsladerAlert.HighTemperature -> context.getString(R.string.alert_high_temperature_detail, alert.temperature.toInt())
            }
            karooSystem.dispatch(
                InRideAlert(
                    id = alert.id,
                    icon = R.drawable.ic_alert,
                    title = "Forumslader",
                    detail = detail,
                    autoDismissMs = 5000L,
                    backgroundColor = android.R.color.holo_red_dark,
                    textColor = android.R.color.white
                )
            )
        }
    }

    private fun emitMetrics(emitter: Emitter<DeviceEvent>, metrics: ForumsladerMetrics) {
        METRICS_REGISTRY.asSequence()
            .mapNotNull { (typeId, extract) -> 
                if (typeId == "fl_battery_range") {
                    batteryEstimator.getEstimate()?.estimatedRangeKm?.let { typeId to it }
                } else {
                    metrics.extract()?.let { typeId to it }
                }
            }
            .map { (typeId, value) ->
                OnDataPoint(
                    dataPoint = DataPoint(
                        dataTypeId = DataType.dataTypeId(
                            extension = EXTENSION_ID,
                            typeId = typeId
                        ),
                        values = mapOf(DataType.Field.SINGLE to value.toDouble()),
                        sourceId = device.uid
                    )
                )
            }
            .forEach(emitter::onNext)
    }

    fun setFitEmitter(emitter: Emitter<FitEffect>?) {
        fitRecorder.fitEmitter = emitter
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendCommand(command: String) =
        bleManager.writeCommand(command.toByteArray(Charsets.US_ASCII))
}
