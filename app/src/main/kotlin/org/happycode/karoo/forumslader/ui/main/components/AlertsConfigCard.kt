package org.happycode.karoo.forumslader.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.happycode.karoo.forumslader.R

@Composable
fun AlertsConfigCard(
    batteryLowThreshold: Int,
    highTempThreshold: Float,
    onBatteryLowThresholdChange: (Int) -> Unit,
    onHighTempThresholdChange: (Float) -> Unit
) {
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
                text = stringResource(R.string.alerts_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            ConfigItem(
                label = stringResource(R.string.label_battery_low_threshold),
                value = "$batteryLowThreshold %"
            )
            Slider(
                value = batteryLowThreshold.toFloat(),
                onValueChange = { onBatteryLowThresholdChange(it.toInt()) },
                valueRange = 5f..50f,
                steps = 44,
                modifier = Modifier.fillMaxWidth()
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            ConfigItem(
                label = stringResource(R.string.label_high_temp_threshold),
                value = "${highTempThreshold.toInt()} °C"
            )
            Slider(
                value = highTempThreshold,
                onValueChange = onHighTempThresholdChange,
                valueRange = 30f..80f,
                steps = 49,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
