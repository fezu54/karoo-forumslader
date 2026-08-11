package org.happycode.karoo.forumslader.ui.main.components

import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import io.hammerhead.karooext.models.UserProfile
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter
import org.happycode.karoo.forumslader.adapters.ForumsladerDataFieldsAdapter.DataFieldId
import org.happycode.karoo.forumslader.ui.main.MetricFormatter

@Composable
fun MetricsList(
    metrics: Map<String, Double>, 
    userProfile: UserProfile?, 
    connected: Boolean,
    onResetDayDistance: () -> Unit,
    onResetTourDistance: () -> Unit
) {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.let { config ->
        if (VERSION.SDK_INT >= VERSION_CODES.N) {
            config.locales[0]
        } else {
            @Suppress("DEPRECATION")
            config.locale
        }
    }
    
    val adapter = remember { ForumsladerDataFieldsAdapter(context) }
    val names = remember { adapter.getDataFieldNames() }
    
    val formatter = remember(userProfile, locale) {
        MetricFormatter.from(context, userProfile, locale)
    }

    var resetDayDistanceDialog by remember { mutableStateOf(false) }
    var resetTourDistanceDialog by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            names.forEach { (id, name) ->
                val rawValue = metrics[id]
                val formattedValue = formatter.format(id, rawValue)
                
                MetricItem(
                    label = name,
                    value = formattedValue,
                    onResetClick = when (id) {
                        DataFieldId.DAY_DISTANCE -> { { resetDayDistanceDialog = true } }
                        DataFieldId.TOUR_DISTANCE -> { { resetTourDistanceDialog = true } }
                        else -> null
                    },
                    connected = connected
                )
                if (id != names.keys.last()) {
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }
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
fun MetricItem(label: String, value: String, onResetClick: (() -> Unit)? = null, connected: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (onResetClick != null) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onResetClick,
                    enabled = connected,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset $label",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
