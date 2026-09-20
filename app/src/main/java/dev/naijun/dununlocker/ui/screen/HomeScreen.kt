package dev.naijun.dununlocker.ui.screen

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import dev.naijun.dununlocker.R
import kotlinx.coroutines.CancellationException
import dev.naijun.dununlocker.data.ApnManager
import dev.naijun.dununlocker.data.ShizukuManager
import dev.naijun.dununlocker.data.SimInfo
import dev.naijun.dununlocker.ui.components.ApnConfigSection
import dev.naijun.dununlocker.ui.components.ShizukuStatusCard
import dev.naijun.dununlocker.ui.components.SimSelectionCard
import dev.naijun.dununlocker.ui.model.ApnRequest
import dev.naijun.dununlocker.ui.model.execute
import dev.naijun.dununlocker.ui.model.successMessageRes
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val resources = LocalResources.current
    val apnManager = remember { ApnManager(context) }
    val scope = rememberCoroutineScope()

    val shizukuGranted by ShizukuManager.isGranted.collectAsState()
    val shizukuRunning by ShizukuManager.isRunning.collectAsState()
    val shizukuErrorMessage by ShizukuManager.errorMessage.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    var isLoading by remember { mutableStateOf(false) }
    var apnRefreshKey by remember { mutableIntStateOf(0) }
    var pendingApnRequest by remember { mutableStateOf<ApnRequest?>(null) }

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
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxSize()
                    .padding(innerPadding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 8.dp),
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
                        verticalArrangement = Arrangement.spacedBy(24.dp)
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
                                        pendingApnRequest = ApnRequest.AddDun(apn, sim)
                                    }
                                },
                                onNamedCopyApnClicked = { apn ->
                                    selectedSim?.let { sim ->
                                        pendingApnRequest = ApnRequest.NamedCopy(apn, sim)
                                    }
                                },
                                onApplyClicked = { form ->
                                    val missingFields = form.missingFieldLabels()
                                    if (missingFields.isEmpty()) {
                                        pendingApnRequest = ApnRequest.Configure(form, selectedSim)
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar(
                                                message = resources.getString(
                                                    R.string.validation_error_required_fields,
                                                    missingFields.joinToString(", ") { resources.getString(it) }
                                                ),
                                                duration = SnackbarDuration.Long
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                Dialog(onDismissRequest = {}) {
                    Surface(shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                        Row(
                            modifier = Modifier.padding(24.dp),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(Modifier.size(32.dp))
                            Text(stringResource(R.string.loading_dialog_title),
                                style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }

    pendingApnRequest?.let { request ->
        ApnApplyConfirmDialog(
            request = request,
            onConfirm = { confirmed ->
                pendingApnRequest = null
                scope.launch {
                    isLoading = true
                    val result = try {
                        confirmed.execute(apnManager)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Result.failure(e)
                    } finally {
                        isLoading = false
                    }
                    val message = result.fold(
                        onSuccess = {
                            apnRefreshKey++
                            resources.getString(confirmed.successMessageRes)
                        },
                        onFailure = { error ->
                            resources.getString(
                                R.string.apn_apply_failure,
                                error.message ?: resources.getString(R.string.unknown_error)
                            )
                        }
                    )
                    snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Long)
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
