package dev.naijun.dununlocker.ui.model

import androidx.annotation.StringRes
import dev.naijun.dununlocker.R
import dev.naijun.dununlocker.domain.model.ApnContent
import dev.naijun.dununlocker.domain.model.CarrierType

data class ApnFormState(
    val carrier: CarrierType,
    val content: ApnContent,
    val useMmsSettings: Boolean
) {
    fun missingFieldLabels(): List<Int> = buildList {
        if (content.name.isBlank()) add(R.string.validation_error_name)
        if (content.apn.isBlank()) add(R.string.validation_error_apn_address)
        if (content.type.isBlank()) add(R.string.validation_error_apn_type)
        if (content.mcc.isBlank()) add(R.string.validation_error_mcc)
        if (content.mnc.isBlank()) add(R.string.validation_error_mnc)
        if (useMmsSettings) {
            if (content.mmsc.isBlank()) add(R.string.validation_error_mmsc)
            if (content.mmsPort.isBlank()) add(R.string.validation_error_mms_port)
        }
    }

    fun toApnContent(): ApnContent = content.copy(
        numeric = content.mcc + content.mnc,
        mmsc = content.mmsc.takeIf { useMmsSettings }.orEmpty(),
        mmsProxy = content.mmsProxy.takeIf { useMmsSettings }.orEmpty(),
        mmsPort = content.mmsPort.takeIf { useMmsSettings }.orEmpty()
    )

    companion object {
        fun forCarrier(carrier: CarrierType): ApnFormState {
            val content = ApnContent.getDefaultConfig(carrier)
            return ApnFormState(
                carrier = carrier,
                content = content,
                useMmsSettings = listOf(content.mmsc, content.mmsProxy, content.mmsPort).any { it.isNotEmpty() }
            )
        }
    }
}

val CarrierType.labelRes: Int
    @StringRes get() = when (this) {
        CarrierType.SKT_5G -> R.string.carrier_skt_5g
        CarrierType.SKT_LTE -> R.string.carrier_skt_lte
        CarrierType.KT_5G -> R.string.carrier_kt_5g
        CarrierType.KT_LTE -> R.string.carrier_kt_lte
        CarrierType.LGU_PLUS_5G -> R.string.carrier_lgu_5g
        CarrierType.LGU_PLUS_LTE -> R.string.carrier_lgu_lte
        CarrierType.CUSTOM -> R.string.carrier_custom
    }
