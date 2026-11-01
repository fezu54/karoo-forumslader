package org.happycode.karoo.forumslader.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.theme.forumsladerColors

@Composable
fun AlertsConfigCard(
    batteryLowThreshold: Int,
    highTempThreshold: Float,
    onBatteryLowThresholdChange: (Int) -> Unit,
    onHighTempThresholdChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.alerts_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // Low battery threshold section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.BatteryAlert,
                    contentDescription = null,
                    tint = MaterialTheme.forumsladerColors.statusDanger,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                ConfigItem(
                    label = stringResource(R.string.label_battery_low_threshold),
                    value = "$batteryLowThreshold %"
                )
            }

            ThresholdStepper(
                currentFraction = (batteryLowThreshold - 5f) / 45f,
                canDecrease = batteryLowThreshold > 5,
                canIncrease = batteryLowThreshold < 50,
                onDecrease = { onBatteryLowThresholdChange((batteryLowThreshold - 1).coerceAtLeast(5)) },
                onIncrease = { onBatteryLowThresholdChange((batteryLowThreshold + 1).coerceAtMost(50)) },
                indicatorColor = MaterialTheme.forumsladerColors.statusDanger
            )

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            // High temperature threshold section
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Thermostat,
                    contentDescription = null,
                    tint = MaterialTheme.forumsladerColors.statusWarning,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                ConfigItem(
                    label = stringResource(R.string.label_high_temp_threshold),
                    value = "${highTempThreshold.toInt()} °C"
                )
            }

            ThresholdStepper(
                currentFraction = (highTempThreshold - 30f) / 50f,
                canDecrease = highTempThreshold > 30f,
                canIncrease = highTempThreshold < 80f,
                onDecrease = { onHighTempThresholdChange((highTempThreshold - 1f).coerceAtLeast(30f)) },
                onIncrease = { onHighTempThresholdChange((highTempThreshold + 1f).coerceAtMost(80f)) },
                indicatorColor = MaterialTheme.forumsladerColors.statusWarning
            )
        }
    }
}

@Composable
private fun ThresholdStepper(
    currentFraction: Float,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    indicatorColor: androidx.compose.ui.graphics.Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FilledTonalIconButton(
            onClick = onDecrease,
            enabled = canDecrease,
            modifier = Modifier.size(36.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = stringResource(R.string.action_decrease),
                modifier = Modifier.size(18.dp)
            )
        }

        LinearProgressIndicator(
            progress = { currentFraction.coerceIn(0f, 1f) },
            color = indicatorColor,
            trackColor = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 4.dp)
        )

        FilledTonalIconButton(
            onClick = onIncrease,
            enabled = canIncrease,
            modifier = Modifier.size(36.dp),
            colors = IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = stringResource(R.string.action_increase),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
