package dev.naijun.dununlocker.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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
    modifier: Modifier = Modifier
) {
    var apns by remember(subscriptionId) { mutableStateOf<List<ApnSummary>>(emptyList()) }
    var loading by remember(subscriptionId) { mutableStateOf(subscriptionId != null) }
    var failed by remember(subscriptionId) { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var showPicker by remember(subscriptionId) { mutableStateOf(false) }

    LaunchedEffect(subscriptionId, refresh, refreshKey) {
        apns = emptyList()
        failed = false
        if (subscriptionId == null) {
            loading = false
            onSelected(null)
            return@LaunchedEffect
        }
        loading = true
        val result = loadApns(subscriptionId)
        result.onSuccess { rows ->
            apns = rows.sortedByDescending { it.isPreferred }
            if (rows.none { it.id == selectedId }) {
                onSelected(rows.firstOrNull { it.isPreferred }?.id)
            }
        }.onFailure {
            failed = true
            onSelected(null)
        }
        loading = false
    }
    val selected = apns.firstOrNull { it.id == selectedId }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth().animateContentSize(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.apn_source_title),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { refresh++ },
                        enabled = subscriptionId != null && !loading
                    ) {
                        Icon(Icons.Default.Refresh, stringResource(R.string.apn_list_refresh))
                    }
                }
                Text(stringResource(R.string.apn_copy_description), style = MaterialTheme.typography.bodyMedium)
                when {
                    subscriptionId == null -> Text(stringResource(R.string.sim_select_placeholder))
                    loading -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(stringResource(R.string.apn_list_loading))
                    }
                    failed -> {
                        Text(stringResource(R.string.apn_list_error), color = MaterialTheme.colorScheme.error)
                        OutlinedButton(onClick = { refresh++ }) {
                            Text(stringResource(R.string.apn_list_retry))
                        }
                    }
                    apns.isEmpty() -> Text(stringResource(R.string.apn_list_empty))
                    else -> {
                        OutlinedCard(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (selected == null) {
                                    Text(stringResource(R.string.apn_source_choose))
                                } else {
                                    ApnSummaryText(selected)
                                }
                                Text(
                                    stringResource(R.string.apn_source_choose_count, apns.size),
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = { selected?.let(onCopy) },
            enabled = selected != null && !loading && !failed,
            modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.copy_apn_button))
        }
    }

    if (showPicker && !loading && !failed) {
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.apn_source_choose)) },
            text = {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 440.dp).selectableGroup()
                ) {
                    items(apns, key = { it.id }) { apn ->
                        Row(
                            modifier = Modifier.fillMaxWidth().selectable(
                                selected = apn.id == selectedId,
                                role = Role.RadioButton,
                                onClick = {
                                    onSelected(apn.id)
                                    showPicker = false
                                }
                            ).padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = apn.id == selectedId, onClick = null)
                            Column(Modifier.weight(1f)) { ApnSummaryText(apn) }
                        }
                        HorizontalDivider()
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.cancel_button)) }
            }
        )
    }
}

@Composable
private fun ApnSummaryText(apn: ApnSummary) {
    Text(
        apn.name.ifBlank { apn.apn },
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Medium
    )
    if (apn.isPreferred) {
        Text(
            stringResource(R.string.apn_current_default),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelMedium
        )
    }
    Text(apn.apn, style = MaterialTheme.typography.bodyMedium)
    Text(
        stringResource(R.string.apn_source_types, apn.type.ifBlank { "*" }),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
