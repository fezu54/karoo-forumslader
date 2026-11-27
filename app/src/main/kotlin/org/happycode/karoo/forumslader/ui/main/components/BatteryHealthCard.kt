package org.happycode.karoo.forumslader.ui.main.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryStd
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.domain.health.BatteryHealthAnalyzer
import org.happycode.karoo.forumslader.domain.health.BatteryHealthSnapshot
import org.happycode.karoo.forumslader.domain.health.BatteryHealthStatus
import org.happycode.karoo.forumslader.domain.health.BatteryHealthSummary
import org.happycode.karoo.forumslader.theme.forumsladerColors

@Composable
fun BatteryHealthCard(
    summary: BatteryHealthSummary?,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BatteryHealthHeader(status = summary?.status)

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            summary?.let {
                BatteryHealthContent(summary = it)
            } ?: Text(
                text = stringResource(id = R.string.battery_health_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BatteryHealthHeader(status: BatteryHealthStatus?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.BatteryStd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = stringResource(id = R.string.battery_health_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        status?.let { BatteryHealthStatusBadge(status = it) }
    }
}

@Composable
private fun BatteryHealthStatusBadge(status: BatteryHealthStatus) {
    val customColors = MaterialTheme.forumsladerColors
    val (labelResId, badgeColor) = when (status) {
        BatteryHealthStatus.GOOD -> R.string.battery_health_status_good to customColors.statusGood
        BatteryHealthStatus.DEGRADING -> R.string.battery_health_status_degrading to customColors.statusWarning
        BatteryHealthStatus.REPLACE_SOON -> R.string.battery_health_status_replace_soon to customColors.statusDanger
    }

    Text(
        text = stringResource(id = labelResId),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = badgeColor,
        modifier = Modifier
            .background(
                color = badgeColor.copy(alpha = 0.16f),
                shape = RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun BatteryHealthContent(summary: BatteryHealthSummary) {
    Text(
        text = stringResource(
            id = R.string.battery_health_capacity_format,
            summary.currentCapacityMah,
            summary.currentCapacityWh,
            summary.stateOfHealthPct.roundToInt()
        ),
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.SemiBold
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = pluralStringResource(
                id = R.plurals.battery_health_cycles_count,
                count = summary.cycleCount,
                summary.cycleCount
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LifespanProjectionText(summary = summary)
    }

    if (summary.history.size >= 2) {
        Text(
            text = stringResource(id = R.string.battery_health_chart_title),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        BatteryCapacityTrendChart(
            history = summary.history,
            baselineCapacityMah = summary.baselineCapacityMah,
            status = summary.status,
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
        )
    }
}

@Composable
private fun LifespanProjectionText(summary: BatteryHealthSummary) {
    val customColors = MaterialTheme.forumsladerColors
    when {
        summary.status == BatteryHealthStatus.REPLACE_SOON || summary.estimatedCyclesRemaining == 0 -> {
            Text(
                text = stringResource(id = R.string.battery_health_replacement_recommended),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = customColors.statusDanger
            )
        }
        summary.estimatedCyclesRemaining != null -> {
            Text(
                text = stringResource(
                    id = R.string.battery_health_cycles_remaining,
                    summary.estimatedCyclesRemaining
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        summary.history.size < 2 -> {
            Text(
                text = stringResource(id = R.string.battery_health_more_rides_for_trend),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BatteryCapacityTrendChart(
    history: List<BatteryHealthSnapshot>,
    baselineCapacityMah: Int,
    status: BatteryHealthStatus,
    modifier: Modifier = Modifier
) {
    val customColors = MaterialTheme.forumsladerColors
    val lineColor = when (status) {
        BatteryHealthStatus.GOOD -> customColors.statusGood
        BatteryHealthStatus.DEGRADING -> customColors.statusWarning
        BatteryHealthStatus.REPLACE_SOON -> customColors.statusDanger
    }
    val thresholdColor = customColors.statusDanger.copy(alpha = 0.6f)
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(modifier = modifier.padding(vertical = 4.dp)) {
        val layout = computeBatteryHealthChartLayout(
            history = history,
            baselineCapacityMah = baselineCapacityMah,
            widthPx = size.width,
            heightPx = size.height
        ) ?: return@Canvas

        drawTrendReferenceLines(gridColor = gridColor, thresholdColor = thresholdColor, thresholdY = layout.thresholdY)
        drawTrendSeries(points = layout.points, lineColor = lineColor)
    }
}

private fun DrawScope.drawTrendReferenceLines(
    gridColor: Color,
    thresholdColor: Color,
    thresholdY: Float
) {
    drawLine(
        color = gridColor,
        start = Offset(0f, size.height),
        end = Offset(size.width, size.height),
        strokeWidth = 1.dp.toPx()
    )
    drawLine(
        color = thresholdColor,
        start = Offset(0f, thresholdY),
        end = Offset(size.width, thresholdY),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
    )
}

private fun DrawScope.drawTrendSeries(
    points: List<Offset>,
    lineColor: Color
) {
    val linePath = Path().apply {
        points.forEachIndexed { index, point ->
            if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
        }
    }
    val fillPath = Path().apply {
        addPath(linePath)
        lineTo(points.last().x, size.height)
        lineTo(points.first().x, size.height)
        close()
    }

    drawPath(path = fillPath, color = lineColor.copy(alpha = 0.15f))
    drawPath(
        path = linePath,
        color = lineColor,
        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
    points.forEach { point ->
        drawCircle(color = Color.White, radius = 3.5.dp.toPx(), center = point)
        drawCircle(color = lineColor, radius = 2.5.dp.toPx(), center = point)
    }
}

internal data class BatteryHealthChartLayout(
    val points: List<Offset>,
    val thresholdY: Float
)

internal fun computeBatteryHealthChartLayout(
    history: List<BatteryHealthSnapshot>,
    baselineCapacityMah: Int,
    widthPx: Float,
    heightPx: Float
): BatteryHealthChartLayout? {
    if (history.size < 2 || baselineCapacityMah <= 0 || widthPx <= 0f || heightPx <= 0f) return null

    val thresholdMah = baselineCapacityMah * (BatteryHealthAnalyzer.DEGRADING_SOH_THRESHOLD_PCT / 100f)
    val minY = minOf(history.minOf { it.fullChargeCapacityMah }.toFloat(), thresholdMah) * 0.9f
    val maxY = maxOf(history.maxOf { it.fullChargeCapacityMah }.toFloat(), baselineCapacityMah.toFloat())
    val ySpan = (maxY - minY).coerceAtLeast(1f)

    fun mapY(mah: Float): Float =
        heightPx - (((mah - minY) / ySpan).coerceIn(0f, 1f) * heightPx)

    val stepX = widthPx / (history.size - 1)
    val points = history.mapIndexed { index, snapshot ->
        Offset(
            x = index * stepX,
            y = mapY(snapshot.fullChargeCapacityMah.toFloat())
        )
    }
    return BatteryHealthChartLayout(
        points = points,
        thresholdY = mapY(thresholdMah)
    )
}
