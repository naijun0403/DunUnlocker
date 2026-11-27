package dev.naijun.dununlocker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.naijun.dununlocker.R
import dev.naijun.dununlocker.data.ApnSummary
import dev.naijun.dununlocker.ui.theme.segmentedListItemShapes

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun ApnPickerSheet(
    apns: List<ApnSummary>,
    selectedId: Long?,
    onSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val maxHeight = with(LocalDensity.current) { windowHeight.toDp() * 0.85f }
    val colors = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainer
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
            Text(
                stringResource(R.string.apn_source_choose),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 16.dp),
                style = MaterialTheme.typography.headlineSmallEmphasized
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).selectableGroup(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)
            ) {
                itemsIndexed(apns, key = { _, apn -> apn.id }) { index, apn ->
                    val isSelected = apn.id == selectedId
                    SegmentedListItem(
                        selected = isSelected,
                        onClick = { onSelected(apn.id) },
                        shapes = segmentedListItemShapes(index, apns.size),
                        colors = ListItemDefaults.segmentedColors(
                            containerColor = colors.surfaceBright,
                            selectedContainerColor = colors.secondaryContainer,
                        ),
                        overlineContent = if (apn.isPreferred) {
                            { Text(stringResource(R.string.apn_current_default)) }
                        } else null,
                        supportingContent = {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (apn.apn.isNotBlank()) Text(apn.apn)
                                ApnTypeChips(apn.type)
                            }
                        },
                        trailingContent = { RadioButton(selected = isSelected, onClick = null) },
                    ) {
                        Text(apn.name.ifBlank { apn.apn }, style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}
