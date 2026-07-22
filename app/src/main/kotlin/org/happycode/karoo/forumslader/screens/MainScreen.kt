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

    val prefs = remember { context.getSharedPreferences("forumslader_prefs", Context.MODE_PRIVATE) }
    var wheelsize by remember { mutableIntStateOf(prefs.getInt("wheelsize", 2200)) }
    var poles by remember { mutableIntStateOf(prefs.getInt("poles", 14)) }
    var versionKey by remember {
        mutableStateOf(
            prefs.getString("version", "unknown") ?: "unknown"
        )
    }
    var speedMultiplier by remember { mutableFloatStateOf(prefs.getFloat("speedMultiplier", 1.0f)) }

    DisposableEffect(prefs) {
        val listener =
            SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                when (key) {
                    "wheelsize" -> wheelsize = sharedPreferences.getInt("wheelsize", 2200)
                    "poles" -> poles = sharedPreferences.getInt("poles", 14)
                    "version" -> versionKey =
                        sharedPreferences.getString("version", "unknown") ?: "unknown"

                    "speedMultiplier" -> speedMultiplier =
                        sharedPreferences.getFloat("speedMultiplier", 1.0f)
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
            DataFieldId.TEMPERATURE
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
        onSpeedMultiplierChange = {
            prefs.edit {
                putFloat(
                    "speedMultiplier",
                    it
                )
            }
        })
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
    onSpeedMultiplierChange: (Float) -> Unit
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
                onSpeedMultiplierChange = onSpeedMultiplierChange
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
                    text = if (connected) "Active" else "Inactive",
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
                    text = "Forumslader Device",
                    style = MaterialTheme.typography.bodyLarge
                )
                val (statusIcon, statusColor, statusDesc) = when (sensorState) {
                    is StreamState.Streaming -> Triple(
                        Icons.Default.CheckCircle,
                        MaterialTheme.colorScheme.primary,
                        "Connected"
                    )

                    is StreamState.Searching -> Triple(
                        Icons.Default.HourglassEmpty,
                        MaterialTheme.colorScheme.secondary,
                        "Searching"
                    )

                    is StreamState.NotAvailable -> Triple(
                        Icons.Default.Cancel,
                        MaterialTheme.colorScheme.error,
                        "Not Available"
                    )

                    else -> Triple(
                        Icons.Default.LinkOff,
                        MaterialTheme.colorScheme.outline,
                        "Disconnected"
                    )
                }
                Icon(
                    imageVector = statusIcon,
                    contentDescription = statusDesc,
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
                            "%.1f A",
                            it
                        )
                    } ?: "---"

                    DataFieldId.CONSUMER_CURRENT -> rawValue?.let {
                        String.format(
                            locale,
                            "%.1f A",
                            it
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
    onSpeedMultiplierChange: (Float) -> Unit
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
                text = "Configuration",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            ConfigItem(label = "Wheel Size", value = "$wheelsize mm")
            ConfigItem(label = "Poles", value = "$poles")
            ConfigItem(label = "Version", value = versionKey)
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            ConfigItem(
                label = "Speed Multiplier",
                value = String.format(locale, "%.2fx", speedMultiplier)
            )
            Slider(
                value = speedMultiplier,
                onValueChange = onSpeedMultiplierChange,
                valueRange = 0.5f..2.0f,
                steps = 29,
                modifier = Modifier.fillMaxWidth()
            )
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
            onSpeedMultiplierChange = {}
        )
    }
}
