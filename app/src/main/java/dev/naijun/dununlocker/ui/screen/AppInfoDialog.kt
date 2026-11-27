package dev.naijun.dununlocker.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.LocalCafe
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.WifiTethering
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.kyant.capsule.ContinuousRoundedRectangle
import dev.naijun.dununlocker.R
import dev.naijun.dununlocker.ui.components.ButtonEmphasis
import dev.naijun.dununlocker.ui.components.ExpressiveButton
import dev.naijun.dununlocker.ui.components.StatusPill
import dev.naijun.dununlocker.ui.theme.segmentedListItemShapes

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun AppInfoDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val githubUrl = stringResource(R.string.app_info_github_url)
    val supportUrl = stringResource(R.string.app_info_support_url)
    val versionName = remember {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } ?: stringResource(R.string.unknown_version)
    val title = stringResource(R.string.app_info_title)
    val colors = MaterialTheme.colorScheme

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().semantics { paneTitle = title },
            shape = MaterialTheme.shapes.extraLargeIncreased,
            color = colors.surfaceContainerHigh,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .background(colors.primaryContainer, MaterialShapes.Cookie12Sided.toShape()),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.WifiTethering,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = colors.onPrimaryContainer,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )
                Spacer(Modifier.height(8.dp))
                StatusPill(
                    text = "${stringResource(R.string.app_info_version)} $versionName",
                    containerColor = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.app_info_description),
                    modifier = Modifier.padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(24.dp))

                Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                    InfoItem(
                        index = 0,
                        icon = Icons.Outlined.Person,
                        headline = stringResource(R.string.app_info_developer),
                        supporting = stringResource(R.string.app_info_developer_name),
                    )
                    InfoItem(
                        index = 1,
                        icon = Icons.Outlined.Code,
                        headline = stringResource(R.string.app_info_github),
                        supporting = githubUrl,
                        onClick = { uriHandler.openUri("https://$githubUrl") },
                    )
                    InfoItem(
                        index = 2,
                        icon = Icons.Outlined.LocalCafe,
                        headline = stringResource(R.string.app_info_support),
                        supporting = stringResource(R.string.app_info_support_description),
                        onClick = { uriHandler.openUri("https://$supportUrl") },
                    )
                    InfoItem(
                        index = 3,
                        icon = Icons.Outlined.Gavel,
                        headline = stringResource(R.string.app_info_license),
                        supporting = stringResource(R.string.app_info_license_type),
                    )
                }

                Spacer(Modifier.height(24.dp))
                ExpressiveButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    emphasis = ButtonEmphasis.Tonal,
                    minHeight = 48.dp,
                ) {
                    Text(stringResource(R.string.app_info_close), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
}

private const val InfoItemCount = 4

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun InfoItem(
    index: Int,
    icon: ImageVector,
    headline: String,
    supporting: String,
    onClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val shapes = segmentedListItemShapes(index, InfoItemCount)
    val itemColors = ListItemDefaults.segmentedColors(containerColor = colors.surfaceBright)
    val leading: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(colors.secondaryContainer, ContinuousRoundedRectangle(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.onSecondaryContainer, modifier = Modifier.size(22.dp))
        }
    }
    val supportingContent: @Composable () -> Unit = { Text(supporting) }
    val content: @Composable () -> Unit = {
        Text(headline, style = MaterialTheme.typography.titleMedium)
    }

    if (onClick != null) {
        SegmentedListItem(
            onClick = onClick,
            shapes = shapes,
            colors = itemColors,
            leadingContent = leading,
            supportingContent = supportingContent,
            trailingContent = {
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = colors.onSurfaceVariant,
                )
            },
            content = content,
        )
    } else {
        SegmentedListItem(
            shapes = shapes,
            colors = itemColors,
            leadingContent = leading,
            supportingContent = supportingContent,
            content = content,
        )
    }
}
