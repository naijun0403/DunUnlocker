package dev.naijun.dununlocker.data

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

    private fun normalize(types: String?): Set<String> = entries(types)
        .map { it.lowercase(Locale.ROOT) }.toSet()
}
