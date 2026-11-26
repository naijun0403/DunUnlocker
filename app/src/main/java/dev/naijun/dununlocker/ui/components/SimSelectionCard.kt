package dev.naijun.dununlocker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.SimCard
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.naijun.dununlocker.R
import dev.naijun.dununlocker.data.SimInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimSelectionCard(
    simList: List<SimInfo>,
    selectedSim: SimInfo?,
    onSimSelected: (SimInfo?) -> Unit,
    isLoading: Boolean,
    loadFailed: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = MaterialTheme.shapes.large,
            color = containerColor
        ) {
            if (simList.size <= 1) {
                val sim = simList.firstOrNull()
                ListItem(
                    headlineContent = {
                        Text(when {
                            isLoading -> stringResource(R.string.sim_loading)
                            loadFailed -> stringResource(R.string.sim_load_failed)
                            else -> sim?.displayName ?: stringResource(R.string.no_active_sim)
                        })
                    },
                    supportingContent = if (sim == null) null else {
                        {
                            Text(
                                if (sim.displayName == sim.carrierName)
                                    stringResource(R.string.sim_default_name, sim.slotIndex + 1)
                                else stringResource(R.string.sim_slot_format, sim.slotIndex + 1, sim.carrierName)
                            )
                        }
                    },
                    leadingContent = { Icon(Icons.Outlined.SimCard, null) },
                    colors = ListItemDefaults.colors(containerColor = containerColor)
                )
            } else {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                    modifier = Modifier.padding(16.dp)
                ) {
                    OutlinedTextField(
                        value = selectedSim?.displayName.orEmpty(),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.sim_card_label)) },
                        placeholder = { Text(stringResource(R.string.sim_select_placeholder)) },
                        leadingIcon = { Icon(Icons.Outlined.SimCard, null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                        modifier = Modifier.fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                        simList.forEach { sim ->
                            DropdownMenuItem(
                                text = {
                                    Column {
                                        Text(sim.displayName)
                                        Text(stringResource(R.string.sim_slot_format, sim.slotIndex + 1, sim.carrierName),
                                            style = MaterialTheme.typography.bodySmall)
                                    }
                                },
                                onClick = { onSimSelected(sim); expanded = false }
                            )
                        }
                    }
                }
            }
        }
        IconButton(onClick = onRefresh, enabled = !isLoading) {
            Icon(Icons.Default.Refresh, stringResource(R.string.sim_refresh))
        }
    }
}
