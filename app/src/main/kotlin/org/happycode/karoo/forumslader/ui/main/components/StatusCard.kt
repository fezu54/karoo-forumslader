package org.happycode.karoo.forumslader.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.hammerhead.karooext.models.StreamState
import org.happycode.karoo.forumslader.R

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
