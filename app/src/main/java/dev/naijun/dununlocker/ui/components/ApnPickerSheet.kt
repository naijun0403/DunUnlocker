package dev.naijun.dununlocker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.naijun.dununlocker.R
import dev.naijun.dununlocker.data.ApnSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ApnPickerSheet(
    apns: List<ApnSummary>,
    selectedId: Long?,
    onSelected: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val windowHeight = LocalWindowInfo.current.containerSize.height
    val maxHeight = with(LocalDensity.current) { windowHeight.toDp() * 0.85f }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
            Text(
                stringResource(R.string.apn_source_choose),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 20.dp),
                style = MaterialTheme.typography.headlineSmall
            )
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false).selectableGroup(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(apns, key = { _, apn -> apn.id }) { index, apn ->
                    val isSelected = apn.id == selectedId
                    val contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    Surface(
                        shape = RoundedCornerShape(
                            topStart = if (index == 0) 20.dp else 4.dp,
                            topEnd = if (index == 0) 20.dp else 4.dp,
                            bottomStart = if (index == apns.lastIndex) 20.dp else 4.dp,
                            bottomEnd = if (index == apns.lastIndex) 20.dp else 4.dp
                        ),
                        color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                            else MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = contentColor
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().selectable(
                                selected = isSelected,
                                role = Role.RadioButton,
                                onClick = {
                                    onSelected(apn.id)
                                }
                            ).padding(horizontal = 20.dp, vertical = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(apn.name.ifBlank { apn.apn },
                                    style = MaterialTheme.typography.titleMedium)
                                if (apn.apn.isNotBlank()) {
                                    Text(apn.apn, style = MaterialTheme.typography.bodyMedium)
                                }
                                Text(apn.type.ifBlank { "*" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isSelected) contentColor
                                        else MaterialTheme.colorScheme.onSurfaceVariant)
                                if (apn.isPreferred) {
                                    Text(stringResource(R.string.apn_current_default),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) contentColor else MaterialTheme.colorScheme.primary)
                                }
                            }
                            if (isSelected) Icon(Icons.Default.Check, null, Modifier.size(24.dp))
                            else Spacer(Modifier.size(24.dp))
                        }
                    }
                }
            }
        }
    }
}
