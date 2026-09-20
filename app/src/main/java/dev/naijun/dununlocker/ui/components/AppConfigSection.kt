package dev.naijun.dununlocker.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.saveable.rememberSaveable
import dev.naijun.dununlocker.data.ApnSummary
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.naijun.dununlocker.R
import dev.naijun.dununlocker.domain.model.ApnContent
import dev.naijun.dununlocker.domain.model.CarrierType

private const val CUSTOM_CARRIER_KEY = "Custom"

private data class CarrierOption(
    val key: String,
    val label: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApnConfigSection(
    subscriptionId: Int?,
    refreshKey: Int,
    loadApns: suspend (Int) -> Result<List<ApnSummary>>,
    onCopyApnClicked: (ApnSummary) -> Unit,
    onApplyClicked: (
        carrier: String,
        apnName: String,
        apnAddress: String,
        apnType: String,
        mmsc: String,
        mmsProxy: String,
        mmsPort: String,
        mcc: String,
        mnc: String,
        authType: String,
        protocol: String,
        roamingProtocol: String,
        useMmsSettings: Boolean
    ) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCopyId by rememberSaveable(subscriptionId) { mutableStateOf<Long?>(null) }
    var copyMode by rememberSaveable { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val modeAnchor = remember { BringIntoViewRequester() }
    var selectedCarrier by remember { mutableStateOf("") }
    var expandedCarrier by remember { mutableStateOf(false) }
    var apnName by remember { mutableStateOf("") }
    var apnAddress by remember { mutableStateOf("") }
    var apnType by remember { mutableStateOf("") }
    var useMmsSettings by remember { mutableStateOf(false) }
    var mmsc by remember { mutableStateOf("") }
    var mmsProxy by remember { mutableStateOf("") }
    var mmsPort by remember { mutableStateOf("") }
    var mcc by remember { mutableStateOf("") }
    var mnc by remember { mutableStateOf("") }
    var authType by remember { mutableStateOf("") }
    var expandedAuthType by remember { mutableStateOf(false) }
    var protocol by remember { mutableStateOf("IPV4V6") }
    var expandedProtocol by remember { mutableStateOf(false) }
    var roamingProtocol by remember { mutableStateOf("IPV4V6") }
    var expandedRoamingProtocol by remember { mutableStateOf(false) }

    val carriers = listOf(
        CarrierOption("SKT (5G)", stringResource(R.string.carrier_skt_5g)),
        CarrierOption("SKT (LTE)", stringResource(R.string.carrier_skt_lte)),
        CarrierOption("KT (5G)", stringResource(R.string.carrier_kt_5g)),
        CarrierOption("KT (LTE)", stringResource(R.string.carrier_kt_lte)),
        CarrierOption("LG U+ (5G)", stringResource(R.string.carrier_lgu_5g)),
        CarrierOption("LG U+ (LTE)", stringResource(R.string.carrier_lgu_lte)),
        CarrierOption(CUSTOM_CARRIER_KEY, stringResource(R.string.carrier_custom))
    )
    val selectedCarrierLabel = carriers.firstOrNull { it.key == selectedCarrier }?.label.orEmpty()
    val protocolOptions = listOf("IPV4V6", "IPV4", "IPV6")

    // Load auth types from string resources
    val authTypeNone = stringResource(R.string.auth_type_value_none)
    val authTypePap = stringResource(R.string.auth_type_value_pap)
    val authTypeChap = stringResource(R.string.auth_type_value_chap)
    val authTypePapOrChap = stringResource(R.string.auth_type_value_pap_or_chap)
    val authTypes = listOf(authTypeNone, authTypePap, authTypeChap, authTypePapOrChap)

    // Initialize authType on first composition
    LaunchedEffect(authTypeNone) {
        if (authType.isEmpty()) {
            authType = authTypeNone
        }
    }

    // 통신사 선택 시 프로필 데이터를 필드에 로드
    LaunchedEffect(selectedCarrier) {
        if (selectedCarrier.isNotEmpty()) {
            val carrierType = CarrierType.fromString(selectedCarrier)
            carrierType?.let { type ->
                val profile = ApnContent.getDefaultConfig(type)

                // 프로필 데이터를 필드에 채우기
                apnName = profile.name
                apnAddress = profile.apn
                apnType = profile.type
                mcc = profile.mcc
                mnc = profile.mnc
                authType = when (profile.authType) {
                    "0" -> authTypeNone
                    "1" -> authTypePap
                    "2" -> authTypeChap
                    "3" -> authTypePapOrChap
                    else -> authTypeNone
                }
                protocol = profile.protocol
                roamingProtocol = profile.roamingProtocol

                // MMS 설정: 프로필에 MMS 값이 있으면 체크박스 ON
                val hasMmsSettings = profile.mmsc.isNotEmpty() || profile.mmsProxy.isNotEmpty() || profile.mmsPort.isNotEmpty()
                useMmsSettings = hasMmsSettings
                mmsc = profile.mmsc
                mmsProxy = profile.mmsProxy
                mmsPort = profile.mmsPort
            }
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // 섹션 제목
        Row(
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = stringResource(R.string.apn_settings_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth().bringIntoViewRequester(modeAnchor)
        ) {
            listOf(R.string.apn_mode_copy, R.string.apn_mode_manual).forEachIndexed { index, label ->
                SegmentedButton(
                    selected = copyMode == (index == 0),
                    onClick = {
                        scope.launch {
                            modeAnchor.bringIntoView()
                            copyMode = index == 0
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index, 2)
                ) { Text(stringResource(label)) }
            }
        }

        // Animate the whole panel (including spacing and its action) as one layout.
        AnimatedContent(
            targetState = copyMode,
            transitionSpec = {
                (fadeIn(tween(180, 90)) togetherWith fadeOut(tween(120)))
                    .using(SizeTransform { _, _ -> tween(300) })
            },
            contentAlignment = androidx.compose.ui.Alignment.TopStart,
            label = "apnConfigurationMode",
            modifier = Modifier.fillMaxWidth()
        ) { copying ->
            if (copying) {
                ExistingApnCard(
                    subscriptionId = subscriptionId,
                    refreshKey = refreshKey,
                    selectedId = selectedCopyId,
                    onSelected = { selectedCopyId = it },
                    loadApns = loadApns,
                    onCopy = onCopyApnClicked
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                    CarrierSelectionCard(
                        selectedCarrier = selectedCarrierLabel,
                        expanded = expandedCarrier,
                        carriers = carriers,
                        onExpandedChange = { expandedCarrier = it },
                        onCarrierSelected = {
                            selectedCarrier = it
                            expandedCarrier = false
                        }
                    )

                    AnimatedVisibility(
                        visible = selectedCarrier.isNotEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        AdditionalInfoCard(
                            apnName = apnName,
                            apnAddress = apnAddress,
                            apnType = apnType,
                            useMmsSettings = useMmsSettings,
                            mmsc = mmsc,
                            mmsProxy = mmsProxy,
                            mmsPort = mmsPort,
                            mcc = mcc,
                            mnc = mnc,
                            authType = authType,
                            expandedAuthType = expandedAuthType,
                            authTypes = authTypes,
                            protocol = protocol,
                            expandedProtocol = expandedProtocol,
                            protocolOptions = protocolOptions,
                            roamingProtocol = roamingProtocol,
                            expandedRoamingProtocol = expandedRoamingProtocol,
                            onApnNameChange = { apnName = it },
                            onApnAddressChange = { apnAddress = it },
                            onApnTypeChange = { apnType = it },
                            onUseMmsSettingsChange = { useMmsSettings = it },
                            onMmscChange = { mmsc = it },
                            onMmsProxyChange = { mmsProxy = it },
                            onMmsPortChange = { if (it.all { char -> char.isDigit() }) mmsPort = it },
                            onMccChange = { if (it.length <= 3 && it.all { char -> char.isDigit() }) mcc = it },
                            onMncChange = { if (it.length <= 3 && it.all { char -> char.isDigit() }) mnc = it },
                            onAuthTypeExpandedChange = { expandedAuthType = it },
                            onAuthTypeSelected = {
                                authType = it
                                expandedAuthType = false
                            },
                            onProtocolExpandedChange = { expandedProtocol = it },
                            onProtocolSelected = {
                                protocol = it
                                expandedProtocol = false
                            },
                            onRoamingProtocolExpandedChange = { expandedRoamingProtocol = it },
                            onRoamingProtocolSelected = {
                                roamingProtocol = it
                                expandedRoamingProtocol = false
                            },
                            selectedCarrier = selectedCarrier
                        )
                    }

                    // 적용 버튼
                    AnimatedVisibility(
                        visible = selectedCarrier.isNotEmpty(),
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Button(
                            onClick = {
                                onApplyClicked(
                                    selectedCarrier, apnName, apnAddress, apnType,
                                    mmsc, mmsProxy, mmsPort, mcc, mnc, authType,
                                    protocol, roamingProtocol, useMmsSettings
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 3.dp,
                                pressedElevation = 6.dp
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = stringResource(R.string.apply_button),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CarrierSelectionCard(
    selectedCarrier: String,
    expanded: Boolean,
    carriers: List<CarrierOption>,
    onExpandedChange: (Boolean) -> Unit,
    onCarrierSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.SignalCellularAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.carrier_selection),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = onExpandedChange
            ) {
                OutlinedTextField(
                    value = selectedCarrier,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.carrier_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )

                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { onExpandedChange(false) }
                ) {
                    carriers.forEach { carrier ->
                        val isCustomCarrier = carrier.key == CUSTOM_CARRIER_KEY
                        DropdownMenuItem(
                            text = {
                                if (isCustomCarrier) {
                                    Column {
                                        Text(
                                            text = carrier.label,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = stringResource(R.string.carrier_custom_description),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    Text(carrier.label)
                                }
                            },
                            onClick = { onCarrierSelected(carrier.key) },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isCustomCarrier) Icons.Filled.Edit else Icons.Filled.SignalCellularAlt,
                                    contentDescription = null,
                                    tint = if (isCustomCarrier) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdditionalInfoCard(
    apnName: String,
    apnAddress: String,
    apnType: String,
    useMmsSettings: Boolean,
    mmsc: String,
    mmsProxy: String,
    mmsPort: String,
    mcc: String,
    mnc: String,
    authType: String,
    expandedAuthType: Boolean,
    authTypes: List<String>,
    protocol: String,
    expandedProtocol: Boolean,
    protocolOptions: List<String>,
    roamingProtocol: String,
    expandedRoamingProtocol: Boolean,
    onApnNameChange: (String) -> Unit,
    onApnAddressChange: (String) -> Unit,
    onApnTypeChange: (String) -> Unit,
    onUseMmsSettingsChange: (Boolean) -> Unit,
    onMmscChange: (String) -> Unit,
    onMmsProxyChange: (String) -> Unit,
    onMmsPortChange: (String) -> Unit,
    onMccChange: (String) -> Unit,
    onMncChange: (String) -> Unit,
    onAuthTypeExpandedChange: (Boolean) -> Unit,
    onAuthTypeSelected: (String) -> Unit,
    onProtocolExpandedChange: (Boolean) -> Unit,
    onProtocolSelected: (String) -> Unit,
    onRoamingProtocolExpandedChange: (Boolean) -> Unit,
    onRoamingProtocolSelected: (String) -> Unit,
    selectedCarrier: String = "",
    modifier: Modifier = Modifier
) {
    val isCustomCarrier = selectedCarrier == CUSTOM_CARRIER_KEY

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 2.dp
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = if (isCustomCarrier) Icons.Filled.Edit else Icons.Filled.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(
                            if (isCustomCarrier)
                                R.string.additional_info_required
                            else
                                R.string.additional_info_optional
                        ),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Custom 선택 시 배지 표시
                if (isCustomCarrier) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = MaterialTheme.shapes.small,
                        tonalElevation = 2.dp
                    ) {
                        Text(
                            text = stringResource(R.string.carrier_custom),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // Custom 모드 안내
            if (isCustomCarrier) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.carrier_custom_notice),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            // 기본 APN 정보
            Text(
                text = stringResource(R.string.basic_settings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = apnName,
                onValueChange = onApnNameChange,
                label = {
                    Text(
                        stringResource(R.string.apn_name_label) +
                        if (isCustomCarrier) " *" else ""
                    )
                },
                placeholder = { Text(stringResource(R.string.apn_name_placeholder)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(stringResource(R.string.apn_name_hint)) },
                isError = isCustomCarrier && apnName.isEmpty()
            )

            OutlinedTextField(
                value = apnAddress,
                onValueChange = onApnAddressChange,
                label = {
                    Text(
                        stringResource(R.string.apn_address_label) +
                        if (isCustomCarrier) " *" else ""
                    )
                },
                placeholder = { Text(stringResource(R.string.apn_address_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Language, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(stringResource(R.string.apn_address_hint)) },
                isError = isCustomCarrier && apnAddress.isEmpty()
            )

            OutlinedTextField(
                value = apnType,
                onValueChange = onApnTypeChange,
                label = {
                    Text(
                        stringResource(R.string.apn_type_label) +
                        if (isCustomCarrier) " *" else ""
                    )
                },
                placeholder = { Text(stringResource(R.string.apn_type_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Category, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(stringResource(R.string.apn_type_hint)) },
                isError = isCustomCarrier && apnType.isEmpty()
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // MMS 설정
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.mms_settings),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                // 통신사별 안내 배지
                if (selectedCarrier.isNotEmpty()) {
                    val carrierType = CarrierType.fromString(selectedCarrier)
                    if (carrierType != null) {
                        Surface(
                            color = if (carrierType.requiresMmsSettings())
                                MaterialTheme.colorScheme.tertiaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                            shape = MaterialTheme.shapes.small,
                            tonalElevation = 2.dp
                        ) {
                            Text(
                                text = if (carrierType.requiresMmsSettings())
                                    stringResource(R.string.mms_settings_lte)
                                else
                                    stringResource(R.string.mms_settings_5g),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                color = if (carrierType.requiresMmsSettings())
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onUseMmsSettingsChange(!useMmsSettings) },
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
            ) {
                // MMS 설정 사용 여부 체크박스
                Checkbox(
                    checked = useMmsSettings,
                    onCheckedChange = onUseMmsSettingsChange,
                    colors = CheckboxDefaults.colors(
                        checkedColor = MaterialTheme.colorScheme.primary,
                        uncheckedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        checkmarkColor = MaterialTheme.colorScheme.onPrimary
                    )
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.mms_settings_enable),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = if (useMmsSettings)
                            stringResource(R.string.mms_settings_enabled)
                        else
                            stringResource(R.string.mms_settings_disabled),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = mmsc,
                onValueChange = onMmscChange,
                enabled = useMmsSettings,
                label = { Text(stringResource(R.string.mmsc_label)) },
                placeholder = { Text(stringResource(R.string.mmsc_placeholder)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(stringResource(R.string.mmsc_hint)) }
            )

            OutlinedTextField(
                value = mmsProxy,
                onValueChange = onMmsProxyChange,
                enabled = useMmsSettings,
                label = { Text(stringResource(R.string.mms_proxy_label)) },
                placeholder = { Text(stringResource(R.string.mms_proxy_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Router, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(stringResource(R.string.mms_proxy_hint)) }
            )

            OutlinedTextField(
                value = mmsPort,
                onValueChange = onMmsPortChange,
                enabled = useMmsSettings,
                label = { Text(stringResource(R.string.mms_port_label)) },
                placeholder = { Text(stringResource(R.string.mms_port_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Numbers, contentDescription = null) },
                modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(stringResource(R.string.mms_port_hint)) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // 네트워크 설정
            Text(
                text = stringResource(R.string.network_settings),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = mcc,
                    onValueChange = onMccChange,
                    label = {
                        Text(
                            stringResource(R.string.mcc_label) +
                            if (isCustomCarrier) " *" else ""
                        )
                    },
                    placeholder = { Text(stringResource(R.string.mcc_placeholder)) },
                    modifier = Modifier.weight(1f),
                    supportingText = { Text(stringResource(R.string.mcc_hint)) },
                    isError = isCustomCarrier && mcc.isEmpty()
                )

                OutlinedTextField(
                    value = mnc,
                    onValueChange = onMncChange,
                    label = {
                        Text(
                            stringResource(R.string.mnc_label) +
                            if (isCustomCarrier) " *" else ""
                        )
                    },
                    placeholder = { Text(stringResource(R.string.mnc_placeholder)) },
                    modifier = Modifier.weight(1f),
                    supportingText = { Text(stringResource(R.string.mnc_hint)) },
                    isError = isCustomCarrier && mnc.isEmpty()
                )
            }

            ExposedDropdownMenuBox(
                expanded = expandedAuthType,
                onExpandedChange = onAuthTypeExpandedChange
            ) {
                OutlinedTextField(
                    value = authType,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.auth_type_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedAuthType) },
                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    supportingText = { Text(stringResource(R.string.auth_type_hint)) }
                )

                ExposedDropdownMenu(
                    expanded = expandedAuthType,
                    onDismissRequest = { onAuthTypeExpandedChange(false) }
                ) {
                    authTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type) },
                            onClick = { onAuthTypeSelected(type) }
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = expandedProtocol,
                onExpandedChange = onProtocolExpandedChange
            ) {
                OutlinedTextField(
                    value = protocol,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.protocol_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedProtocol) },
                    leadingIcon = { Icon(Icons.Filled.NetworkCheck, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    supportingText = { Text(stringResource(R.string.protocol_hint)) }
                )

                ExposedDropdownMenu(
                    expanded = expandedProtocol,
                    onDismissRequest = { onProtocolExpandedChange(false) }
                ) {
                    protocolOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = { onProtocolSelected(option) }
                        )
                    }
                }
            }

            ExposedDropdownMenuBox(
                expanded = expandedRoamingProtocol,
                onExpandedChange = onRoamingProtocolExpandedChange
            ) {
                OutlinedTextField(
                    value = roamingProtocol,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.roaming_protocol_label)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedRoamingProtocol) },
                    leadingIcon = { Icon(Icons.Filled.Public, contentDescription = null) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, true),
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    supportingText = { Text(stringResource(R.string.roaming_protocol_hint)) }
                )

                ExposedDropdownMenu(
                    expanded = expandedRoamingProtocol,
                    onDismissRequest = { onRoamingProtocolExpandedChange(false) }
                ) {
                    protocolOptions.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = { onRoamingProtocolSelected(option) }
                        )
                    }
                }
            }
        }
    }
}
