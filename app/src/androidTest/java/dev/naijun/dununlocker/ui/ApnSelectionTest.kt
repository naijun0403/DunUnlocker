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
import dev.naijun.dununlocker.ui.components.NamedApnCopyDialog
import dev.naijun.dununlocker.ui.model.ApnFormState
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

    @Test fun failedReadDisablesCopyAndRetryLoadsList() {
        var reads = 0
        compose.setContent {
            var selectedId by remember { mutableStateOf<Long?>(null) }
            MaterialTheme {
                ExistingApnCard(1, 0, selectedId, { selectedId = it }, {
                    if (reads++ == 0) Result.failure(IllegalStateException("test failure"))
                    else Result.success(rows)
                }, {}, onNamedCopy = {})
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
                }, {}, onNamedCopy = {})
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

    @Test fun mmsFieldsKeepTheirValuesWhenCollapsed() {
        var appliedMmsc: String? = null
        var appliedMmsEnabled: Boolean? = null
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ApnConfigSection(
                        subscriptionId = 1,
                        refreshKey = 0,
                        loadApns = { Result.success(rows) },
                        onCopyApnClicked = {},
                        onNamedCopyApnClicked = {},
                        onApplyClicked = { form ->
                            appliedMmsc = form.content.mmsc
                            appliedMmsEnabled = form.useMmsSettings
                        }
                    )
                }
            }
        }
        compose.onNodeWithText(text(R.string.apn_mode_manual)).performClick()
        compose.onNodeWithText(text(R.string.carrier_label)).performClick()
        compose.onNodeWithText(text(R.string.carrier_skt_lte)).performClick()
        compose.onNodeWithText(text(R.string.mmsc_label)).performScrollTo()
            .performTextReplacement("https://mms.example")
        compose.onNodeWithText(text(R.string.mms_settings_enable)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.mmsc_label)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.mms_settings_enable)).performScrollTo().performClick()
        compose.onNodeWithText("https://mms.example").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.apply_button)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("https://mms.example", appliedMmsc)
            assertEquals(true, appliedMmsEnabled)
        }
    }

    @Test fun namedCopyValidatesNameAndUsesTheSelectedApn() {
        var copiedId: Long? = null
        var copiedName: String? = null
        compose.setContent {
            var selectedId by remember { mutableStateOf<Long?>(null) }
            var copySource by remember { mutableStateOf<ApnSummary?>(null) }
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ExistingApnCard(1, 0, selectedId, { selectedId = it },
                        { Result.success(rows) }, {},
                        onNamedCopy = { copySource = it })
                }
                copySource?.let { source ->
                    NamedApnCopyDialog(source, "SIM 1", {
                        copiedId = source.id
                        copiedName = it
                        copySource = null
                    }, { copySource = null })
                }
            }
        }
        compose.onNodeWithText(text(R.string.apn_source_choose_count, 2)).performClick()
        compose.onNodeWithText("MMS profile").performClick()
        compose.onNodeWithText(text(R.string.apn_named_copy_button)).performScrollTo().performClick()
        compose.onNode(hasSetTextAction()).assertTextContains("MMS profile (DUN)")
            .performTextReplacement("   ")
        compose.onNodeWithText(text(R.string.apn_named_copy_confirm)).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("MMS profile")
        compose.onNodeWithText(text(R.string.apn_named_copy_confirm)).assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextReplacement("  My APN  ")
        compose.onNodeWithText(text(R.string.apn_named_copy_confirm)).performClick()
        compose.runOnIdle {
            assertEquals(20L, copiedId)
            assertEquals("My APN", copiedName)
        }
    }

    @Test fun manualEditsSurviveModeSwitchesAndCarrierReselection() {
        var applied: ApnFormState? = null
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    ApnConfigSection(
                        subscriptionId = 1,
                        refreshKey = 0,
                        loadApns = { Result.success(rows) },
                        onCopyApnClicked = {},
                        onNamedCopyApnClicked = {},
                        onApplyClicked = { applied = it }
                    )
                }
            }
        }
        compose.onNodeWithText(text(R.string.apn_mode_manual)).performClick()
        compose.onNodeWithText(text(R.string.carrier_label)).performClick()
        compose.onNodeWithText(text(R.string.carrier_skt_lte)).performClick()
        compose.onNodeWithText(text(R.string.apn_name_label)).performScrollTo()
            .performTextReplacement("Edited APN")
        compose.onNodeWithText(text(R.string.carrier_label)).performScrollTo().performClick()
        compose.onAllNodesWithText(text(R.string.carrier_skt_lte)).onLast().performClick()
        compose.onNodeWithText(text(R.string.apn_mode_copy)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.apn_mode_manual)).performClick()
        compose.onNodeWithText("Edited APN").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.auth_type_label)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.auth_type_value_pap)).performClick()
        compose.onNodeWithText(text(R.string.apply_button)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals("Edited APN", applied?.content?.name)
            assertEquals("1", applied?.content?.authType)
        }
    }

}
