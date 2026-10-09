package org.happycode.karoo.forumslader.ui.main.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.unit.dp
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.domain.DfuState

@Composable
fun FirmwareUpdateCard(
    dfuState: DfuState,
    sharedUri: Uri?,
    onStartUpdate: (Uri) -> Unit,
    onDownloadLatest: () -> Unit,
) {
    var selectedUri by remember { mutableStateOf(sharedUri) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { selectedUri = it }
    }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.firmware_update_title),
                style = MaterialTheme.typography.titleMedium
            )

            Button(
                onClick = onDownloadLatest,
                modifier = Modifier.fillMaxWidth(),
                enabled = dfuState is DfuState.Idle
            ) {
                Text(text = stringResource(R.string.action_download_latest))
            }

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
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
                    color = MaterialTheme.colorScheme.primary
                )
                is DfuState.Error -> Text(
                    text = stringResource(R.string.status_dfu_error, dfuState.message),
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
