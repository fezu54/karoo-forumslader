package org.happycode.karoo.forumslader.ui.main.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.happycode.karoo.forumslader.R
import org.happycode.karoo.forumslader.domain.DynamoPolePreset
import org.happycode.karoo.forumslader.domain.WheelSizePreset

@Composable
fun ConfigCard(
    wheelsize: Int,
    poles: Int,
    versionKey: String,
    lockedMacAddress: String?,
    onConfigUpdate: (wheelsize: Int, poles: Int) -> Unit,
    onForgetDevice: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.configuration_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            ConfigItem(label = stringResource(R.string.label_version), value = versionKey)

            WheelSizeOverrideField(
                currentValue = wheelsize,
                onValueChange = { newWheelsize -> 
                    newWheelsize?.let { onConfigUpdate(it, poles) } 
                }
            )
            PolesOverrideField(
                currentValue = poles,
                onValueChange = { newPoles -> 
                    newPoles?.let { onConfigUpdate(wheelsize, it) } 
                }
            )

            if (lockedMacAddress != null) {
                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                LockedMacSection(macAddress = lockedMacAddress, onForgetDevice = onForgetDevice)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WheelSizeOverrideField(
    currentValue: Int,
    onValueChange: (Int?) -> Unit
) {
    var text by remember(currentValue) {
        mutableStateOf(currentValue.toString())
    }
    var expanded by remember { mutableStateOf(false) }
    val isError = text.toIntOrNull()?.let { it !in 1000..2500 } ?: true
    val focusManager = LocalFocusManager.current

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input
                input.toIntOrNull()?.takeIf { it in 1000..2500 }?.let(onValueChange)
            },
            label = { Text(stringResource(R.string.label_override_wheel_size)) },
            isError = isError,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    expanded = false
                }
            ),
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            WheelSizePreset.ALL.forEach { preset ->
                DropdownMenuItem(
                    text = { Text(preset.label) },
                    onClick = {
                        text = preset.circumferenceMm.toString()
                        onValueChange(preset.circumferenceMm)
                        expanded = false
                        focusManager.clearFocus()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PolesOverrideField(
    currentValue: Int,
    onValueChange: (Int?) -> Unit
) {
    var text by remember(currentValue) {
        mutableStateOf(currentValue.toString())
    }
    var expanded by remember { mutableStateOf(false) }
    val isError = text.toIntOrNull()?.let { it !in 1..60 } ?: true
    val focusManager = LocalFocusManager.current

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { input ->
                text = input
                input.toIntOrNull()?.takeIf { it in 1..60 }?.let(onValueChange)
            },
            label = { Text(stringResource(R.string.label_override_poles)) },
            isError = isError,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    focusManager.clearFocus()
                    expanded = false
                }
            ),
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DynamoPolePreset.ALL.forEach { preset ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                R.string.label_pole_preset_format,
                                preset.name,
                                preset.poles
                            )
                        )
                    },
                    onClick = {
                        text = preset.poles.toString()
                        onValueChange(preset.poles)
                        expanded = false
                        focusManager.clearFocus()
                    }
                )
            }
        }
    }
}

@Composable
private fun LockedMacSection(macAddress: String, onForgetDevice: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = stringResource(R.string.label_locked_device),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = macAddress,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Button(onClick = onForgetDevice) {
            Text(text = stringResource(R.string.forget_device_action))
        }
    }
}

@Composable
fun ConfigItem(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
