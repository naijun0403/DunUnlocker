package dev.naijun.dununlocker.data

import android.content.ContentValues
import org.junit.Assert.*
import org.junit.Test

class NamedApnCopyTest {
    private fun source() = ContentValues().apply {
        put("_id", 10L)
        put("name", "Internet")
        put("apn", "internet.example")
        put("numeric", "45006")
        put("mcc", "450")
        put("mnc", "06")
        put("carrier_id", -1)
        put("type", "default,supl")
        put("profile_id", 0)
        put("modem_cognitive", 0)
        put("protocol", "IPV4V6")
        put("user", "subscriber")
        put("password", "test-password")
        put("user_visible", 1)
    }

    @Test fun rejectsReservedAndPersistentModemProfiles() {
        for (original in listOf(
            source().apply { put("profile_id", 1) },
            source().apply { put("profile_id", 2) },
            source().apply { put("modem_cognitive", 1) },
            source().apply { remove("profile_id") }
        )) {
            val failure = assertThrows(NamedApnCopy.Rejected::class.java) {
                NamedApnCopy.prepare(original, listOf(original), "New name")
            }
            assertEquals(NamedApnCopy.Failure.UNSUPPORTED_PROFILE, failure.reason)
        }
    }

    @Test fun allowsAnotherCarriersProfileWithTheSameAddressAndName() {
        val original = source().apply { put("apn", "internet") }
        val verizon = ContentValues(original).apply {
            put("numeric", "311480")
            put("mcc", "311")
            put("mnc", "480")
            put("profile_id", 1)
            put("name", "My copy")
        }
        val copy = NamedApnCopy.prepare(original, listOf(original, verizon), "My copy")
        assertEquals("45006", copy.getAsString("numeric"))
        assertEquals("My copy", copy.getAsString("name"))
        assertEquals(1, copy.getAsInteger("profile_id"))
    }

    @Test fun rejectsTheSameKeyDespiteDifferentNonUniqueFields() {
        val original = source().apply { put("owned_by", 0) }
        val existing = ContentValues(original).apply {
            put("profile_id", 1)
            put("owned_by", 1)
            put("name", "Existing DUN")
            put("type", "dun")
            put("sub_id", 99)
            put("user", "other")
            put("password", "other")
            put("user_visible", 0)
            put("carrier_enabled", true)
            put("user_editable", 1L)
            put("esim_bootstrap_provisioning", false)
            put("proxy", "")
            put("roaming_protocol", "IP")
        }
        assertEquals(NamedApnCopy.Failure.EXISTING_COPY,
            assertThrows(NamedApnCopy.Rejected::class.java) {
                NamedApnCopy.prepare(original, listOf(existing), "My copy")
            }.reason)
    }

    @Test fun copiesAlreadySupportedTypesWithoutNarrowingOrDuplicatingThem() {
        for (type in listOf("", "*", "default,DUN")) {
            val original = source().apply { put("type", type) }
            val copy = NamedApnCopy.prepare(original, listOf(original), "New name")
            assertEquals(type, copy.getAsString("type"))
            assertEquals("New name", copy.getAsString("name"))
            assertFalse(copy.containsKey("_id"))
        }
    }
}
