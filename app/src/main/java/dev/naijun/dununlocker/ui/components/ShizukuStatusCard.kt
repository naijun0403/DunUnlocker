package dev.naijun.dununlocker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.naijun.dununlocker.R

private enum class ShizukuState { NotRunning, NeedsPermission, Connected }

@Composable
fun ShizukuStatusCard(
    isGranted: Boolean,
    isRunning: Boolean,
    usesCarrierPrivilegeMode: Boolean = false,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uriHandler = LocalUriHandler.current
    val state = when {
        isGranted && isRunning -> ShizukuState.Connected
        isRunning -> ShizukuState.NeedsPermission
        else -> ShizukuState.NotRunning
    }
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = state,
        modifier = modifier.fillMaxWidth(),
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                .using(SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() })
        },
        contentAlignment = Alignment.TopStart,
        label = "shizukuStatus",
    ) { current ->
        when (current) {
            ShizukuState.Connected -> ConnectedStatus(usesCarrierPrivilegeMode)
            ShizukuState.NeedsPermission -> SetupCard(
                icon = { Icon(Icons.Outlined.Key, contentDescription = null) },
                message = stringResource(R.string.shizuku_running_message),
                actionLabel = stringResource(R.string.shizuku_request_button),
                onAction = onRequestPermission,
            )
            ShizukuState.NotRunning -> SetupCard(
                icon = { Icon(Icons.Outlined.PowerSettingsNew, contentDescription = null) },
                message = stringResource(R.string.shizuku_not_running_message),
                actionLabel = stringResource(R.string.shizuku_install_button),
                onAction = { uriHandler.openUri("https://shizuku.rikka.app/") },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectedStatus(usesCarrierPrivilegeMode: Boolean) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatusPill(
            text = stringResource(R.string.shizuku_granted_message),
            icon = Icons.Outlined.CheckCircle,
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        if (usesCarrierPrivilegeMode) {
            StatusPill(
                text = stringResource(R.string.carrier_config_mode_label),
                icon = Icons.Outlined.SimCard,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SetupCard(
    icon: @Composable () -> Unit,
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val iconShape = MaterialShapes.Cookie9Sided.toShape()
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(MaterialTheme.colorScheme.primary, iconShape),
                contentAlignment = Alignment.Center,
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides MaterialTheme.colorScheme.onPrimary,
                    content = icon,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    stringResource(R.string.shizuku_status_title),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )
                Text(message, style = MaterialTheme.typography.bodyLarge)
            }
            ExpressiveButton(onClick = onAction, modifier = Modifier.fillMaxWidth()) {
                ButtonLabel(icon = null, text = actionLabel)
            }
        }
    }
}
