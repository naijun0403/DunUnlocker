package dev.naijun.dununlocker.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousCapsule
import dev.naijun.dununlocker.R
import dev.naijun.dununlocker.data.ApnSummary

@Composable
fun ExistingApnCard(
    subscriptionId: Int?,
    refreshKey: Int,
    selectedId: Long?,
    onSelected: (Long?) -> Unit,
    loadApns: suspend (Int, Boolean) -> Result<List<ApnSummary>>,
    onCopy: (ApnSummary) -> Unit,
    onNamedCopy: (ApnSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    var state by remember(subscriptionId) {
        mutableStateOf(if (subscriptionId == null) ApnListState.NoSim else ApnListState.Loading)
    }
    var refresh by remember { mutableIntStateOf(0) }
    var lastRefresh by remember { mutableIntStateOf(0) }
    var showPicker by remember(subscriptionId) { mutableStateOf(false) }
    val currentSelection by rememberUpdatedState(selectedId)
    val onSelectionChange by rememberUpdatedState(onSelected)
    val load by rememberUpdatedState(loadApns)

    LaunchedEffect(subscriptionId, refresh, refreshKey) {
        showPicker = false
        if (subscriptionId == null) {
            state = ApnListState.NoSim
            onSelectionChange(null)
            return@LaunchedEffect
        }
        val forceRefresh = refresh != lastRefresh
        lastRefresh = refresh
        state = ApnListState.Loading
        state = load(subscriptionId, forceRefresh).fold(
            onSuccess = { rows ->
                if (rows.none { it.id == currentSelection }) {
                    onSelectionChange(rows.firstOrNull { it.isPreferred }?.id)
                }
                ApnListState.Ready(rows.sortedByDescending { it.isPreferred })
            },
            onFailure = {
                onSelectionChange(null)
                ApnListState.Failed
            }
        )
    }
    val apns = (state as? ApnListState.Ready)?.apns.orEmpty()
    val selected = apns.firstOrNull { it.id == selectedId }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.apn_source_title),
                modifier = Modifier.weight(1f).padding(start = 4.dp),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = { refresh++ }, enabled = subscriptionId != null && state != ApnListState.Loading) {
                Icon(Icons.Default.Refresh, stringResource(R.string.apn_list_refresh))
            }
        }
        when {
            state == ApnListState.NoSim -> MessageCard(stringResource(R.string.sim_select_placeholder))
            state == ApnListState.Loading -> LoadingCard()
            state == ApnListState.Failed -> MessageCard(
                text = stringResource(R.string.apn_list_error),
                isError = true,
                action = {
                    ExpressiveButton(
                        onClick = { refresh++ },
                        emphasis = ButtonEmphasis.Tonal,
                        minHeight = 48.dp,
                    ) {
                        ButtonLabel(Icons.Default.Refresh, stringResource(R.string.apn_list_retry))
                    }
                }
            )
            apns.isEmpty() -> MessageCard(stringResource(R.string.apn_list_empty))
            else -> SelectedApnCard(
                selected = selected,
                count = apns.size,
                onClick = { showPicker = true },
            )
        }
        Text(
            stringResource(R.string.apn_copy_description),
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        ExpressiveButton(
            onClick = { selected?.let(onCopy) },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth(),
        ) {
            ButtonLabel(Icons.AutoMirrored.Filled.ArrowForward, stringResource(R.string.copy_apn_button))
        }
        ExpressiveButton(
            onClick = { selected?.let(onNamedCopy) },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth(),
            emphasis = ButtonEmphasis.Outlined,
            minHeight = 48.dp,
        ) {
            ButtonLabel(Icons.Outlined.Edit, stringResource(R.string.apn_named_copy_button))
        }
    }

    if (showPicker && state is ApnListState.Ready) {
        ApnPickerSheet(
            apns = apns,
            selectedId = selectedId,
            onSelected = {
                onSelected(it)
                showPicker = false
            },
            onDismiss = { showPicker = false }
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LoadingIndicator(Modifier.size(48.dp))
            Text(stringResource(R.string.apn_list_loading), style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SelectedApnCard(
    selected: ApnSummary?,
    count: Int,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        shape = MaterialTheme.shapes.extraLargeIncreased,
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (selected == null) {
                Text(
                    stringResource(R.string.apn_source_choose),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                )
            } else {
                ApnSummaryContent(selected)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.apn_source_choose_count, count),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    Icons.Outlined.UnfoldMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ApnSummaryContent(apn: ApnSummary) {
    if (apn.isPreferred) {
        StatusPill(
            text = stringResource(R.string.apn_current_default),
            icon = Icons.Outlined.Star,
            modifier = Modifier.padding(bottom = 4.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
    Text(
        apn.name.ifBlank { apn.apn },
        style = MaterialTheme.typography.headlineSmallEmphasized,
    )
    if (apn.apn.isNotBlank()) {
        Text(
            apn.apn,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    ApnTypeChips(apn.type, Modifier.padding(top = 4.dp))
}

/** APN types as small capsules, with DUN highlighted since it is what tethering needs. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ApnTypeChips(types: String, modifier: Modifier = Modifier) {
    val entries = types.split(",").map(String::trim).filter(String::isNotEmpty).ifEmpty { listOf("*") }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        entries.forEach { type ->
            val isDun = type.equals("dun", ignoreCase = true)
            Surface(
                shape = ContinuousCapsule,
                color = if (isDun) MaterialTheme.colorScheme.tertiaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (isDun) MaterialTheme.colorScheme.onTertiaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    type,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

private sealed interface ApnListState {
    data object NoSim : ApnListState
    data object Loading : ApnListState
    data object Failed : ApnListState
    data class Ready(val apns: List<ApnSummary>) : ApnListState
}
