package dev.naijun.dununlocker.data

/** Only display fields cross the broker boundary; credentials stay in the provider. */
data class ApnSummary(
    val id: Long,
    val name: String,
    val apn: String,
    val type: String,
    val isPreferred: Boolean
)
