package dev.naijun.dununlocker.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.naijun.dununlocker.R
import dev.naijun.dununlocker.domain.model.CarrierType
import dev.naijun.dununlocker.ui.components.NamedApnCopyDialog
import dev.naijun.dununlocker.ui.model.ApnRequest
import dev.naijun.dununlocker.ui.model.labelRes

@Composable
internal fun AppInfoDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val githubUrl = stringResource(R.string.app_info_github_url)
    val supportUrl = stringResource(R.string.app_info_support_url)
    val unknownVersion = stringResource(R.string.unknown_version)
    val packageInfo = remember {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val versionName = packageInfo.versionName ?: unknownVersion

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.app_info_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Description
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainer
                ) {
                    Text(
                        text = stringResource(R.string.app_info_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                HorizontalDivider()

                // Version
                InfoRow(
                    label = stringResource(R.string.app_info_version),
                    value = versionName
                )

                // Developer
                InfoRow(
                    label = stringResource(R.string.app_info_developer),
                    value = stringResource(R.string.app_info_developer_name)
                )

                // GitHub
                AppInfoLink(
                    title = stringResource(R.string.app_info_github),
                    detail = githubUrl,
                    onClick = { uriHandler.openUri("https://$githubUrl") }
                )

                // Support
                AppInfoLink(
                    title = stringResource(R.string.app_info_support),
                    detail = stringResource(R.string.app_info_support_description),
                    onClick = { uriHandler.openUri("https://$supportUrl") }
                )

                // License
                InfoRow(
                    label = stringResource(R.string.app_info_license),
                    value = stringResource(R.string.app_info_license_type)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.app_info_close))
            }
        }
    )
}

@Composable
private fun AppInfoLink(
    title: String,
    detail: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(label, modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, modifier = Modifier.weight(1.4f),
            textAlign = TextAlign.End,
            style = MaterialTheme.typography.bodyMedium)
    }
}

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
