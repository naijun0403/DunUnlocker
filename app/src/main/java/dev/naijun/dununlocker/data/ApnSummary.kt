package dev.naijun.dununlocker.data

data class ApnSummary(
    val id: Long,
    val name: String,
    val apn: String,
    val type: String,
    val isPreferred: Boolean
)
