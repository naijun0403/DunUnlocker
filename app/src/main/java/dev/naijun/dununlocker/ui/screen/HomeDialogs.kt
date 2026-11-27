package dev.naijun.dununlocker.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.naijun.dununlocker.R
import dev.naijun.dununlocker.domain.model.CarrierType
import dev.naijun.dununlocker.ui.components.NamedApnCopyDialog
import dev.naijun.dununlocker.ui.model.ApnRequest
import dev.naijun.dununlocker.ui.model.labelRes

@Composable
internal fun ApnApplyConfirmDialog(
    request: ApnRequest,
    onConfirm: (ApnRequest) -> Unit,
    onDismiss: () -> Unit
) {
    val message = when (request) {
        is ApnRequest.NamedCopy -> {
            NamedApnCopyDialog(
                apn = request.apn,
                simLabel = stringResource(R.string.sim_slot_format, request.sim.slotIndex + 1, request.sim.displayName),
                onConfirm = { onConfirm(request.copy(name = it)) },
                onDismiss = onDismiss
            )
            return
        }
        is ApnRequest.AddDun -> stringResource(
            R.string.copy_apn_confirm_message,
            request.apn.name.ifBlank { request.apn.apn },
            request.apn.apn,
            request.apn.type.ifBlank { "*" }
        )
        is ApnRequest.Configure -> if (request.form.carrier == CarrierType.CUSTOM) {
            stringResource(R.string.confirm_dialog_message_custom)
        } else {
            stringResource(R.string.confirm_dialog_message, stringResource(request.form.carrier.labelRes))
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.confirm_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(message)
                request.sim?.let { sim ->
                    Text(stringResource(R.string.sim_slot_format, sim.slotIndex + 1, sim.displayName),
                        style = MaterialTheme.typography.labelLarge)
                }
                Text(stringResource(R.string.confirm_dialog_notice_content),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(request) }) { Text(stringResource(R.string.confirm_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel_button)) }
        }
    )
}
