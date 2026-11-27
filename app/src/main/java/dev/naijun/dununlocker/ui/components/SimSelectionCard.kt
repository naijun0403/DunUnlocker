package dev.naijun.dununlocker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousCapsule
import dev.naijun.dununlocker.R
import dev.naijun.dununlocker.data.SimInfo
import dev.naijun.dununlocker.ui.theme.segmentedListItemShapes

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionHeader(
            title = stringResource(R.string.sim_card_label),
            action = {
                FilledTonalIconButton(onClick = onRefresh, enabled = !isLoading, shape = ContinuousCapsule) {
                    Icon(Icons.Default.Refresh, stringResource(R.string.sim_refresh))
                }
            }
        )
        when {
            isLoading -> Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surfaceBright,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LoadingIndicator(Modifier.size(40.dp))
                    Text(stringResource(R.string.sim_loading), style = MaterialTheme.typography.bodyLarge)
                }
            }
            loadFailed -> MessageCard(stringResource(R.string.sim_load_failed), isError = true)
            simList.isEmpty() -> MessageCard(stringResource(R.string.no_active_sim))
            else -> Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
                simList.forEachIndexed { index, sim ->
                    SimItem(
                        sim = sim,
                        selected = sim.subscriptionId == selectedSim?.subscriptionId,
                        index = index,
                        count = simList.size,
                        onClick = { onSimSelected(sim) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SimItem(
    sim: SimInfo,
    selected: Boolean,
    index: Int,
    count: Int,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    SegmentedListItem(
        selected = selected,
        onClick = onClick,
        shapes = segmentedListItemShapes(index, count),
        colors = ListItemDefaults.segmentedColors(
            containerColor = colors.surfaceBright,
            selectedContainerColor = colors.secondaryContainer,
        ),
        leadingContent = { SlotBadge(sim.slotIndex + 1, selected) },
        supportingContent = {
            Text(
                if (sim.displayName == sim.carrierName) {
                    stringResource(R.string.sim_default_name, sim.slotIndex + 1)
                } else {
                    stringResource(R.string.sim_slot_format, sim.slotIndex + 1, sim.carrierName)
                }
            )
        },
        trailingContent = { RadioButton(selected = selected, onClick = null) },
    ) {
        Text(sim.displayName, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SlotBadge(slot: Int, selected: Boolean) {
    val colors = MaterialTheme.colorScheme
    val container by animateColorAsState(
        if (selected) colors.primary else colors.surfaceContainerHighest,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "slotBadgeContainer",
    )
    val content by animateColorAsState(
        if (selected) colors.onPrimary else colors.onSurfaceVariant,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "slotBadgeContent",
    )
    val shape = MaterialShapes.Cookie4Sided.toShape()
    Box(
        modifier = Modifier.size(40.dp).background(container, shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(slot.toString(), style = MaterialTheme.typography.titleMediumEmphasized, color = content)
    }
}
