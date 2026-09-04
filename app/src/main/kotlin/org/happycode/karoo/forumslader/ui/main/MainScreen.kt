package org.happycode.karoo.forumslader.ui.main

import android.content.Context
import android.content.SharedPreferences
import android.os.Process
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataPoint
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_BATTERY_LOW_THRESHOLD
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_HIGH_TEMP_THRESHOLD
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_LOCKED_MAC_ADDRESS
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_POLES
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_SPEED_MULTIPLIER
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_VERSION
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_WHEEL_SIZE
import org.happycode.karoo.forumslader.PreferencesConstants.PREFS_NAME
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import org.happycode.karoo.forumslader.adapters.network.CoroutineLogServer
import org.happycode.karoo.forumslader.adapters.storage.PublicStorageAdapter
import org.happycode.karoo.forumslader.application.BatteryEstimateStore
import org.happycode.karoo.forumslader.application.CsvLoggerProvider
import org.happycode.karoo.forumslader.application.ForumsladerStateStore
import org.happycode.karoo.forumslader.application.LogServerGateway
import org.happycode.karoo.forumslader.application.LogcatDumper
import org.happycode.karoo.forumslader.application.PublicStorageGateway
import org.happycode.karoo.forumslader.domain.BatteryEstimate
import org.happycode.karoo.forumslader.domain.CommandBus
import org.happycode.karoo.forumslader.theme.AppTheme
import org.happycode.karoo.forumslader.ui.main.components.AlertsConfigCard
import org.happycode.karoo.forumslader.ui.main.components.BatteryEstimateCard
import org.happycode.karoo.forumslader.ui.main.components.ConfigCard
import org.happycode.karoo.forumslader.ui.main.components.LogExportCard
import org.happycode.karoo.forumslader.ui.main.components.MetricsList
import org.happycode.karoo.forumslader.ui.main.components.MissingStreamsWarning
import org.happycode.karoo.forumslader.ui.main.components.StatusCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val karooSystem = remember { KarooSystemService(context) }
    var connected by remember { mutableStateOf(false) }
    val metrics = remember { mutableStateMapOf<String, Double>() }
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    val streamStates = remember { mutableStateMapOf<String, StreamState>() }
    val estimate by BatteryEstimateStore.estimateFlow.collectAsState(null)

    val hasMissingStreams by remember {
        derivedStateOf {
            val hasActive = streamStates.values.any { it is StreamState.Streaming || it is StreamState.Searching }
            val hasMissing = streamStates.values.any { it is StreamState.NotAvailable }
            hasActive && hasMissing
        }
    }

    val sensorState by remember {
        derivedStateOf {
            when {
                streamStates.values.any { it is StreamState.Streaming } ->
                    streamStates.values.first { it is StreamState.Streaming }
                streamStates.values.any { it is StreamState.Searching } ->
                    StreamState.Searching
                streamStates.values.isNotEmpty() && streamStates.values.all { it is StreamState.NotAvailable } ->
                    StreamState.NotAvailable
                else -> StreamState.Idle
            }
        }
    }

    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var wheelsize by remember { mutableIntStateOf(prefs.getInt(KEY_WHEEL_SIZE, 2200)) }
    var poles by remember { mutableIntStateOf(prefs.getInt(KEY_POLES, 14)) }
    var versionKey by remember {
        mutableStateOf(
            prefs.getString(KEY_VERSION, "unknown") ?: "unknown"
        )
    }
    var speedMultiplier by remember { mutableFloatStateOf(prefs.getFloat(KEY_SPEED_MULTIPLIER, 1.0f)) }
    var lockedMacAddress by remember { mutableStateOf(prefs.getString(KEY_LOCKED_MAC_ADDRESS, null)) }
    var batteryLowThreshold by remember { mutableIntStateOf(prefs.getInt(KEY_BATTERY_LOW_THRESHOLD, 20)) }
    var highTempThreshold by remember { mutableFloatStateOf(prefs.getFloat(KEY_HIGH_TEMP_THRESHOLD, 50f)) }

    DisposableEffect(prefs) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                when (key) {
                    KEY_WHEEL_SIZE -> wheelsize = sharedPreferences.getInt(KEY_WHEEL_SIZE, 2200)
                    KEY_POLES -> poles = sharedPreferences.getInt(KEY_POLES, 14)
                    KEY_VERSION -> versionKey =
                        sharedPreferences.getString(KEY_VERSION, "unknown") ?: "unknown"
                    KEY_SPEED_MULTIPLIER -> speedMultiplier =
                        sharedPreferences.getFloat(KEY_SPEED_MULTIPLIER, 1.0f)
                    KEY_LOCKED_MAC_ADDRESS -> lockedMacAddress =
                        sharedPreferences.getString(KEY_LOCKED_MAC_ADDRESS, null)
                    KEY_BATTERY_LOW_THRESHOLD -> batteryLowThreshold =
                        sharedPreferences.getInt(KEY_BATTERY_LOW_THRESHOLD, 20)
                    KEY_HIGH_TEMP_THRESHOLD -> highTempThreshold =
                        sharedPreferences.getFloat(KEY_HIGH_TEMP_THRESHOLD, 50f)
                }
            }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    DisposableEffect(karooSystem) {
        karooSystem.connect { connected = it }

        val extensionId = "karoo-forumslader"
        val types = listOf(
            DataFieldId.BATTERY_LEVEL,
            DataFieldId.BATTERY_VOLTAGE,
            DataFieldId.BATTERY_CURRENT,
            DataFieldId.CONSUMER_CURRENT,
            DataFieldId.SPEED,
            DataFieldId.TRIP_DISTANCE,
            DataFieldId.FREQUENCY,
            DataFieldId.TEMPERATURE,
            DataFieldId.GENERATOR_GEAR,
            DataFieldId.CHARGE_STATE,
            DataFieldId.TRIP_ENERGY,
            DataFieldId.TOUR_ENERGY,
            DataFieldId.DYNAMO_POWER,
            DataFieldId.ODOMETER,
            DataFieldId.DAY_DISTANCE,
            DataFieldId.TOUR_DISTANCE,
            DataFieldId.BATTERY_RANGE
        )

        val listeners = mutableListOf<String>()

        listeners.add(karooSystem.addConsumer { profile: UserProfile ->
            userProfile = profile
        })

        types.forEach { typeId ->
            val dataTypeId = DataType.dataTypeId(extensionId, typeId)
            listeners.add(karooSystem.addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState ->
                streamStates[typeId] = event.state
                (event.state as? StreamState.Streaming)?.dataPoint?.values?.get(DataType.Field.SINGLE)
                    ?.let { value ->
                        metrics[typeId] = value
                    }
            })
        }

        onDispose {
            listeners.forEach { karooSystem.removeConsumer(it) }
            karooSystem.disconnect()
        }
    }

    val configLoaded by ForumsladerStateStore.isConfigLoadedFlow.collectAsState(false)

    val coroutineScope = rememberCoroutineScope()
    val telemetryDir = remember { context.filesDir.toPath().resolve("telemetry") }
    val csvLogger = remember { CsvLoggerProvider.getInstance(telemetryDir) }
    val logcatDumper = remember { LogcatDumper(telemetryDir) }
    val publicStorageGateway: PublicStorageGateway = remember { PublicStorageAdapter(context) }

    var csvRowCount by remember { mutableIntStateOf(csvLogger.getRowCount()) }
    var csvFileSize by remember { mutableLongStateOf(csvLogger.getFileSize()) }
    var logExportStatusResId by remember { mutableStateOf<Int?>(null) }
    val logExportStatus = logExportStatusResId?.let { stringResource(it) }

    val serverGateway: LogServerGateway = remember {
        CoroutineLogServer(
            scope = coroutineScope,
            getLogcatPath = {
                logcatDumper.dumpLogcat(Process.myPid())
            },
            getCsvPath = {
                csvLogger.getCsvPath()
            }
        )
    }

    val isServerRunning by serverGateway.isRunning.collectAsState()
    val serverUrl by serverGateway.serverUrl.collectAsState()

    DisposableEffect(Unit) {
        onDispose {
            serverGateway.stop()
        }
    }

    MainScreenContent(
        connected = connected,
        configLoaded = configLoaded,
        sensorState = sensorState,
        hasMissingStreams = hasMissingStreams,
        metrics = metrics,
        userProfile = userProfile,
        estimate = estimate,
        wheelsize = wheelsize,
        poles = poles,
        versionKey = versionKey,
        speedMultiplier = speedMultiplier,
        lockedMacAddress = lockedMacAddress,
        batteryLowThreshold = batteryLowThreshold,
        highTempThreshold = highTempThreshold,
        csvRowCount = csvRowCount,
        csvFileSize = csvFileSize,
        isServerRunning = isServerRunning,
        serverUrl = serverUrl,
        logExportStatus = logExportStatus,
        onSpeedMultiplierChange = {
            prefs.edit { putFloat(KEY_SPEED_MULTIPLIER, it) }
        },
        onForgetDevice = {
            prefs.edit { putString(KEY_LOCKED_MAC_ADDRESS, null) }
        },
        onBatteryLowThresholdChange = {
            prefs.edit { putInt(KEY_BATTERY_LOW_THRESHOLD, it) }
        },
        onHighTempThresholdChange = {
            prefs.edit { putFloat(KEY_HIGH_TEMP_THRESHOLD, it) }
        },
        onResetDayDistance = {
            CommandBus.sendCommand($$"$FLT,7*45\n")
        },
        onResetTourDistance = {
            CommandBus.sendCommand($$"$FLT,6*44\n")
        },
        onSaveToUsb = {
            coroutineScope.launch(Dispatchers.IO) {
                val logcatPath = logcatDumper.dumpLogcat(Process.myPid())
                val logcatResult = publicStorageGateway.exportToPublicStorage(logcatPath, "forumslader-logcat.txt")
                val csvResult = csvLogger.getCsvPath()?.let { csvPath ->
                    publicStorageGateway.exportToPublicStorage(csvPath, "telemetry.csv")
                }
                val isSuccess = logcatResult.isSuccess && (csvResult == null || csvResult.isSuccess)
                logExportStatusResId = if (isSuccess) {
                    R.string.status_usb_exported
                } else {
                    R.string.status_usb_export_failed
                }
                csvRowCount = csvLogger.getRowCount()
                csvFileSize = csvLogger.getFileSize()
            }
        },
        onToggleServer = {
            if (isServerRunning) {
                serverGateway.stop()
            } else {
                coroutineScope.launch(Dispatchers.IO) {
                    logcatDumper.dumpLogcat(Process.myPid())
                    serverGateway.start(port = 8080)
                    csvRowCount = csvLogger.getRowCount()
                    csvFileSize = csvLogger.getFileSize()
                }
            }
        },
        onClearLogs = {
            csvLogger.clear()
            logcatDumper.clear()
            csvRowCount = 0
            csvFileSize = 0L
            logExportStatusResId = R.string.status_logs_cleared
        }
    )
}

@Composable
fun MainScreenContent(
    connected: Boolean,
    configLoaded: Boolean,
    sensorState: StreamState,
    hasMissingStreams: Boolean = false,
    metrics: Map<String, Double>,
    userProfile: UserProfile?,
    estimate: BatteryEstimate?,
    wheelsize: Int,
    poles: Int,
    versionKey: String,
    speedMultiplier: Float,
    lockedMacAddress: String?,
    batteryLowThreshold: Int,
    highTempThreshold: Float,
    csvRowCount: Int = 0,
    csvFileSize: Long = 0L,
    isServerRunning: Boolean = false,
    serverUrl: String? = null,
    logExportStatus: String? = null,
    onSpeedMultiplierChange: (Float) -> Unit,
    onForgetDevice: () -> Unit,
    onBatteryLowThresholdChange: (Int) -> Unit,
    onHighTempThresholdChange: (Float) -> Unit,
    onResetDayDistance: () -> Unit,
    onResetTourDistance: () -> Unit,
    onSaveToUsb: () -> Unit = {},
    onToggleServer: () -> Unit = {},
    onClearLogs: () -> Unit = {}
) {
    Scaffold { padding ->
        val pagerState = rememberPagerState(pageCount = { 2 })
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (page == 0) {
                        if (hasMissingStreams) {
                            MissingStreamsWarning()
                        }
                        StatusCard(connected = connected, sensorState = sensorState)
                        BatteryEstimateCard(estimate = estimate)
                        MetricsList(
                            metrics = metrics, 
                            userProfile = userProfile, 
                            connected = connected && configLoaded,
                            onResetDayDistance = onResetDayDistance,
                            onResetTourDistance = onResetTourDistance
                        )
                    } else {
                        ConfigCard(
                            wheelsize = wheelsize,
                            poles = poles,
                            versionKey = versionKey,
                            speedMultiplier = speedMultiplier,
                            lockedMacAddress = lockedMacAddress,
                            onSpeedMultiplierChange = onSpeedMultiplierChange,
                            onForgetDevice = onForgetDevice
                        )
                        AlertsConfigCard(
                            batteryLowThreshold = batteryLowThreshold,
                            highTempThreshold = highTempThreshold,
                            onBatteryLowThresholdChange = onBatteryLowThresholdChange,
                            onHighTempThresholdChange = onHighTempThresholdChange
                        )
                        LogExportCard(
                            csvRowCount = csvRowCount,
                            csvFileSize = csvFileSize,
                            isServerRunning = isServerRunning,
                            serverUrl = serverUrl,
                            statusMessage = logExportStatus,
                            onSaveToUsb = onSaveToUsb,
                            onToggleServer = onToggleServer,
                            onClearLogs = onClearLogs
                        )
                    }
                }
            }

            // Page indicator
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                repeat(2) { iteration ->
                    val color = if (pagerState.currentPage == iteration) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    Box(
                        modifier = Modifier
                            .padding(2.dp)
                            .background(color, CircleShape)
                            .size(8.dp)
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 256, heightDp = 426)
@Composable
fun MainScreenPreview() {
    AppTheme {
        MainScreenContent(
            connected = true,
            configLoaded = true,
            sensorState = StreamState.Streaming(
                DataPoint(
                    "",
                    emptyMap(),
                    ""
                )
            ),
            hasMissingStreams = false,
            metrics = mapOf(
                DataFieldId.BATTERY_LEVEL to 85.0,
                DataFieldId.SPEED to 7.05 // ~25.4 km/h
            ),
            userProfile = null,
            estimate = null,
            wheelsize = 2200,
            poles = 14,
            versionKey = "v6",
            speedMultiplier = 1.0f,
            lockedMacAddress = "00:11:22:33:44:55",
            batteryLowThreshold = 20,
            highTempThreshold = 50f,
            csvRowCount = 120,
            csvFileSize = 4096L,
            isServerRunning = false,
            serverUrl = null,
            logExportStatus = null,
            onSpeedMultiplierChange = {},
            onForgetDevice = {},
            onBatteryLowThresholdChange = {},
            onHighTempThresholdChange = {},
            onResetDayDistance = {},
            onResetTourDistance = {},
            onSaveToUsb = {},
            onToggleServer = {},
            onClearLogs = {}
        )
    }
}
