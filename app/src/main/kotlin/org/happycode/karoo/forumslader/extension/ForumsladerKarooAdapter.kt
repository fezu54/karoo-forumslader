package org.happycode.karoo.forumslader.extension

import android.Manifest
import android.content.Context
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_BATTERY_LOW_THRESHOLD
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_HIGH_TEMP_THRESHOLD
import org.happycode.karoo.forumslader.application.BatteryEstimateStore
import org.happycode.karoo.forumslader.domain.BatteryEstimate
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
import io.hammerhead.karooext.models.FitEffect
import io.hammerhead.karooext.models.OnNavigationState
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import org.happycode.karoo.forumslader.application.CsvLogger
import org.happycode.karoo.forumslader.application.CsvLoggerProvider
import org.happycode.karoo.forumslader.application.ForumsladerStateStore

class ForumsladerKarooAdapter(
    private val context: Context,
    val address: String,
    displayName: String? = null,
    private val adapterScope: CoroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob()),
    private val bleManager: ForumsladerBleManager = ForumsladerBleManager(address, adapterScope),
    private val karooSystem: KarooSystemService = KarooSystemService(context.applicationContext),
    private val csvLogger: CsvLogger = CsvLoggerProvider.getInstance(context.filesDir.toPath().resolve("telemetry")),
) {
    companion object {
        private const val EXTENSION_ID = "karoo-forumslader"

        private val METRICS_REGISTRY = listOf<Pair<String, MetricContext.() -> Number?>>(
            "fl_battery_voltage" to { metrics.power.batteryVoltage },
            "fl_battery_current" to { metrics.power.batteryCurrent * 1000.0 },
            "fl_consumer_current" to { metrics.power.consumerCurrent * 1000.0 },
            "fl_speed" to { metrics.dynamics.speedMetersPerSecond },
            "fl_trip_distance" to { metrics.distance.tripMeters },
            "fl_frequency" to { metrics.dynamics.frequency },
            "fl_temperature" to { metrics.environment.temperatureCelsius },
            "fl_generator_gear" to { metrics.dynamics.generatorGear },
            "fl_charge_state" to { metrics.power.chargeState.ordinal },
            "fl_trip_energy" to { metrics.energy.tripWattHours },
            "fl_tour_energy" to { metrics.energy.tourWattHours },
            "fl_dynamo_power" to { metrics.power.dynamoPowerWatts },
            "fl_odometer" to { metrics.distance.odometerMeters },
            "fl_day_distance" to { metrics.distance.dayMeters },
            "fl_tour_distance" to { metrics.distance.tourMeters },
            "fl_battery_level" to { metrics.power.batteryLevelPercentage },
            "fl_battery_range" to { estimate?.estimatedRangeKm?.let { it * 1000.0 } }
        )
    }

    private data class MetricContext(
        val metrics: ForumsladerMetrics,
        val estimate: BatteryEstimate?
    )

    private val config = ForumsladerConfig(context)
    private val parser = ForumsladerParser(config)
    private var currentEmitter: Emitter<DeviceEvent>? = null
    
    private val protocol = ForumsladerProtocol(bleManager, adapterScope)

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
    
    private var flowCollectionJob: Job? = null
    private val consumers = mutableListOf<String>()

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
        karooSystem.connect {}

        consumers += karooSystem.addConsumer { event: OnNavigationState ->
            val state = event.state
            if (state is OnNavigationState.NavigationState.NavigatingRoute) {
                val totalElevation = state.climbs.sumOf { it.totalElevation }
                batteryEstimator.onRouteRemaining(state.routeDistance, totalElevation)
            } else {
                batteryEstimator.onRouteRemaining(null, null)
            }
            BatteryEstimateStore.updateEstimate(batteryEstimator.getEstimate())
        }

        consumers += karooSystem.addConsumer(OnStreamState.StartStreaming(DataType.dataTypeId("karoo-headwind", "headwindSpeed"))) { event: OnStreamState ->
            val state = event.state
            if (state is StreamState.Streaming) {
                val windSpeed = state.dataPoint.values[DataType.Field.SINGLE]
                batteryEstimator.onHeadwindSpeed(windSpeed?.toFloat())
            } else {
                batteryEstimator.onHeadwindSpeed(null)
            }
            BatteryEstimateStore.updateEstimate(batteryEstimator.getEstimate())
        }

        emitter.setCancellable {
            currentEmitter = null
            flowCollectionJob?.cancel()
            consumers.forEach { karooSystem.removeConsumer(it) }
            consumers.clear()
            protocol.stopParameterRequestLoop()
            parser.resetConfigLoaded()
            bleManager.stop()
            karooSystem.disconnect()
            BatteryEstimateStore.clear()
        }
        
        flowCollectionJob = adapterScope.launch {
            launch {
                parser.isConfigLoadedFlow.collect {
                    ForumsladerStateStore.setConfigLoaded(it)
                }
            }
            launch {
                bleManager.connectionState.collect { status ->
                    currentEmitter?.onNext(OnConnectionStatus(status = status))
                    if (status == ConnectionStatus.DISCONNECTED) {
                        protocol.stopParameterRequestLoop()
                        parser.resetConfigLoaded()
                        BatteryEstimateStore.clear()
                    }
                }
            }
            launch {
                bleManager.versionDetected.collect { version ->
                    parser.version = version
                    config.version = version
                }
            }
            launch {
                bleManager.notificationsEnabled.collect {
                    protocol.startParameterRequestLoop()
                }
            }
            launch {
                bleManager.incomingData.collect { data ->
                    val em = currentEmitter ?: return@collect
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
                        emitMetrics(em, metrics)
                        evaluateAlerts(metrics)
                        fitRecorder.onMetricsReceived(metrics)
                        csvLogger.logTelemetry(metrics)
                    }
                }
            }
        }
        
        bleManager.start()
    }

    private fun evaluateAlerts(metrics: ForumsladerMetrics) {
        alertManager.evaluate(metrics).forEach { alert ->
            karooSystem.dispatch(
                InRideAlert(
                    id = alert.id,
                    icon = R.drawable.ic_alert,
                    title = alert.title,
                    detail = resolveAlertDetail(alert),
                    autoDismissMs = 5000L,
                    backgroundColor = android.R.color.holo_red_dark,
                    textColor = android.R.color.white
                )
            )
        }
    }

    private fun resolveAlertDetail(alert: ForumsladerAlert): String = when (alert) {
        is ForumsladerAlert.BatteryLow -> context.getString(R.string.alert_battery_low_detail, alert.percentage)
        is ForumsladerAlert.ShortCircuit -> context.getString(R.string.alert_short_circuit_detail)
        is ForumsladerAlert.SystemInterrupt -> context.getString(R.string.alert_system_interrupt_detail)
        is ForumsladerAlert.HighTemperature -> context.getString(R.string.alert_high_temperature_detail, alert.temperature.toInt())
    }

    private fun emitMetrics(emitter: Emitter<DeviceEvent>, metrics: ForumsladerMetrics) {
        val context = MetricContext(metrics, batteryEstimator.getEstimate())
        METRICS_REGISTRY.asSequence()
            .mapNotNull { (typeId, extract) ->
                context.extract()?.let { typeId to it }
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

    fun setFitEmitter(emitter: Emitter<FitEffect>?) = run { fitRecorder.fitEmitter = emitter }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendCommand(command: String) {
        bleManager.writeCommand(command.toByteArray(Charsets.US_ASCII))
        if (command.startsWith($$"$FLT,6") || command.startsWith($$"$FLT,7")) {
            parser.resetConfigLoaded()
            protocol.startParameterRequestLoop()
        }
    }
}
