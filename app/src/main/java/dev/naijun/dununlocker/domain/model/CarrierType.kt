package dev.naijun.dununlocker.domain.model

enum class CarrierType(val displayName: String) {
    SKT_5G("SKT_5G"),
    SKT_LTE("SKT_LTE"),
    KT_5G("KT_5G"),
    KT_LTE("KT_LTE"),
    LGU_PLUS_5G("LG U+_5G"),
    LGU_PLUS_LTE("LG U+_LTE"),
    CUSTOM("CUSTOM");

    fun requiresMmsSettings(): Boolean {
        return when (this) {
            SKT_5G, KT_5G, LGU_PLUS_5G -> false
            SKT_LTE, KT_LTE, LGU_PLUS_LTE -> true
            CUSTOM -> false
        }
    }

    companion object {
        fun fromString(carrier: String): CarrierType? {
            return when (carrier) {
                "SKT (5G)" -> SKT_5G
                "SKT (LTE)" -> SKT_LTE
                "KT (5G)" -> KT_5G
                "KT (LTE)" -> KT_LTE
                "LG U+ (5G)" -> LGU_PLUS_5G
                "LG U+ (LTE)" -> LGU_PLUS_LTE
                "Custom" -> CUSTOM
                else -> null
            }
        }
    }
}
