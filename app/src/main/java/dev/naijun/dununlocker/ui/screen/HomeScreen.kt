package dev.naijun.dununlocker.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.naijun.dununlocker.R
import dev.naijun.dununlocker.data.ApnManager
import dev.naijun.dununlocker.data.ApnSummary
import dev.naijun.dununlocker.data.ShizukuManager
import dev.naijun.dununlocker.data.SimInfo
import dev.naijun.dununlocker.ui.components.ApnConfigSection
import dev.naijun.dununlocker.ui.components.ShizukuStatusCard
import dev.naijun.dununlocker.ui.components.SimSelectionCard
import dev.naijun.dununlocker.ui.model.ApnFormState
import dev.naijun.dununlocker.ui.model.ApnRequest
import dev.naijun.dununlocker.ui.model.execute
import dev.naijun.dununlocker.ui.model.successMessageRes
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

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
    val apnRefreshKey by ApnManager.apnChanges.collectAsState()
    var pendingApnRequest by remember { mutableStateOf<ApnRequest?>(null) }
    val manualApnForm = remember { mutableStateOf<ApnFormState?>(null) }

    var simRefreshKey by remember { mutableIntStateOf(0) }
    var simResult by remember(shizukuGranted) { mutableStateOf<Result<List<SimInfo>>?>(null) }
    var loadedSimRefreshKey by remember(shizukuGranted) { mutableStateOf<Int?>(null) }
    val isSimLoading = loadedSimRefreshKey != simRefreshKey
    val simList = simResult?.getOrNull().orEmpty()
    var selectedSimId by remember(shizukuGranted) { mutableStateOf<Int?>(null) }
    val selectedSim = simList.firstOrNull { it.subscriptionId == selectedSimId }
    var showAppInfoDialog by remember { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { simRefreshKey++ }

    LaunchedEffect(shizukuGranted, simRefreshKey) {
        if (shizukuGranted) {
            val requestedRefreshKey = simRefreshKey
            val result = apnManager.getActiveSubscriptions()
            result.onSuccess { sims ->
                selectedSimId = sims.singleOrNull()?.subscriptionId
                    ?: selectedSimId?.takeIf { id -> sims.any { it.subscriptionId == id } }
            }
            simResult = result
            loadedSimRefreshKey = requestedRefreshKey
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

    HomeContent(
        shizukuGranted = shizukuGranted,
        shizukuRunning = shizukuRunning,
        usesCarrierPrivilegeMode = apnManager.usesCarrierPrivilegeMode,
        onRequestShizukuPermission = { ShizukuManager.requestPermission() },
        simList = simList,
        selectedSim = selectedSim,
        isSimLoading = isSimLoading,
        simLoadFailed = simResult?.isFailure == true,
        showApnSection = simResult?.isSuccess == true && (selectedSim != null || simList.isEmpty()),
        onRefreshSims = { simRefreshKey++ },
        onSimSelected = { selectedSimId = it?.subscriptionId },
        apnRefreshKey = apnRefreshKey,
        manualApnForm = manualApnForm,
        loadApns = { subId, forceRefresh -> apnManager.getApns(subId, forceRefresh) },
        onRequest = { pendingApnRequest = it },
        onMissingFields = { missingFields ->
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = resources.getString(
                        R.string.validation_error_required_fields,
                        missingFields.joinToString(", ") { resources.getString(it) }
                    ),
                    duration = SnackbarDuration.Long
                )
            }
        },
        isWorking = isLoading,
        snackbarHostState = snackbarHostState,
        onShowAppInfo = { showAppInfoDialog = true },
    )

    pendingApnRequest?.let { request ->
        ApnApplyConfirmDialog(
            request = request,
            onConfirm = { confirmed ->
                pendingApnRequest = null
                val targetSimId = confirmed.sim?.subscriptionId
                if (!shizukuGranted || isSimLoading || simResult?.isSuccess != true ||
                    targetSimId != selectedSim?.subscriptionId ||
                    (targetSimId == null && simList.isNotEmpty())
                ) {
                    scope.launch {
                        snackbarHostState.showSnackbar(resources.getString(R.string.sim_selection_changed))
                    }
                    return@ApnApplyConfirmDialog
                }
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
                        onSuccess = { resources.getString(confirmed.successMessageRes) },
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

/** The home screen layout. It holds no app state, so it can be previewed with sample data. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun HomeContent(
    shizukuGranted: Boolean,
    shizukuRunning: Boolean,
    usesCarrierPrivilegeMode: Boolean,
    onRequestShizukuPermission: () -> Unit,
    simList: List<SimInfo>,
    selectedSim: SimInfo?,
    isSimLoading: Boolean,
    simLoadFailed: Boolean,
    showApnSection: Boolean,
    onRefreshSims: () -> Unit,
    onSimSelected: (SimInfo?) -> Unit,
    apnRefreshKey: Int,
    manualApnForm: MutableState<ApnFormState?>,
    loadApns: suspend (Int, Boolean) -> Result<List<ApnSummary>>,
    onRequest: (ApnRequest) -> Unit,
    onMissingFields: (List<Int>) -> Unit,
    isWorking: Boolean,
    snackbarHostState: SnackbarHostState,
    onShowAppInfo: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = colors.surfaceContainer,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(R.string.top_bar_title)) },
                subtitle = { Text(stringResource(R.string.app_info_description)) },
                actions = {
                    IconButton(onClick = onShowAppInfo) {
                        Icon(Icons.Outlined.Info, contentDescription = stringResource(R.string.menu_app_info))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.surfaceContainer,
                    scrolledContainerColor = colors.surfaceContainer,
                ),
                scrollBehavior = scrollBehavior,
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
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                ShizukuStatusCard(
                    isGranted = shizukuGranted,
                    isRunning = shizukuRunning,
                    usesCarrierPrivilegeMode = usesCarrierPrivilegeMode,
                    onRequestPermission = onRequestShizukuPermission,
                )

                AnimatedVisibility(
                    visible = shizukuGranted,
                    enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
                    exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.fastSpatialSpec())
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        SimSelectionCard(
                            simList = simList,
                            selectedSim = selectedSim,
                            isLoading = isSimLoading,
                            loadFailed = simLoadFailed,
                            onRefresh = onRefreshSims,
                            onSimSelected = onSimSelected
                        )

                        AnimatedVisibility(
                            visible = showApnSection,
                            enter = fadeIn(motion.defaultEffectsSpec()) + expandVertically(motion.defaultSpatialSpec()),
                            exit = fadeOut(motion.fastEffectsSpec()) + shrinkVertically(motion.fastSpatialSpec())
                        ) {
                            ApnConfigSection(
                                subscriptionId = selectedSim?.subscriptionId,
                                formState = manualApnForm,
                                refreshKey = apnRefreshKey,
                                loadApns = loadApns,
                                onCopyApnClicked = { apn ->
                                    selectedSim?.let { sim -> onRequest(ApnRequest.AddDun(apn, sim)) }
                                },
                                onNamedCopyApnClicked = { apn ->
                                    selectedSim?.let { sim -> onRequest(ApnRequest.NamedCopy(apn, sim)) }
                                },
                                onApplyClicked = { form ->
                                    val missingFields = form.missingFieldLabels()
                                    if (missingFields.isEmpty()) {
                                        onRequest(ApnRequest.Configure(form, selectedSim))
                                    } else {
                                        onMissingFields(missingFields)
                                    }
                                }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (isWorking) {
                Dialog(onDismissRequest = {}) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLargeIncreased,
                        color = colors.surfaceContainerHigh
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 40.dp, vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            LoadingIndicator(Modifier.size(72.dp))
                            Text(
                                stringResource(R.string.loading_dialog_title),
                                style = MaterialTheme.typography.titleMediumEmphasized
                            )
                        }
                    }
                }
            }
        }
    }
}
