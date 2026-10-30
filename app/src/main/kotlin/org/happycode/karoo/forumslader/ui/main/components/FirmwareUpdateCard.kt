package org.happycode.karoo.forumslader.ui.main.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.domain.DfuState

@Composable
fun FirmwareUpdateCard(
    dfuState: DfuState,
    sharedUri: Uri?,
    onStartUpdate: (Uri) -> Unit,
    onDownloadLatest: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedUri by remember { mutableStateOf(sharedUri) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri = it }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.firmware_update_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onDownloadLatest,
                modifier = Modifier.fillMaxWidth(),
                enabled = dfuState is DfuState.Idle
            ) {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.action_download_latest))
            }

            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                    Text(
                        text = stringResource(R.string.label_select_local_file),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (selectedUri != null) {
                        Text(
                            text = stringResource(
                                R.string.label_selected_file,
                                selectedUri?.lastPathSegment ?: ""
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                OutlinedButton(
                    onClick = { filePickerLauncher.launch(arrayOf("application/octet-stream", "*/*")) },
                    enabled = dfuState is DfuState.Idle
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(R.string.action_browse))
                }
            }

            when (dfuState) {
                is DfuState.Idle -> {
                    Button(
                        onClick = { selectedUri?.let { onStartUpdate(it) } },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = selectedUri != null
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdateAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.action_start_update))
                    }
                }
                is DfuState.Connecting -> Text(text = stringResource(R.string.status_dfu_connecting))
                is DfuState.Validating -> Text(text = stringResource(R.string.status_dfu_validating))
                is DfuState.Uploading -> {
                    Column {
                        Text(text = stringResource(R.string.status_dfu_uploading, dfuState.progressPercent))
                        LinearProgressIndicator(
                            progress = { dfuState.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                    }
                }
                is DfuState.Verifying -> Text(text = stringResource(R.string.status_dfu_verifying))
                is DfuState.Success -> Text(
                    text = stringResource(R.string.status_dfu_success),
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                is DfuState.Error -> Text(
                    text = stringResource(R.string.status_dfu_error, dfuState.message),
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
