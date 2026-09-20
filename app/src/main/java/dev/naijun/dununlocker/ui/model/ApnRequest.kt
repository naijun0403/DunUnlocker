package dev.naijun.dununlocker.ui.model

import androidx.annotation.StringRes
import dev.naijun.dununlocker.R
import dev.naijun.dununlocker.data.ApnManager
import dev.naijun.dununlocker.data.ApnSummary
import dev.naijun.dununlocker.data.SimInfo

internal sealed interface ApnRequest {
    val sim: SimInfo?

    data class AddDun(val apn: ApnSummary, override val sim: SimInfo) : ApnRequest
    data class NamedCopy(
        val apn: ApnSummary,
        override val sim: SimInfo,
        val name: String = ""
    ) : ApnRequest
    data class Configure(val form: ApnFormState, override val sim: SimInfo?) : ApnRequest
}

internal val ApnRequest.successMessageRes: Int
    @StringRes get() = when (this) {
        is ApnRequest.AddDun -> R.string.copy_apn_success
        is ApnRequest.NamedCopy -> R.string.apn_named_copy_success
        is ApnRequest.Configure -> R.string.apn_apply_success
    }

internal suspend fun ApnRequest.execute(manager: ApnManager): Result<Unit> = when (this) {
    is ApnRequest.AddDun -> manager.addDunToApn(sim.subscriptionId, apn.id)
    is ApnRequest.NamedCopy -> manager.createNamedApnCopy(sim.subscriptionId, apn.id, name)
    is ApnRequest.Configure -> manager.applyApnConfig(form.carrier, form.toApnContent(), sim?.subscriptionId)
}
