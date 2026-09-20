package dev.naijun.dununlocker.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.naijun.dununlocker.R
import dev.naijun.dununlocker.data.ApnSummary
import dev.naijun.dununlocker.domain.model.CarrierType
import dev.naijun.dununlocker.ui.model.ApnFormState
import dev.naijun.dununlocker.ui.model.labelRes
import kotlinx.coroutines.launch

private enum class ConfigurationMode(@param:StringRes val label: Int) {
    Existing(R.string.apn_mode_copy),
    Manual(R.string.apn_mode_manual)
}

@Composable
fun ApnConfigSection(
    subscriptionId: Int?,
    refreshKey: Int,
    loadApns: suspend (Int) -> Result<List<ApnSummary>>,
    onCopyApnClicked: (ApnSummary) -> Unit,
    onNamedCopyApnClicked: (ApnSummary) -> Unit,
    onApplyClicked: (ApnFormState) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCopyId by rememberSaveable(subscriptionId) { mutableStateOf<Long?>(null) }
    var mode by rememberSaveable { mutableStateOf(ConfigurationMode.Existing) }
    var form by remember { mutableStateOf<ApnFormState?>(null) }
    val scope = rememberCoroutineScope()
    val modeAnchor = remember { BringIntoViewRequester() }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.apn_settings_title), style = MaterialTheme.typography.titleLarge)
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().bringIntoViewRequester(modeAnchor)
        ) {
            ConfigurationMode.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = mode == option,
                    onClick = {
                        scope.launch {
                            modeAnchor.bringIntoView()
                            mode = option
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, ConfigurationMode.entries.size)
                ) { Text(stringResource(option.label)) }
            }
        }
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (fadeIn(tween(180, 90)) togetherWith fadeOut(tween(120)))
                    .using(SizeTransform { _, _ -> spring(dampingRatio = 0.85f, stiffness = 400f) })
            },
            contentAlignment = Alignment.TopStart,
            label = "apnConfigurationMode",
            modifier = Modifier.fillMaxWidth()
        ) { currentMode ->
            when (currentMode) {
                ConfigurationMode.Existing -> ExistingApnCard(
                    subscriptionId = subscriptionId,
                    refreshKey = refreshKey,
                    selectedId = selectedCopyId,
                    onSelected = { selectedCopyId = it },
                    loadApns = loadApns,
                    onCopy = onCopyApnClicked,
                    onNamedCopy = onNamedCopyApnClicked
                )
                ConfigurationMode.Manual -> ManualApnForm(
                    form = form,
                    onFormChange = { form = it },
                    onApply = onApplyClicked
                )
            }
        }
    }
}

@Composable
private fun ManualApnForm(
    form: ApnFormState?,
    onFormChange: (ApnFormState) -> Unit,
    onApply: (ApnFormState) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.carrier_selection),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ApnDropdown(
                value = form?.carrier,
                label = R.string.carrier_label,
                options = CarrierType.entries,
                optionLabel = { stringResource(it.labelRes) },
                onSelected = { carrier ->
                    if (carrier != form?.carrier) onFormChange(ApnFormState.forCarrier(carrier))
                }
            )
        }
        AnimatedVisibility(form != null) {
            form?.let { current ->
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    ApnFormFields(current, onFormChange)
                    Button(
                        onClick = { onApply(current) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)
                    ) {
                        Icon(Icons.Default.Check, null, Modifier.size(24.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(stringResource(R.string.apply_button), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun ApnFormFields(form: ApnFormState, onChange: (ApnFormState) -> Unit) {
    val content = form.content
    val authLabels = listOf(
        R.string.auth_type_value_none, R.string.auth_type_value_pap,
        R.string.auth_type_value_chap, R.string.auth_type_value_pap_or_chap
    )
    val protocols = listOf("IPV4V6", "IPV4", "IPV6")
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (form.carrier == CarrierType.CUSTOM) {
            Text(
                stringResource(R.string.carrier_custom_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        FormSectionTitle(R.string.basic_settings)
        ApnTextField(content.name, { onChange(form.copy(content = content.copy(name = it))) }, R.string.apn_name_label)
        ApnTextField(content.apn, { onChange(form.copy(content = content.copy(apn = it))) },
            R.string.apn_address_label, keyboardType = KeyboardType.Uri)
        ApnTextField(content.type, { onChange(form.copy(content = content.copy(type = it))) },
            R.string.apn_type_label, hint = R.string.apn_type_hint)

        FormSectionTitle(R.string.network_settings)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ApnTextField(content.mcc, { onChange(form.copy(content = content.copy(mcc = it))) },
                R.string.mcc_label, Modifier.weight(1f), KeyboardType.Number, R.string.mcc_hint)
            ApnTextField(content.mnc, { onChange(form.copy(content = content.copy(mnc = it))) },
                R.string.mnc_label, Modifier.weight(1f), KeyboardType.Number, R.string.mnc_hint)
        }
        ApnDropdown(
            value = content.authType,
            label = R.string.auth_type_label,
            options = listOf("0", "1", "2", "3"),
            optionLabel = { stringResource(authLabels[it.toInt()]) },
            onSelected = { onChange(form.copy(content = content.copy(authType = it))) }
        )
        ApnDropdown(content.protocol, R.string.protocol_label, protocols, { it },
            { onChange(form.copy(content = content.copy(protocol = it))) })
        ApnDropdown(content.roamingProtocol, R.string.roaming_protocol_label, protocols, { it },
            { onChange(form.copy(content = content.copy(roamingProtocol = it))) })

        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Row(
                modifier = Modifier.fillMaxWidth()
                    .toggleable(
                        value = form.useMmsSettings,
                        role = Role.Switch,
                        onValueChange = { onChange(form.copy(useMmsSettings = it)) }
                    ).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(stringResource(R.string.mms_settings_enable), Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge)
                Switch(checked = form.useMmsSettings, onCheckedChange = null)
            }
        }
        AnimatedVisibility(form.useMmsSettings) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                ApnTextField(content.mmsc, { onChange(form.copy(content = content.copy(mmsc = it))) },
                    R.string.mmsc_label, keyboardType = KeyboardType.Uri)
                ApnTextField(content.mmsProxy, { onChange(form.copy(content = content.copy(mmsProxy = it))) },
                    R.string.mms_proxy_label, keyboardType = KeyboardType.Uri)
                ApnTextField(content.mmsPort, { onChange(form.copy(content = content.copy(mmsPort = it))) },
                    R.string.mms_port_label, keyboardType = KeyboardType.Number)
            }
        }
    }
}

@Composable
private fun FormSectionTitle(@StringRes label: Int) {
    Text(stringResource(label), Modifier.padding(top = 8.dp),
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun ApnTextField(
    value: String,
    onValueChange: (String) -> Unit,
    @StringRes label: Int,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    @StringRes hint: Int? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        supportingText = hint?.let { { Text(stringResource(it)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T : Any> ApnDropdown(
    value: T?,
    @StringRes label: Int,
    options: List<T>,
    optionLabel: @Composable (T) -> String,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = value?.let { optionLabel(it) }.orEmpty(),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(label)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
