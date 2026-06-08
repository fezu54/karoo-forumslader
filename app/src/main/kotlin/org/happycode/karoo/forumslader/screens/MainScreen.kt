package org.happycode.karoo.forumslader.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import org.happycode.karoo.forumslader.theme.AppTheme
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val karooSystem = remember { KarooSystemService(context) }
    var connected by remember { mutableStateOf(false) }
    var sensorState by remember { mutableStateOf<StreamState>(StreamState.Idle) }
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

        val listeners = types.map { typeId ->
            val dataTypeId = DataType.dataTypeId(extensionId, typeId)
            karooSystem.addConsumer(OnStreamState.StartStreaming(dataTypeId)) { event: OnStreamState ->
                sensorState = event.state
                (event.state as? StreamState.Streaming)?.dataPoint?.values?.get(DataType.Field.SINGLE)?.let { value ->
                    metrics[typeId] = value
                }
            }
        }

        onDispose {
            listeners.forEach { karooSystem.removeConsumer(it) }
            karooSystem.disconnect()
        }
    }

    MainScreenContent(connected = connected, sensorState = sensorState, metrics = metrics)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenContent(connected: Boolean, sensorState: StreamState, metrics: Map<String, Double>) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(text = stringResource(id = R.string.app_name)) }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StatusCard(connected = connected, sensorState = sensorState)
            MetricsList(metrics = metrics)
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
                val (statusText, statusColor) = when (sensorState) {
                    is StreamState.Streaming -> "Connected" to MaterialTheme.colorScheme.primary
                    is StreamState.Searching -> "Searching" to MaterialTheme.colorScheme.secondary
                    is StreamState.NotAvailable -> "Not Available" to MaterialTheme.colorScheme.error
                    else -> "Disconnected" to MaterialTheme.colorScheme.outline
                }
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
        }
    }
}

@Composable
fun MetricsList(metrics: Map<String, Double>) {
    val context = LocalContext.current
    val adapter = remember { ForumsladerDataFieldsAdapter(context) }
    val names = remember { adapter.getDataFieldNames() }

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            names.forEach { (id, name) ->
                MetricItem(
                    label = name,
                    value = metrics[id]?.let { String.format(Locale.getDefault(), "%.1f", it) } ?: "---"
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

@Preview(showBackground = true, widthDp = 256, heightDp = 426)
@Composable
fun MainScreenPreview() {
    AppTheme {
        Surface {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatusCard(connected = true, sensorState = StreamState.Streaming(io.hammerhead.karooext.models.DataPoint("", emptyMap(), "")))
                MetricsList(
                    metrics = mapOf(
                        DataFieldId.BATTERY_LEVEL to 85.0,
                        DataFieldId.SPEED to 25.4
                    )
                )
            }
        }
    }
}
