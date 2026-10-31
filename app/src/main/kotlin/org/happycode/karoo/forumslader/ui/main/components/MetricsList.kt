package org.happycode.karoo.forumslader.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.hammerhead.karooext.models.UserProfile

@Composable
fun MetricsList(
    metrics: Map<String, Double>, 
    userProfile: UserProfile?, 
    connected: Boolean,
    onResetDayDistance: () -> Unit,
    onResetTourDistance: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PowerMetricsCard(
            metrics = metrics,
            userProfile = userProfile
        )
        DistanceMetricsCard(
            metrics = metrics,
            userProfile = userProfile,
            connected = connected,
            onResetDayDistance = onResetDayDistance,
            onResetTourDistance = onResetTourDistance
        )
    }
}
