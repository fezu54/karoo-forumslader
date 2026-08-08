package org.happycode.karoo.forumslader.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.happycode.karoo.forumslader.domain.RideEnergySummary
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun RideHistoryList(history: List<RideEnergySummary>) {
    if (history.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "No ride history available.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }
        return
    }

    val formatter = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm")
        .withZone(ZoneId.systemDefault())

    history.forEach { summary ->
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = formatter.format(summary.rideDate),
                    style = MaterialTheme.typography.titleMedium
                )
                
                HorizontalDivider()
                
                val durationMin = summary.durationSec / 60
                Text(text = "Duration: $durationMin min | Distance: ${String.format(Locale.US, "%.1f", summary.distanceKm)} km")
                
                Text(text = "Energy Gen: ${String.format(Locale.US, "%.2f", summary.totalEnergyWh)} Wh")
                Text(text = "Avg Power: ${String.format(Locale.US, "%.1f", summary.avgPowerW)} W | Peak: ${String.format(Locale.US, "%.1f", summary.peakPowerW)} W")
                Text(text = "Charge State: ${summary.chargingTimeSec}s Chg | ${summary.dischargingTimeSec}s Dischg | ${summary.standbyTimeSec}s Stdby")
                
                val batteryDelta = summary.batteryEndPct - summary.batteryStartPct
                val deltaSign = if (batteryDelta >= 0) "+" else ""
                Text(text = "Battery Change: $deltaSign$batteryDelta% (${summary.batteryStartPct}% → ${summary.batteryEndPct}%)")
            }
        }
    }
}
