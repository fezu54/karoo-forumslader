package org.happycode.karoo.forumslader.screens

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
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import org.happycode.karoo.forumslader.model.ForumsladerConfig
import org.happycode.karoo.forumslader.theme.AppTheme
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val config = remember { ForumsladerConfig(context) }
    val karooSystem = remember { KarooSystemService(context) }
    var connected by remember { mutableStateOf(false) }
    var sensorState by remember { mutableStateOf<StreamState>(StreamState.Idle) }
    var userProfile by remember { mutableStateOf<UserProfile?>(null) }
    val metrics = remember { mutableStateMapOf<String, Double>() }

    DisposableEffect(karooSystem) {
        karooSystem.connect { connected = it }

        val extensionId = "karoo-forumslader"
        val types = listOf(
            DataFieldId.BATTERY_LEVEL,
            DataFieldId.CONSUMER_CURRENT,
            DataFieldId.SPEED,
            DataFieldId.TRIP_DISTANCE
        )

        val listeners = mutableListOf<String>()
        
        listeners.add(karooSystem.addConsumer { profile: UserProfile ->
            userProfile = profile
        })

        types.forEach { typeId ->
            val dataTypeId = DataType.dataTypeId(extensionId, typeId)
            listeners.add(karooSystem.addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState ->
                sensorState = event.state
                (event.state as? StreamState.Streaming)?.dataPoint?.values?.get(DataType.Field.SINGLE)?.let { value ->
                    metrics[typeId] = value
                }
            })
        }

        onDispose {
            listeners.forEach { karooSystem.removeConsumer(it) }
            karooSystem.disconnect()
        }
    }

    MainScreenContent(connected = connected, sensorState = sensorState, metrics = metrics, userProfile = userProfile, config = config)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(connected: Boolean, sensorState: StreamState, metrics: Map<String, Double>, userProfile: UserProfile?, config: ForumsladerConfig) {
    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusCard(connected = connected, sensorState = sensorState)
            ConfigCard(config = config)
            MetricsList(metrics = metrics, userProfile = userProfile)
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
    val adapter = remember { ForumsladerDataFieldsAdapter(context) }
    val names = remember { adapter.getDataFieldNames() }
    val isImperial = userProfile?.preferredUnit?.distance == UserProfile.PreferredUnit.UnitType.IMPERIAL

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
                                String.format(Locale.getDefault(), "%.1f mph", speedKmh * 0.621371)
                            } else {
                                String.format(Locale.getDefault(), "%.1f km/h", speedKmh)
                            }
                        } ?: "---"
                    }
                    DataFieldId.TRIP_DISTANCE -> {
                        rawValue?.let {
                            val distanceKm = it / 1000.0
                            if (isImperial) {
                                String.format(Locale.getDefault(), "%.2f mi", distanceKm * 0.621371)
                            } else {
                                String.format(Locale.getDefault(), "%.2f km", distanceKm)
                            }
                        } ?: "---"
                    }
                    DataFieldId.BATTERY_LEVEL -> rawValue?.let { String.format(Locale.getDefault(), "%d%%", it.toInt()) } ?: "---"
                    DataFieldId.CONSUMER_CURRENT -> rawValue?.let { String.format(Locale.getDefault(), "%.1f A", it) } ?: "---"
                    else -> rawValue?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "---"
                }
                MetricItem(
                    label = name,
                    value = formattedValue
                )
                if (id != names.keys.last()) {
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
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
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun ConfigCard(config: ForumsladerConfig) {
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
            ConfigItem(label = "Wheel Size", value = "${config.wheelsize} mm")
            ConfigItem(label = "Poles", value = "${config.poles}")
            ConfigItem(label = "Version", value = config.version.key)
        }
    }
}

@Composable
fun ConfigItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Preview(showBackground = true, widthDp = 256, heightDp = 426)
@Composable
fun MainScreenPreview() {
    AppTheme {
        MainScreenContent(
            connected = true,
            sensorState = StreamState.Streaming(io.hammerhead.karooext.models.DataPoint("", emptyMap(), "")),
            metrics = mapOf(
                DataFieldId.BATTERY_LEVEL to 85.0,
                DataFieldId.SPEED to 7.05 // ~25.4 km/h
            ),
            userProfile = null,
            config = ForumsladerConfig(LocalContext.current)
        )
    }
}
