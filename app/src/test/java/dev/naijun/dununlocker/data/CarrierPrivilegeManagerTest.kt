package dev.naijun.dununlocker.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarrierPrivilegeManagerTest {

    private val selfRule = "0123456789abcdef:dev.naijun.dununlocker"
    private val carrierRule = "fedcba9876543210:com.carrier.app"
    private val thirdPartyRule = "aaaaaaaaaaaaaaaa:com.thirdparty.tool"

    @Test
    fun containsSelfCertificateRule_matchesCaseInsensitively() {
        val existing = listOf(carrierRule, "0123456789ABCDEF:dev.naijun.dununlocker")
        assertTrue(CarrierPrivilegeManager.containsSelfCertificateRule(existing, listOf(selfRule)))
        assertFalse(CarrierPrivilegeManager.containsSelfCertificateRule(listOf(carrierRule), listOf(selfRule)))
    }

    @Test
    fun removeSelfCertificateRules_preservesCarrierAndThirdPartyRules() {
        val existing = listOf(carrierRule, "0123456789ABCDEF:dev.naijun.dununlocker", thirdPartyRule)
        val cleaned = CarrierPrivilegeManager.removeSelfCertificateRules(existing, listOf(selfRule))
        assertEquals(listOf(carrierRule, thirdPartyRule), cleaned)
    }

    @Test
    fun mergeCertificateRules_appendsSelfWithoutDuplicating() {
        val base = listOf(carrierRule, thirdPartyRule)
        val merged = CarrierPrivilegeManager.mergeCertificateRules(base, listOf(selfRule))
        assertArrayEquals(arrayOf(carrierRule, thirdPartyRule, selfRule), merged)

        val mergedAgain = CarrierPrivilegeManager.mergeCertificateRules(
            merged.toList(),
            listOf("0123456789ABCDEF:dev.naijun.dununlocker")
        )
        assertArrayEquals(arrayOf(carrierRule, thirdPartyRule, selfRule), mergedAgain)
    }
}
