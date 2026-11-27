package dev.naijun.dununlocker.ui.screen

import android.content.res.Configuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import dev.naijun.dununlocker.data.ApnSummary
import dev.naijun.dununlocker.data.SimInfo
import dev.naijun.dununlocker.ui.model.ApnFormState
import dev.naijun.dununlocker.ui.theme.DunUnlockerTheme

private val previewSims = listOf(
    SimInfo(1, 0, "SKTelecom", "SKTelecom", "450", "05"),
    SimInfo(2, 1, "LG U+", "Work eSIM", "450", "06"),
)

private val previewApns = listOf(
    ApnSummary(10, "SKT LTE", "lte.sktelecom.com", "default,supl,mms,ia", true),
    ApnSummary(11, "SKT_LTE_DUN", "lte.sktelecom.com", "default,supl,mms,dun", false),
)

@Composable
private fun HomePreview(granted: Boolean, running: Boolean) {
    DunUnlockerTheme {
        HomeContent(
            shizukuGranted = granted,
            shizukuRunning = running,
            usesCarrierPrivilegeMode = true,
            onRequestShizukuPermission = {},
            simList = previewSims,
            selectedSim = previewSims.first(),
            isSimLoading = false,
            simLoadFailed = false,
            showApnSection = true,
            onRefreshSims = {},
            onSimSelected = {},
            apnRefreshKey = 0,
            manualApnForm = remember { mutableStateOf<ApnFormState?>(null) },
            loadApns = { _, _ -> Result.success(previewApns) },
            onRequest = {},
            onMissingFields = {},
            isWorking = false,
            snackbarHostState = remember { SnackbarHostState() },
            onShowAppInfo = {},
        )
    }
}

@Preview(name = "Connected", locale = "ko", heightDp = 1000)
@Preview(name = "Connected · Dark", locale = "ko", heightDp = 1000, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ConnectedPreview() = HomePreview(granted = true, running = true)

@Preview(name = "Shizuku not running", locale = "ko")
@Composable
private fun NotRunningPreview() = HomePreview(granted = false, running = false)
