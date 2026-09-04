package org.happycode.karoo.forumslader.ui.main.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.happycode.karoo.forumslader.R
import qrcode.QRCode
import java.util.Locale

@Composable
fun LogExportCard(
    csvRowCount: Int,
    csvFileSize: Long,
    isServerRunning: Boolean,
    serverUrl: String?,
    statusMessage: String? = null,
    onSaveToUsb: () -> Unit,
    onToggleServer: () -> Unit,
    onClearLogs: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.diagnostics_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.label_telemetry_csv),
                    style = MaterialTheme.typography.bodyMedium
                )
                val formattedSize = formatFileSize(csvFileSize)
                Text(
                    text = "$csvRowCount rows ($formattedSize)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            statusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            if (isServerRunning && serverUrl != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.instruction_scan_qr),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    QrCodeCanvas(url = serverUrl, modifier = Modifier.size(160.dp))

                    Text(
                        text = serverUrl,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Button(
                        onClick = onToggleServer,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.action_stop_server))
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onSaveToUsb,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = stringResource(R.string.action_save_usb))
                    }

                    Button(
                        onClick = onToggleServer,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = stringResource(R.string.action_share_wifi))
                    }
                }
            }

            OutlinedButton(
                onClick = onClearLogs,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.action_clear_logs))
            }
        }
    }
}

@Composable
fun QrCodeCanvas(url: String, modifier: Modifier = Modifier) {
    val rawData = remember(url) { QRCode(url).rawData }
    val matrixSize = rawData.size
    val quietZone = 2
    val totalGrid = matrixSize + quietZone * 2

    Box(
        modifier = modifier
            .background(Color.White)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val cellSize = size.width / totalGrid
            for (row in 0 until matrixSize) {
                for (col in 0 until matrixSize) {
                    if (rawData[row][col].dark) {
                        drawRect(
                            color = Color.Black,
                            topLeft = Offset(
                                x = (col + quietZone) * cellSize,
                                y = (row + quietZone) * cellSize
                            ),
                            size = Size(width = cellSize, height = cellSize)
                        )
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}
