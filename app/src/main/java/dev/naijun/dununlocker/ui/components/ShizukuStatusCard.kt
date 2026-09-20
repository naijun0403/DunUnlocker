package dev.naijun.dununlocker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.naijun.dununlocker.R

@Composable
fun ShizukuStatusCard(
    isGranted: Boolean,
    isRunning: Boolean,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    if (isGranted && isRunning) {
        Row(
            modifier = modifier.padding(horizontal = 4.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Outlined.CheckCircle, null, Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.shizuku_granted_message),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Outlined.LinkOff, null)
                    Text(stringResource(R.string.shizuku_status_title),
                        style = MaterialTheme.typography.titleMedium)
                }
                Text(stringResource(if (isRunning) R.string.shizuku_running_message
                    else R.string.shizuku_not_running_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FilledTonalButton(
                    onClick = {
                        if (isRunning) onRequestPermission()
                        else uriHandler.openUri("https://shizuku.rikka.app/")
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(if (isRunning) R.string.shizuku_request_button
                        else R.string.shizuku_install_button))
                }
            }
        }
    }
}
