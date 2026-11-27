package dev.naijun.dununlocker.ui.components

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalToggleButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonShapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kyant.capsule.ContinuousCapsule
import dev.naijun.dununlocker.R
import dev.naijun.dununlocker.data.ApnSummary
import dev.naijun.dununlocker.domain.model.CarrierType
import dev.naijun.dununlocker.ui.model.ApnFormState
import dev.naijun.dununlocker.ui.model.labelRes
import dev.naijun.dununlocker.ui.theme.animateContinuousShape
import dev.naijun.dununlocker.ui.theme.connectedButtonShape
import kotlinx.coroutines.launch

private enum class ConfigurationMode(@param:StringRes val label: Int, val icon: ImageVector) {
    Existing(R.string.apn_mode_copy, Icons.Outlined.ContentCopy),
    Manual(R.string.apn_mode_manual, Icons.Outlined.EditNote)
}

@Composable
fun ApnConfigSection(
    subscriptionId: Int?,
    refreshKey: Int,
    loadApns: suspend (Int, Boolean) -> Result<List<ApnSummary>>,
    onCopyApnClicked: (ApnSummary) -> Unit,
    onNamedCopyApnClicked: (ApnSummary) -> Unit,
    onApplyClicked: (ApnFormState) -> Unit,
    modifier: Modifier = Modifier,
    formState: MutableState<ApnFormState?> = remember { mutableStateOf(null) }
) {
    var selectedCopyId by rememberSaveable(subscriptionId) { mutableStateOf<Long?>(null) }
    var mode by rememberSaveable { mutableStateOf(ConfigurationMode.Existing) }
    var form by formState
    val scope = rememberCoroutineScope()
    val modeAnchor = remember { BringIntoViewRequester() }
    val motion = MaterialTheme.motionScheme

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(stringResource(R.string.apn_settings_title))
        ModeSwitch(
            selected = mode,
            onSelected = { option ->
                scope.launch {
                    modeAnchor.bringIntoView()
                    mode = option
                }
            },
            modifier = Modifier.bringIntoViewRequester(modeAnchor)
        )
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()))
                    .using(SizeTransform { _, _ -> motion.defaultSpatialSpec() })
            },
            contentAlignment = Alignment.TopStart,
            label = "apnConfigurationMode",
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ModeSwitch(
    selected: ConfigurationMode,
    onSelected: (ConfigurationMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = ConfigurationMode.entries
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val checked = option == selected
            val interactionSource = remember { MutableInteractionSource() }
            val pressed by interactionSource.collectIsPressedAsState()
            val shape = animateContinuousShape(connectedButtonShape(index, options.size, checked, pressed))
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelected(option) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .semantics { role = Role.RadioButton },
                shapes = ToggleButtonShapes(shape = shape, pressedShape = shape, checkedShape = shape),
                colors = FilledTonalToggleButtonDefaults.colors(),
                interactionSource = interactionSource,
            ) {
                Icon(
                    if (checked) Icons.Filled.Check else option.icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    stringResource(option.label),
                    modifier = Modifier.padding(start = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
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
    val motion = MaterialTheme.motionScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FormGroup(title = stringResource(R.string.carrier_label)) {
            ChoiceChips(
                options = CarrierType.entries,
                selected = form?.carrier,
                optionLabel = { stringResource(it.labelRes) },
                onSelected = { carrier ->
                    if (carrier != form?.carrier) onFormChange(ApnFormState.forCarrier(carrier))
                }
            )
        }
        AnimatedVisibility(
            visible = form != null,
            enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
            exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.fastSpatialSpec()),
        ) {
            form?.let { current ->
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ApnFormFields(current, onFormChange)
                    ExpressiveButton(
                        onClick = { onApply(current) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        ButtonLabel(Icons.Filled.Check, stringResource(R.string.apply_button))
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
    val motion = MaterialTheme.motionScheme

    FormGroup(title = stringResource(R.string.basic_settings)) {
        if (form.carrier == CarrierType.CUSTOM) {
            Text(
                stringResource(R.string.carrier_custom_notice),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        ApnTextField(content.name, { onChange(form.copy(content = content.copy(name = it))) }, R.string.apn_name_label)
        ApnTextField(content.apn, { onChange(form.copy(content = content.copy(apn = it))) },
            R.string.apn_address_label, keyboardType = KeyboardType.Uri)
        ApnTextField(content.type, { onChange(form.copy(content = content.copy(type = it))) },
            R.string.apn_type_label, hint = R.string.apn_type_hint)
    }

    FormGroup(title = stringResource(R.string.network_settings)) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ApnTextField(content.mcc, { onChange(form.copy(content = content.copy(mcc = it))) },
                R.string.mcc_label, Modifier.weight(1f), KeyboardType.Number, R.string.mcc_hint)
            ApnTextField(content.mnc, { onChange(form.copy(content = content.copy(mnc = it))) },
                R.string.mnc_label, Modifier.weight(1f), KeyboardType.Number, R.string.mnc_hint)
        }
        LabeledChoice(R.string.auth_type_label) {
            ChoiceChips(
                options = listOf("0", "1", "2", "3"),
                selected = content.authType,
                optionLabel = { stringResource(authLabels[it.toInt()]) },
                onSelected = { onChange(form.copy(content = content.copy(authType = it))) }
            )
        }
        LabeledChoice(R.string.protocol_label) {
            ChoiceChips(protocols, content.protocol, { it },
                { onChange(form.copy(content = content.copy(protocol = it))) })
        }
        LabeledChoice(R.string.roaming_protocol_label) {
            ChoiceChips(protocols, content.roamingProtocol, { it },
                { onChange(form.copy(content = content.copy(roamingProtocol = it))) })
        }
    }

    FormGroup(title = stringResource(R.string.mms_settings)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(
                    value = form.useMmsSettings,
                    role = Role.Switch,
                    onValueChange = { onChange(form.copy(useMmsSettings = it)) }
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(stringResource(R.string.mms_settings_enable), Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = form.useMmsSettings,
                onCheckedChange = null,
                thumbContent = if (form.useMmsSettings) {
                    { Icon(Icons.Filled.Check, null, Modifier.size(SwitchDefaults.IconSize)) }
                } else null
            )
        }
        AnimatedVisibility(
            visible = form.useMmsSettings,
            enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
            exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.fastSpatialSpec()),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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

/** A G2 card that groups related fields under a title. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FormGroup(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceBright,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                color = MaterialTheme.colorScheme.primary,
            )
            content()
        }
    }
}

@Composable
private fun LabeledChoice(@StringRes label: Int, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(label),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        content()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T : Any> ChoiceChips(
    options: List<T>,
    selected: T?,
    optionLabel: @Composable (T) -> String,
    onSelected: (T) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            val isSelected = option == selected
            FilterChip(
                selected = isSelected,
                onClick = { onSelected(option) },
                label = { Text(optionLabel(option)) },
                leadingIcon = if (isSelected) {
                    { Icon(Icons.Filled.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }
                } else null,
                shape = ContinuousCapsule,
            )
        }
    }
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
        shape = MaterialTheme.shapes.large,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        supportingText = hint?.let { { Text(stringResource(it)) } }
    )
}
