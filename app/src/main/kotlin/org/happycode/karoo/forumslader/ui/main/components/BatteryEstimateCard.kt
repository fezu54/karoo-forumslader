package org.happycode.karoo.forumslader.ui.main.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.domain.ChargeState
import org.happycode.karoo.forumslader.domain.estimation.BatteryEstimate
import kotlin.math.roundToInt

@Composable
fun BatteryEstimateCard(
    estimate: BatteryEstimate?,
    modifier: Modifier = Modifier,
    batteryLevel: Double? = null
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(id = R.string.battery_range_estimate_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                val level = batteryLevel?.toInt() ?: estimate?.remainingCapacityPct
                if (level != null) {
                    Text(
                        text = "$level%",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            estimate?.let {
                BatteryEstimateContent(it)
            } ?: run {
                Text(
                    text = stringResource(id = R.string.battery_range_calculating),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BatteryEstimateContent(estimate: BatteryEstimate) {
    val statusText = when (estimate.chargeState) {
        ChargeState.CHARGING,
        ChargeState.FULL -> stringResource(R.string.battery_range_charging)
        ChargeState.STANDBY -> estimate.estimatedRangeKm?.let {
            stringResource(R.string.battery_range_remaining, it.roundToInt())
        } ?: stringResource(R.string.battery_range_standby)
        ChargeState.DISCHARGING -> estimate.estimatedRangeKm?.let {
            stringResource(R.string.battery_range_remaining, it.roundToInt())
        } ?: stringResource(R.string.battery_range_not_enough_data)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f, fill = false)
        )

        if ((estimate.chargeState == ChargeState.DISCHARGING || estimate.chargeState == ChargeState.STANDBY) && estimate.avgDischargeRatePctPerKm > 0f) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.battery_range_rate, estimate.avgDischargeRatePctPerKm),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    estimate.isSufficientForRoute?.let { isSufficient ->
        val containerColor = if (isSufficient) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        }
        val contentColor = if (isSufficient) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        }
        val icon = if (isSufficient) Icons.Default.CheckCircle else Icons.Default.Warning

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .background(containerColor, shape = MaterialTheme.shapes.small)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = stringResource(
                    if (isSufficient) R.string.battery_range_sufficient
                    else R.string.battery_range_insufficient
                ),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = contentColor
            )
        }
    }
}
