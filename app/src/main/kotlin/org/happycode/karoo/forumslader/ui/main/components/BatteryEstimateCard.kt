package org.happycode.karoo.forumslader.ui.main.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.domain.BatteryEstimate
import org.happycode.karoo.forumslader.domain.ChargeState
import kotlin.math.roundToInt

@Composable
fun BatteryEstimateCard(
    estimate: BatteryEstimate?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(id = R.string.battery_range_estimate_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )

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
    // Determine the primary status message
    val statusText = when (estimate.chargeState) {
        ChargeState.CHARGING,
        ChargeState.FULL -> stringResource(R.string.battery_range_charging)
        ChargeState.STANDBY -> stringResource(R.string.battery_range_standby)
        ChargeState.DISCHARGING -> estimate.estimatedRangeKm?.let {
            stringResource(R.string.battery_range_remaining, it.roundToInt())
        } ?: stringResource(R.string.battery_range_not_enough_data)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodyLarge
        )

        // Show discharge rate if actually discharging
        if (estimate.chargeState == ChargeState.DISCHARGING && estimate.avgDischargeRatePctPerKm > 0f) {
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = stringResource(R.string.battery_range_rate, estimate.avgDischargeRatePctPerKm),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    // Display route sufficiency if a route is active
    estimate.isSufficientForRoute?.let { isSufficient ->
        Text(
            text = stringResource(
                if (isSufficient) R.string.battery_range_sufficient
                else R.string.battery_range_insufficient
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
