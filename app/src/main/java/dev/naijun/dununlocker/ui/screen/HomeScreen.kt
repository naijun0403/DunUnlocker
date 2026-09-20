package dev.naijun.dununlocker.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.naijun.dununlocker.R
import dev.naijun.dununlocker.data.ApnSummary
import kotlinx.coroutines.CancellationException
import dev.naijun.dununlocker.data.ApnManager
import dev.naijun.dununlocker.data.ShizukuManager
import dev.naijun.dununlocker.data.SimInfo
import dev.naijun.dununlocker.domain.model.CarrierType
import dev.naijun.dununlocker.ui.components.ApnConfigSection
import dev.naijun.dununlocker.ui.components.ShizukuStatusCard
import dev.naijun.dununlocker.ui.components.SimSelectionCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val resources = LocalResources.current
    val apnManager = remember { ApnManager(context) }
    val scope = rememberCoroutineScope()

    val validationErrorName = stringResource(R.string.validation_error_name)
    val validationErrorApnAddress = stringResource(R.string.validation_error_apn_address)
    val validationErrorApnType = stringResource(R.string.validation_error_apn_type)
    val validationErrorMcc = stringResource(R.string.validation_error_mcc)
    val validationErrorMnc = stringResource(R.string.validation_error_mnc)
    val validationErrorMmsc = stringResource(R.string.validation_error_mmsc)
    val validationErrorMmsPort = stringResource(R.string.validation_error_mms_port)
    val validationErrorRequiredFields = stringResource(R.string.validation_error_required_fields)
    val carrierNotSupported = stringResource(R.string.carrier_not_supported)
    val apnApplySuccess = stringResource(R.string.apn_apply_success)
    val copyApnSuccess = stringResource(R.string.copy_apn_success)
    val apnApplyFailure = stringResource(R.string.apn_apply_failure)
    val errorOccurred = stringResource(R.string.error_occurred)
    val unknownError = stringResource(R.string.unknown_error)

    val shizukuGranted by ShizukuManager.isGranted.collectAsState()
    val shizukuRunning by ShizukuManager.isRunning.collectAsState()
    val shizukuErrorMessage by ShizukuManager.errorMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var isLoading by remember { mutableStateOf(false) }
    var apnRefreshKey by remember { mutableIntStateOf(0) }
    var pendingApnRequest by remember { mutableStateOf<PendingApnRequest?>(null) }

    var simList by remember { mutableStateOf<List<SimInfo>>(emptyList()) }
    var selectedSim by remember { mutableStateOf<SimInfo?>(null) }
    var showAppInfoDialog by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    LaunchedEffect(shizukuGranted) {
        if (shizukuGranted) {
            simList = apnManager.getActiveSubscriptions()
            if (simList.size == 1) {
                selectedSim = simList.first()
            }
        } else {
            simList = emptyList()
            selectedSim = null
        }
    }

    LaunchedEffect(shizukuErrorMessage) {
        shizukuErrorMessage?.let {
            snackbarHostState.showSnackbar(
                message = resources.getString(it.resId, *it.args.toTypedArray()),
                duration = SnackbarDuration.Short
            )
            ShizukuManager.clearError()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.top_bar_title)) },
                actions = {
                    IconButton(onClick = { showOverflowMenu = true }) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = stringResource(R.string.menu_more_options)
                        )
                    }
                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.menu_app_info)) },
                            onClick = {
                                showOverflowMenu = false
                                showAppInfoDialog = true
                            },
                            leadingIcon = {
                                Icon(Icons.Filled.Info, contentDescription = null)
                            }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ShizukuStatusCard(
                    isGranted = shizukuGranted,
                    isRunning = shizukuRunning,
                    onRequestPermission = {
                        ShizukuManager.requestPermission()
                    }
                )

                AnimatedVisibility(
                    visible = shizukuGranted,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        SimSelectionCard(
                            simList = simList,
                            selectedSim = selectedSim,
                            onSimSelected = { selectedSim = it }
                        )

                        AnimatedVisibility(
                            visible = selectedSim != null || simList.isEmpty(),
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically()
                        ) {
                            ApnConfigSection(
                                subscriptionId = selectedSim?.subscriptionId,
                                refreshKey = apnRefreshKey,
                                loadApns = apnManager::getApns,
                                onCopyApnClicked = { apn ->
                                    selectedSim?.let { sim ->
                                        pendingApnRequest = PendingApnRequest.Copy(apn, sim)
                                    }
                                },
                                onApplyClicked = { carrier, apnName, apnAddress, apnType, mmsc, mmsProxy, mmsPort, mcc, mnc, authType, protocol, roamingProtocol, useMmsSettings ->
                                    val validationErrors = mutableListOf<String>()

                                    if (apnName.isEmpty()) validationErrors.add(validationErrorName)
                                    if (apnAddress.isEmpty()) validationErrors.add(validationErrorApnAddress)
                                    if (apnType.isEmpty()) validationErrors.add(validationErrorApnType)
                                    if (mcc.isEmpty()) validationErrors.add(validationErrorMcc)
                                    if (mnc.isEmpty()) validationErrors.add(validationErrorMnc)

                                    if (useMmsSettings) {
                                        if (mmsc.isEmpty()) validationErrors.add(validationErrorMmsc)
                                        if (mmsPort.isEmpty()) validationErrors.add(validationErrorMmsPort)
                                    }

                                    if (validationErrors.isNotEmpty()) {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = validationErrorRequiredFields.format(validationErrors.joinToString(", ")),
                                                duration = SnackbarDuration.Long
                                            )
                                        }
                                        return@ApnConfigSection
                                    }

                                    pendingApnRequest = PendingApnRequest.Configure(
                                        PendingApnData(
                                            carrier, apnName, apnAddress, apnType,
                                            mmsc, mmsProxy, mmsPort,
                                            mcc, mnc, authType,
                                            protocol, roamingProtocol,
                                            useMmsSettings
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.5f)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            elevation = CardDefaults.cardElevation(
                                defaultElevation = 6.dp
                            ),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Column(
                                modifier = Modifier.padding(40.dp),
                                horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp),
                                    strokeWidth = 4.dp
                                )
                                Text(
                                    text = stringResource(R.string.loading_dialog_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = stringResource(R.string.loading_dialog_subtitle),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingApnRequest?.let { request ->
        ApnApplyConfirmDialog(
            request = request,
            selectedSim = (request as? PendingApnRequest.Copy)?.sim ?: selectedSim,
            onConfirm = {
                pendingApnRequest = null  // Dialog를 먼저 닫음
                scope.launch {
                    isLoading = true
                    try {
                        val result = when (request) {
                            is PendingApnRequest.Copy -> {
                                apnManager.copyApnWithDun(
                                    subscriptionId = request.sim.subscriptionId,
                                    sourceApnId = request.apn.id
                                )
                            }

                            is PendingApnRequest.Configure -> {
                                val data = request.data
                                val carrierType = CarrierType.fromString(data.carrier)
                                if (carrierType == null) {
                                    isLoading = false
                                    snackbarHostState.showSnackbar(
                                        message = carrierNotSupported,
                                        duration = SnackbarDuration.Short
                                    )
                                    return@launch
                                }

                                val customApn = apnManager.createCustomApnContent(
                                    carrierType = carrierType,
                                    name = data.apnName,
                                    apnAddress = data.apnAddress,
                                    apnType = data.apnType,
                                    mmsc = data.mmsc,
                                    mmsProxy = data.mmsProxy,
                                    mmsPort = data.mmsPort,
                                    mcc = data.mcc,
                                    mnc = data.mnc,
                                    authType = data.authType,
                                    protocol = data.protocol,
                                    roamingProtocol = data.roamingProtocol,
                                    useMmsSettings = data.useMmsSettings
                                )

                                apnManager.applyApnConfig(
                                    carrierType = carrierType,
                                    customApnContent = customApn,
                                    subscriptionId = selectedSim?.subscriptionId
                                )
                            }
                        }

                        result.onSuccess {
                            apnRefreshKey++
                            isLoading = false
                            snackbarHostState.showSnackbar(
                                message = if (request is PendingApnRequest.Copy) {
                                    copyApnSuccess
                                } else {
                                    apnApplySuccess
                                },
                                duration = SnackbarDuration.Long
                            )
                        }.onFailure { error ->
                            isLoading = false
                            snackbarHostState.showSnackbar(
                                message = apnApplyFailure.format(error.message ?: unknownError),
                                duration = SnackbarDuration.Long
                            )
                        }
                    } catch (e: CancellationException) {
                        isLoading = false
                        throw e
                    } catch (e: Exception) {
                        isLoading = false
                        snackbarHostState.showSnackbar(
                            message = errorOccurred.format(e.message ?: unknownError),
                            duration = SnackbarDuration.Long
                        )
                    }
                }
            },
            onDismiss = {
                pendingApnRequest = null
            }
        )
    }

    if (showAppInfoDialog) {
        AppInfoDialog(
            onDismiss = { showAppInfoDialog = false }
        )
    }
}

@Composable
private fun AppInfoDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val githubUrl = stringResource(R.string.app_info_github_url)
    val unknownVersion = stringResource(R.string.unknown_version)
    val packageInfo = remember {
        context.packageManager.getPackageInfo(context.packageName, 0)
    }
    val versionName = packageInfo.versionName ?: unknownVersion

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = stringResource(R.string.app_info_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Description
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest
                ) {
                    Text(
                        text = stringResource(R.string.app_info_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                HorizontalDivider()

                // Version
                InfoRow(
                    label = stringResource(R.string.app_info_version),
                    value = versionName
                )

                // Developer
                InfoRow(
                    label = stringResource(R.string.app_info_developer),
                    value = stringResource(R.string.app_info_developer_name)
                )

                // GitHub
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    onClick = {
                        uriHandler.openUri("https://$githubUrl")
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = stringResource(R.string.app_info_github),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = stringResource(R.string.app_info_github_url),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                // License
                InfoRow(
                    label = stringResource(R.string.app_info_license),
                    value = stringResource(R.string.app_info_license_type)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.app_info_close))
            }
        }
    )
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ApnApplyConfirmDialog(
    request: PendingApnRequest,
    selectedSim: SimInfo?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.confirm_dialog_title),
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = when (request) {
                        is PendingApnRequest.Copy -> {
                            stringResource(
                                R.string.copy_apn_confirm_message,
                                request.apn.name.ifBlank { request.apn.apn },
                                request.apn.apn,
                                request.apn.type.ifBlank { "*" }
                            )
                        }

                        is PendingApnRequest.Configure -> {
                            if (CarrierType.fromString(request.data.carrier) == CarrierType.CUSTOM) {
                                stringResource(R.string.confirm_dialog_message_custom)
                            } else {
                                stringResource(R.string.confirm_dialog_message, request.data.carrier)
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge
                )

                selectedSim?.let { sim ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        tonalElevation = 3.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.confirm_dialog_target_sim),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = sim.formattedName,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    tonalElevation = 3.dp
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.confirm_dialog_important_notice),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = stringResource(R.string.confirm_dialog_notice_content),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                lineHeight = MaterialTheme.typography.bodyMedium.lineHeight
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text(stringResource(R.string.confirm_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel_button))
            }
        }
    )
}

private sealed interface PendingApnRequest {
    data class Copy(val apn: ApnSummary, val sim: SimInfo) : PendingApnRequest
    data class Configure(val data: PendingApnData) : PendingApnRequest
}

private data class PendingApnData(
    val carrier: String,
    val apnName: String,
    val apnAddress: String,
    val apnType: String,
    val mmsc: String,
    val mmsProxy: String,
    val mmsPort: String,
    val mcc: String,
    val mnc: String,
    val authType: String,
    val protocol: String,
    val roamingProtocol: String,
    val useMmsSettings: Boolean
)
