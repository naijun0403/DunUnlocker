package dev.naijun.dununlocker.data

import android.content.ContentValues

internal object ApnIdentity {
    private val textKeyDefaults = mapOf(
        "numeric" to "", "mcc" to "", "mnc" to "",
        "apn" to "", "proxy" to "", "port" to "",
        "mmsproxy" to "", "mmsport" to "", "mmsc" to "",
        "mvno_type" to "", "mvno_match_data" to "",
        "protocol" to "IP", "roaming_protocol" to "IP"
    )
    private val integerKeyDefaults = mapOf(
        "carrier_enabled" to "1", "bearer" to "0", "profile_id" to "0",
        "user_editable" to "1", "owned_by" to "1", "apn_set_id" to "0",
        "carrier_id" to "-1", "infrastructure_bitmask" to "3",
        "esim_bootstrap_provisioning" to "0"
    )
    private val uniqueKeyDefaults = textKeyDefaults + integerKeyDefaults

    fun matches(requested: ContentValues, existing: ContentValues): Boolean {
        fun value(row: ContentValues, column: String, default: String): String? {
            val raw = if (row.containsKey(column)) row.getAsString(column) else default
            if (column !in integerKeyDefaults) return raw
            return when {
                raw.equals("true", ignoreCase = true) -> "1"
                raw.equals("false", ignoreCase = true) -> "0"
                else -> raw?.toLongOrNull()?.toString() ?: raw
            }
        }
        return uniqueKeyDefaults.all { (column, default) ->
            val expected = value(requested, column, default)
            expected != null && expected == value(existing, column, default)
        }
    }
}
