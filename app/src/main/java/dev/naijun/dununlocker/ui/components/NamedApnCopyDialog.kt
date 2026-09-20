package dev.naijun.dununlocker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.naijun.dununlocker.R
import dev.naijun.dununlocker.data.ApnSummary

@Composable
fun NamedApnCopyDialog(
    apn: ApnSummary,
    simLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val suggestedName = stringResource(R.string.apn_copy_name_default, apn.name.ifBlank { apn.apn })
    var name by rememberSaveable(apn.id) { mutableStateOf(suggestedName) }
    val valid = name.trim().isNotEmpty() && name.trim() != apn.name
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.apn_named_copy_button)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.apn_named_copy_description),
                    style = MaterialTheme.typography.bodyMedium)
                Text(simLabel, style = MaterialTheme.typography.labelLarge)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.apn_copy_name_label)) },
                    singleLine = true,
                    isError = !valid,
                    supportingText = if (!valid) {
                        { Text(stringResource(R.string.apn_copy_name_error)) }
                    } else null,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(apn.apn, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim()) }, enabled = valid) {
                Text(stringResource(R.string.apn_named_copy_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) }
        }
    )
}