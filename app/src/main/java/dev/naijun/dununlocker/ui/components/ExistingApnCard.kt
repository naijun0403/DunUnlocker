package dev.naijun.dununlocker.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.naijun.dununlocker.R
import dev.naijun.dununlocker.data.ApnSummary

@Composable
fun ExistingApnCard(
    subscriptionId: Int?,
    refreshKey: Int,
    selectedId: Long?,
    onSelected: (Long?) -> Unit,
    loadApns: suspend (Int) -> Result<List<ApnSummary>>,
    onCopy: (ApnSummary) -> Unit,
    onNamedCopy: (ApnSummary) -> Unit,
    modifier: Modifier = Modifier
) {
    var state by remember(subscriptionId) {
        mutableStateOf(if (subscriptionId == null) ApnListState.NoSim else ApnListState.Loading)
    }
    var refresh by remember { mutableIntStateOf(0) }
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
        state = ApnListState.Loading
        state = load(subscriptionId).fold(
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

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.apn_source_title),
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(onClick = { refresh++ }, enabled = subscriptionId != null && state != ApnListState.Loading) {
                Icon(Icons.Default.Refresh, stringResource(R.string.apn_list_refresh))
            }
        }
        when {
            state == ApnListState.NoSim -> Text(stringResource(R.string.sim_select_placeholder))
            state == ApnListState.Loading -> {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.apn_list_loading),
                    style = MaterialTheme.typography.bodyMedium)
            }
            state == ApnListState.Failed -> {
                Text(stringResource(R.string.apn_list_error), color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = { refresh++ }) {
                    Text(stringResource(R.string.apn_list_retry))
                }
            }
            apns.isEmpty() -> Text(stringResource(R.string.apn_list_empty))
            else -> {
                Card(
                    onClick = { showPicker = true },
                    modifier = Modifier.fillMaxWidth().animateContentSize(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (selected == null) {
                            Text(stringResource(R.string.apn_source_choose),
                                style = MaterialTheme.typography.titleLarge)
                        } else {
                            ApnSummaryText(selected)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.apn_source_choose_count, apns.size),
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.labelLarge)
                            Icon(Icons.Default.ExpandMore, null, Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
        Text(
            stringResource(R.string.apn_copy_description),
            modifier = Modifier.padding(horizontal = 4.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = { selected?.let(onCopy) },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp)
        ) {
            Text(stringResource(R.string.copy_apn_button), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.width(12.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(20.dp))
        }
        OutlinedButton(
            onClick = { selected?.let(onNamedCopy) },
            enabled = selected != null,
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
        ) {
            Text(stringResource(R.string.apn_named_copy_button))
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

@Composable
private fun ApnSummaryText(apn: ApnSummary) {
    val detailColor = MaterialTheme.colorScheme.onSurfaceVariant
    if (apn.isPreferred) {
        Text(
            stringResource(R.string.apn_current_default),
            color = detailColor,
            style = MaterialTheme.typography.labelMedium
        )
    }
    Text(
        apn.name.ifBlank { apn.apn },
        style = MaterialTheme.typography.titleLarge
    )
    if (apn.apn.isNotBlank()) {
        Text(apn.apn, style = MaterialTheme.typography.bodyMedium, color = detailColor)
    }
    Text(
        stringResource(R.string.apn_source_types, apn.type.ifBlank { "*" }),
        style = MaterialTheme.typography.bodySmall,
        color = detailColor
    )
}

private sealed interface ApnListState {
    data object NoSim : ApnListState
    data object Loading : ApnListState
    data object Failed : ApnListState
    data class Ready(val apns: List<ApnSummary>) : ApnListState
}
