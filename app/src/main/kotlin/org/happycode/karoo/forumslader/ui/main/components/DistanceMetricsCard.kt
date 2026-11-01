package org.happycode.karoo.forumslader.ui.main.components

import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.hammerhead.karooext.models.UserProfile
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import org.happycode.karoo.forumslader.theme.forumsladerColors
import org.happycode.karoo.forumslader.ui.main.MetricFormatter

@Composable
fun DistanceMetricsCard(
    metrics: Map<String, Double>,
    userProfile: UserProfile?,
    connected: Boolean,
    onResetDayDistance: () -> Unit,
    onResetTourDistance: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.let { config ->
        config.locales[0]
    }

    val adapter = remember { ForumsladerDataFieldsAdapter(context) }
    val names = remember { adapter.getDataFieldNames() }
    val formatter = remember(userProfile, locale) {
        MetricFormatter.from(context, userProfile, locale)
    }

    var resetDayDistanceDialog by remember { mutableStateOf(false) }
    var resetTourDistanceDialog by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Route,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.card_title_distance),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            MetricRow(
                label = names[DataFieldId.SPEED] ?: DataFieldId.SPEED,
                value = formatter.format(DataFieldId.SPEED, metrics[DataFieldId.SPEED]),
                icon = Icons.Default.Speed,
                iconTint = MaterialTheme.forumsladerColors.accentDistance
            )

            MetricRow(
                label = names[DataFieldId.TRIP_DISTANCE] ?: DataFieldId.TRIP_DISTANCE,
                value = formatter.format(DataFieldId.TRIP_DISTANCE, metrics[DataFieldId.TRIP_DISTANCE]),
                icon = Icons.AutoMirrored.Filled.DirectionsBike,
                iconTint = MaterialTheme.forumsladerColors.accentDistance
            )

            ResettableMetricRow(
                label = names[DataFieldId.DAY_DISTANCE] ?: DataFieldId.DAY_DISTANCE,
                value = formatter.format(DataFieldId.DAY_DISTANCE, metrics[DataFieldId.DAY_DISTANCE]),
                icon = Icons.Default.Today,
                connected = connected,
                onResetClick = { resetDayDistanceDialog = true }
            )

            ResettableMetricRow(
                label = names[DataFieldId.TOUR_DISTANCE] ?: DataFieldId.TOUR_DISTANCE,
                value = formatter.format(DataFieldId.TOUR_DISTANCE, metrics[DataFieldId.TOUR_DISTANCE]),
                icon = Icons.Default.Map,
                connected = connected,
                onResetClick = { resetTourDistanceDialog = true }
            )

            MetricRow(
                label = names[DataFieldId.TRIP_ENERGY] ?: DataFieldId.TRIP_ENERGY,
                value = formatter.format(DataFieldId.TRIP_ENERGY, metrics[DataFieldId.TRIP_ENERGY]),
                icon = Icons.Default.Bolt,
                iconTint = MaterialTheme.forumsladerColors.accentPower
            )

            MetricRow(
                label = names[DataFieldId.TOUR_ENERGY] ?: DataFieldId.TOUR_ENERGY,
                value = formatter.format(DataFieldId.TOUR_ENERGY, metrics[DataFieldId.TOUR_ENERGY]),
                icon = Icons.Default.BatterySaver,
                iconTint = MaterialTheme.forumsladerColors.accentPower
            )

            MetricRow(
                label = names[DataFieldId.ODOMETER] ?: DataFieldId.ODOMETER,
                value = formatter.format(DataFieldId.ODOMETER, metrics[DataFieldId.ODOMETER]),
                icon = Icons.Default.PinDrop,
                iconTint = MaterialTheme.colorScheme.secondary
            )
        }

        if (resetDayDistanceDialog) {
            AlertDialog(
                onDismissRequest = { resetDayDistanceDialog = false },
                title = { Text(stringResource(R.string.reset_day_distance_title)) },
                text = { Text(stringResource(R.string.reset_day_distance_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        onResetDayDistance()
                        resetDayDistanceDialog = false
                    }) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { resetDayDistanceDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }

        if (resetTourDistanceDialog) {
            AlertDialog(
                onDismissRequest = { resetTourDistanceDialog = false },
                title = { Text(stringResource(R.string.reset_tour_distance_title)) },
                text = { Text(stringResource(R.string.reset_tour_distance_message)) },
                confirmButton = {
                    TextButton(onClick = {
                        onResetTourDistance()
                        resetTourDistanceDialog = false
                    }) {
                        Text(stringResource(android.R.string.ok))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { resetTourDistanceDialog = false }) {
                        Text(stringResource(android.R.string.cancel))
                    }
                }
            )
        }
    }
}

@Composable
fun ResettableMetricRow(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    connected: Boolean,
    onResetClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                shape = RoundedCornerShape(8.dp)
            )
            .padding(horizontal = 8.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f, fill = false),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.forumsladerColors.accentDistance,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(modifier = Modifier.width(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                FilledTonalIconButton(
                    onClick = onResetClick,
                    enabled = connected,
                    modifier = Modifier.size(24.dp),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = stringResource(R.string.action_reset_metric, label),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
