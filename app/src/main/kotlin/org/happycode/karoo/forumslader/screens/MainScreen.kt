package org.happycode.karoo.forumslader.screens

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.hammerhead.karooext.KarooSystemService
import io.hammerhead.karooext.models.DataType
import io.hammerhead.karooext.models.OnStreamState
import io.hammerhead.karooext.models.StreamState
import io.hammerhead.karooext.models.UserProfile
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_LOCKED_MAC_ADDRESS
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_POLES
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_SPEED_MULTIPLIER
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_VERSION
import org.happycode.karoo.forumslader.PreferencesConstants.KEY_WHEEL_SIZE
import org.happycode.karoo.forumslader.PreferencesConstants.PREFS_NAME
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import org.happycode.karoo.forumslader.theme.AppTheme
import androidx.core.content.edit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val karooSystem = remember { KarooSystemService(context) }
    var connected by remember { mutableStateOf(false) }
    val metrics = remember { mutableStateMapOf<String, Double>() }
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    val streamStates = remember { mutableStateMapOf<String, StreamState>() }
    
    val hasMissingStreams by remember {
        androidx.compose.runtime.derivedStateOf {
            val hasActive = streamStates.values.any { it is StreamState.Streaming || it is StreamState.Searching }
            val hasMissing = streamStates.values.any { it is StreamState.NotAvailable }
            hasActive && hasMissing
        }
    }
    
    val sensorState by remember {
        androidx.compose.runtime.derivedStateOf {
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
            DataFieldId.TOUR_DISTANCE
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

    MainScreenContent(
        connected = connected,
        sensorState = sensorState,
        hasMissingStreams = hasMissingStreams,
        metrics = metrics,
        userProfile = userProfile,
        wheelsize = wheelsize,
        poles = poles,
        versionKey = versionKey,
        speedMultiplier = speedMultiplier,
        lockedMacAddress = lockedMacAddress,
        onSpeedMultiplierChange = {
            prefs.edit {
                putFloat(
                    KEY_SPEED_MULTIPLIER,
                    it
                )
            }
        },
        onForgetDevice = {
            prefs.edit {
                putString(KEY_LOCKED_MAC_ADDRESS, null)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(
    connected: Boolean,
    sensorState: StreamState,
    hasMissingStreams: Boolean = false,
    metrics: Map<String, Double>,
    userProfile: UserProfile?,
    wheelsize: Int,
    poles: Int,
    versionKey: String,
    speedMultiplier: Float,
    lockedMacAddress: String?,
    onSpeedMultiplierChange: (Float) -> Unit,
    onForgetDevice: () -> Unit
) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (hasMissingStreams) {
                MissingStreamsWarning()
            }
            StatusCard(connected = connected, sensorState = sensorState)
            ConfigCard(
                wheelsize = wheelsize,
                poles = poles,
                versionKey = versionKey,
                speedMultiplier = speedMultiplier,
                lockedMacAddress = lockedMacAddress,
                onSpeedMultiplierChange = onSpeedMultiplierChange,
                onForgetDevice = onForgetDevice
            )
            MetricsList(metrics = metrics, userProfile = userProfile)
        }
    }
}

@Composable
fun MissingStreamsWarning() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null
            )
            Text(
                text = stringResource(id = R.string.missing_streams_warning),
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun StatusCard(connected: Boolean, sensorState: StreamState) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(id = R.string.extension_name),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = if (connected) stringResource(R.string.status_active) else stringResource(R.string.status_inactive),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                )
            }
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.forumslader_device_label),
                    style = MaterialTheme.typography.bodyLarge
                )
                val (statusIcon, statusColor, statusResId) = when (sensorState) {
                    is StreamState.Streaming -> Triple(
                        Icons.Default.CheckCircle,
                        MaterialTheme.colorScheme.primary,
                        R.string.status_connected
                    )

                    is StreamState.Searching -> Triple(
                        Icons.Default.HourglassEmpty,
                        MaterialTheme.colorScheme.secondary,
                        R.string.status_searching
                    )

                    is StreamState.NotAvailable -> Triple(
                        Icons.Default.Cancel,
                        MaterialTheme.colorScheme.error,
                        R.string.status_not_available
                    )

                    else -> Triple(
                        Icons.Default.LinkOff,
                        MaterialTheme.colorScheme.outline,
                        R.string.status_disconnected
                    )
                }
                Icon(
                    imageVector = statusIcon,
                    contentDescription = stringResource(statusResId),
                    tint = statusColor
                )
            }
        }
    }
}

@Composable
fun MetricsList(metrics: Map<String, Double>, userProfile: UserProfile?) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.let { config ->
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            config.locales[0]
        } else {
            @Suppress("DEPRECATION")
            config.locale
        }
    }
    val adapter = remember { ForumsladerDataFieldsAdapter(context) }
    val names = remember { adapter.getDataFieldNames() }
    val isImperial =
        userProfile?.preferredUnit?.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            names.forEach { (id, name) ->
                val rawValue = metrics[id]
                val formattedValue = when (id) {
                    DataFieldId.SPEED -> {
                        rawValue?.let {
                            val speedKmh = it * 3.6
                            if (isImperial) {
                                String.format(locale, "%.1f mph", speedKmh * 0.621371)
                            } else {
                                String.format(locale, "%.1f km/h", speedKmh)
                            }
                        } ?: "---"
                    }

                    DataFieldId.TRIP_DISTANCE -> {
                        rawValue?.let {
                            val distanceKm = it / 1000.0
                            if (isImperial) {
                                String.format(locale, "%.2f mi", distanceKm * 0.621371)
                            } else {
                                String.format(locale, "%.2f km", distanceKm)
                            }
                        } ?: "---"
                    }

                    DataFieldId.BATTERY_LEVEL -> rawValue?.let {
                        String.format(
                            locale,
                            "%d%%",
                            it.toInt()
                        )
                    } ?: "---"

                    DataFieldId.BATTERY_VOLTAGE -> rawValue?.let {
                        String.format(
                            locale,
                            "%.1f V",
                            it
                        )
                    } ?: "---"

                    DataFieldId.BATTERY_CURRENT -> rawValue?.let {
                        String.format(
                            locale,
                            "%d mA",
                            it.toInt()
                        )
                    } ?: "---"

                    DataFieldId.CONSUMER_CURRENT -> rawValue?.let {
                        String.format(
                            locale,
                            "%d mA",
                            it.toInt()
                        )
                    } ?: "---"

                    DataFieldId.TEMPERATURE -> rawValue?.let {
                        val isTempImperial = userProfile?.preferredUnit?.temperature == UserProfile.PreferredUnit.UnitType.IMPERIAL
                        if (isTempImperial) {
                            String.format(locale, "%.1f °F", (it * 9 / 5) + 32)
                        } else {
                            String.format(locale, "%.1f °C", it)
                        }
                    } ?: "---"

                    DataFieldId.GENERATOR_GEAR -> rawValue?.let {
                        String.format(locale, "%d", it.toInt())
                    } ?: "---"

                    DataFieldId.CHARGE_STATE -> rawValue?.let {
                        when (it.toInt()) {
                            0 -> "Standby"
                            1 -> "Charging"
                            2 -> "Discharging"
                            3 -> "Full"
                            else -> "Unknown"
                        }
                    } ?: "---"

                    DataFieldId.TRIP_ENERGY,
                    DataFieldId.TOUR_ENERGY -> rawValue?.let {
                        String.format(locale, "%.1f Wh", it)
                    } ?: "---"

                    DataFieldId.DYNAMO_POWER -> rawValue?.let {
                        String.format(locale, "%.1f W", it)
                    } ?: "---"

                    DataFieldId.ODOMETER,
                    DataFieldId.DAY_DISTANCE,
                    DataFieldId.TOUR_DISTANCE -> rawValue?.let {
                        val distanceKm = it / 1000.0
                        if (isImperial) {
                            String.format(locale, "%.2f mi", distanceKm * 0.621371)
                        } else {
                            String.format(locale, "%.2f km", distanceKm)
                        }
                    } ?: "---"

                    DataFieldId.FREQUENCY -> rawValue?.let { String.format(locale, "%.1f Hz", it) } ?: "---"
                    else -> rawValue?.let { String.format(locale, "%.1f", it) } ?: "---"
                }
                MetricItem(
                    label = name,
                    value = formattedValue
                )
                if (id != names.keys.last()) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
        }
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun ConfigCard(
    wheelsize: Int,
    poles: Int,
    versionKey: String,
    speedMultiplier: Float,
    lockedMacAddress: String?,
    onSpeedMultiplierChange: (Float) -> Unit,
    onForgetDevice: () -> Unit
) {
    val locale = LocalConfiguration.current.let { config ->
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            config.locales[0]
        } else {
            @Suppress("DEPRECATION")
            config.locale
        }
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.configuration_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            ConfigItem(label = stringResource(R.string.label_wheel_size), value = "$wheelsize mm")
            ConfigItem(label = stringResource(R.string.label_poles), value = "$poles")
            ConfigItem(label = stringResource(R.string.label_version), value = versionKey)
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            ConfigItem(
                label = stringResource(R.string.label_speed_multiplier),
                value = String.format(locale, "%.2fx", speedMultiplier)
            )
            Slider(
                value = speedMultiplier,
                onValueChange = onSpeedMultiplierChange,
                valueRange = 0.5f..2.0f,
                steps = 149,
                modifier = Modifier.fillMaxWidth()
            )
            if (lockedMacAddress != null) {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.label_locked_device),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = lockedMacAddress,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(onClick = onForgetDevice) {
                        Text(text = stringResource(R.string.forget_device_action))
                    }
                }
            }
        }
    }
}

@Composable
fun ConfigItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Preview(showBackground = true, widthDp = 256, heightDp = 426)
@Composable
fun MainScreenPreview() {
    AppTheme {
        MainScreenContent(
            connected = true,
            sensorState = StreamState.Streaming(
                io.hammerhead.karooext.models.DataPoint(
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
            wheelsize = 2200,
            poles = 14,
            versionKey = "v6",
            speedMultiplier = 1.0f,
            lockedMacAddress = "00:11:22:33:44:55",
            onSpeedMultiplierChange = {},
            onForgetDevice = {}
        )
    }
}
