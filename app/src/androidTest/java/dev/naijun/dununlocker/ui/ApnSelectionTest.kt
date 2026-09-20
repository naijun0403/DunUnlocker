package dev.naijun.dununlocker.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import dev.naijun.dununlocker.R
import dev.naijun.dununlocker.data.ApnSummary
import dev.naijun.dununlocker.ui.components.ApnConfigSection
import dev.naijun.dununlocker.ui.components.ExistingApnCard
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ApnSelectionTest {
    @get:Rule val compose = createComposeRule()

    private val preferred = ApnSummary(10, "Internet", "internet.example", "default,supl", true)
    private val other = ApnSummary(20, "MMS profile", "mms.example", "mms", false)
    private val rows = listOf(other, preferred)
    private fun text(id: Int, vararg args: Any) =
        InstrumentationRegistry.getInstrumentation().targetContext.getString(id, *args)

    @Test fun preferredIsSelectedButUserCanCopyAnotherRow() {
        var copied: Long? = null
        compose.setContent {
            var selectedId by remember { mutableStateOf<Long?>(null) }
            MaterialTheme {
                ExistingApnCard(1, 0, selectedId, { selectedId = it },
                    { Result.success(rows) }, { copied = it.id })
            }
        }
        compose.onNodeWithText("Internet").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.apn_current_default)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.apn_source_choose_count, 2)).performClick()
        compose.onNodeWithText("MMS profile").performClick()
        compose.onNodeWithText(text(R.string.copy_apn_button)).performClick()
        compose.runOnIdle { assertEquals(20L, copied) }
    }

    @Test fun failedReadDisablesCopyAndRetryLoadsList() {
        var reads = 0
        compose.setContent {
            var selectedId by remember { mutableStateOf<Long?>(null) }
            MaterialTheme {
                ExistingApnCard(1, 0, selectedId, { selectedId = it }, {
                    if (reads++ == 0) Result.failure(IllegalStateException("test failure"))
                    else Result.success(rows)
                }, {})
            }
        }
        compose.onNodeWithText(text(R.string.copy_apn_button)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.apn_list_retry)).performClick()
        compose.onNodeWithText("Internet").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.copy_apn_button)).assertIsEnabled()
    }

    @Test fun simChangeDiscardsAnUnfinishedReadAndOldSelection() {
        val firstRead = CompletableDeferred<Result<List<ApnSummary>>>()
        val sim = mutableIntStateOf(1)
        compose.setContent {
            var selectedId by remember(sim.intValue) { mutableStateOf<Long?>(null) }
            MaterialTheme {
                ExistingApnCard(sim.intValue, 0, selectedId, { selectedId = it }, {
                    if (it == 1) firstRead.await()
                    else Result.success(listOf(other))
                }, {})
            }
        }
        compose.onNodeWithText(text(R.string.apn_list_loading)).assertIsDisplayed()
        compose.runOnIdle { sim.intValue = 2 }
        compose.onNodeWithText(text(R.string.apn_source_choose_count, 1)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.copy_apn_button)).assertIsNotEnabled()
        compose.runOnIdle { firstRead.complete(Result.success(rows)) }
        compose.onNodeWithText("Internet").assertDoesNotExist()
        compose.onNodeWithText(text(R.string.apn_source_choose_count, 1)).performClick()
        compose.onNodeWithText("MMS profile").performClick()
        compose.onNodeWithText(text(R.string.copy_apn_button)).assertIsEnabled()
    }

    @Test fun modeChangesKeepTheChosenSource() {
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ApnConfigSection(
                        subscriptionId = 1,
                        refreshKey = 0,
                        loadApns = { Result.success(rows) },
                        onCopyApnClicked = {},
                        onApplyClicked = { _, _, _, _, _, _, _, _, _, _, _, _, _ -> }
                    )
                }
            }
        }
        compose.onNodeWithText(text(R.string.apn_source_choose_count, 2)).performClick()
        compose.onNodeWithText("MMS profile").performClick()
        compose.onNodeWithText(text(R.string.apn_mode_manual)).performClick()
        compose.onNodeWithText(text(R.string.carrier_selection)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.apn_mode_copy)).performClick()
        compose.onNodeWithText("MMS profile").assertIsDisplayed()
        compose.onNodeWithText(text(R.string.copy_apn_button)).assertIsEnabled()
    }
}
