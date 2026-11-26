package dev.naijun.dununlocker.data

import android.telephony.data.ApnSetting
import java.util.Locale

internal object ApnTypes {
    private fun entries(types: String?): List<String> = types.orEmpty().split(",")
        .map(String::trim).filter(String::isNotEmpty)

    fun withDun(types: String?): String? {
        val entries = entries(types)
        return if (entries.isEmpty() || entries.any { it == "*" || it.equals("dun", true) }) {
            types
        } else {
            (entries + "dun").joinToString(",")
        }
    }

    fun equivalent(first: String?, second: String?): Boolean = normalize(first) == normalize(second)

    fun includesAll(actual: String?, requested: String?): Boolean {
        val requestedTypes = normalize(requested)
        if (requestedTypes.any { ApnSetting.getApnTypeInt(it) == 0 }) return false
        val requestedMask = ApnSetting.getApnTypesBitmaskFromString(requestedTypes.joinToString(","))
        val actualMask = ApnSetting.getApnTypesBitmaskFromString(normalize(actual).joinToString(","))
        return requestedMask != 0 && (actualMask and requestedMask) == requestedMask
    }

    private fun normalize(types: String?): Set<String> = entries(types)
        .map { it.lowercase(Locale.ROOT) }.toSet()
}
