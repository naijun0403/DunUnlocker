package dev.naijun.dununlocker.data

import android.telephony.data.ApnSetting
import java.util.Locale

internal object ApnTypes {
    private val getApnTypeIntMethod by lazy {
        ApnSetting::class.java.getMethod("getApnTypeInt", String::class.java)
    }
    private val getApnTypesBitmaskFromStringMethod by lazy {
        ApnSetting::class.java.getMethod("getApnTypesBitmaskFromString", String::class.java)
    }

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
        if (requestedTypes.any { getApnTypeInt(it) == 0 }) return false
        val requestedMask = getApnTypesBitmaskFromString(requestedTypes.joinToString(","))
        val actualMask = getApnTypesBitmaskFromString(normalize(actual).joinToString(","))
        return requestedMask != 0 && (actualMask and requestedMask) == requestedMask
    }

    private fun getApnTypeInt(type: String): Int =
        getApnTypeIntMethod.invoke(null, type) as Int

    private fun getApnTypesBitmaskFromString(types: String): Int =
        getApnTypesBitmaskFromStringMethod.invoke(null, types) as Int

    private fun normalize(types: String?): Set<String> = entries(types)
        .map { it.lowercase(Locale.ROOT) }.toSet()
}
