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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    modifier: Modifier = Modifier,
    isDownloadingLatest: Boolean = false,
    onStartUpdate: (Uri) -> Unit,
    onDownloadLatest: () -> Unit,
    onCancelUpdate: () -> Unit = {},
    onReset: () -> Unit = {}
) {
    var selectedUri by remember { mutableStateOf(sharedUri) }
    LaunchedEffect(sharedUri) {
        if (sharedUri != null) {
            selectedUri = sharedUri
        }
    }
    val canStartUpdate = dfuState is DfuState.Idle || dfuState is DfuState.Error

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
                enabled = canStartUpdate && !isDownloadingLatest
            ) {
                if (isDownloadingLatest) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }
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
                    enabled = canStartUpdate
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

            val onStartClick: () -> Unit = { selectedUri?.let(onStartUpdate) }

            when (dfuState) {
                is DfuState.Idle -> StartUpdateButton(
                    enabled = selectedUri != null,
                    onClick = onStartClick
                )
                is DfuState.Connecting -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = stringResource(R.string.status_dfu_connecting))
                    CancelUpdateButton(onClick = onCancelUpdate)
                }
                is DfuState.Validating -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = stringResource(R.string.status_dfu_validating))
                    CancelUpdateButton(onClick = onCancelUpdate)
                }
                is DfuState.Uploading -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = stringResource(R.string.status_dfu_uploading, dfuState.progressPercent))
                        LinearProgressIndicator(
                            progress = { dfuState.progressPercent / 100f },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        )
                        CancelUpdateButton(onClick = onCancelUpdate)
                    }
                }
                is DfuState.Verifying -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = stringResource(R.string.status_dfu_verifying))
                    CancelUpdateButton(onClick = onCancelUpdate)
                }
                is DfuState.Success -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.status_dfu_success),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = onReset,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(text = stringResource(R.string.action_done))
                    }
                }
                is DfuState.Error -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.status_dfu_error, dfuState.message),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Medium
                    )
                    StartUpdateButton(
                        enabled = selectedUri != null,
                        onClick = onStartClick
                    )
                }
            }
        }
    }
}

@Composable
private fun StartUpdateButton(enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled
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

@Composable
private fun CancelUpdateButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(text = stringResource(R.string.action_cancel))
    }
}
