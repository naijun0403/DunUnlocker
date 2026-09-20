package dev.naijun.dununlocker.data

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.MatrixCursor
import android.net.Uri
import android.provider.Telephony
import org.junit.Assert.*
import org.junit.Test

class NamedApnCopySaveTest {
    private fun source() = ContentValues().apply {
        put("_id", 10L)
        put("name", "Internet")
        put("apn", "internet")
        put("numeric", "45006")
        put("mcc", "450")
        put("mnc", "06")
        put("profile_id", 0)
        put("modem_cognitive", 0)
        put("owned_by", 1)
        put("type", "default,supl")
        put("user", "subscriber")
        put("password", "test-password")
        put("edited", Telephony.Carriers.UNEDITED)
    }

    @Test fun verifiesAnInsertedRowBeforeReturningItsId() {
        val original = source()
        val provider = TestProvider(original)
        assertEquals(20L, NamedApnCopy.save(ContentResolver.wrap(provider), original, 1, "My copy"))
        assertEquals(original, provider.rows.first())
        assertEquals("My copy", provider.rows.last().getAsString("name"))
        assertEquals("default,supl,dun", provider.rows.last().getAsString("type"))
    }

    @Test fun verifiesASoftDeletedRowRestoredWithANullUri() {
        val original = source()
        val provider = TestProvider(original).apply {
            rows.add(ContentValues(original).apply {
                put("_id", 20L)
                put("profile_id", 1)
                put("name", "Deleted copy")
                put("edited", Telephony.Carriers.USER_DELETED)
                put("type", "dun,default,supl")
            })
            onInsert = { values ->
                val restored = rows.last()
                restored.putAll(values)
                restored.put("edited", Telephony.Carriers.CARRIER_EDITED)
                restored.put("type", "DUN,default,supl")
                null
            }
        }
        assertEquals(20L, NamedApnCopy.save(ContentResolver.wrap(provider), original, 1, "My copy"))
        assertEquals(2, provider.rows.size)
        assertEquals(original, provider.rows.first())
        assertEquals("My copy", provider.rows.last().getAsString("name"))
        assertNotEquals(Telephony.Carriers.USER_DELETED, provider.rows.last().getAsInteger("edited"))
    }

    @Test fun rejectsNullWithoutASavedCopy() {
        val original = source()
        val provider = TestProvider(original).apply { onInsert = { null } }
        assertUnverified(provider, original)
    }

    @Test fun rejectsIncorrectSavedFieldsWithOrWithoutAnInsertedUri() {
        for (returnUri in listOf(false, true)) {
            val original = source()
            val provider = TestProvider(original).apply {
                onInsert = { values ->
                    addCopy(values).put("name", "Wrong name")
                    if (returnUri) copyUri else null
                }
            }
            assertUnverified(provider, original)
        }
    }

    @Test fun rejectsAChangedSourceWithOrWithoutAnInsertedUri() {
        for (returnUri in listOf(false, true)) {
            val original = source()
            val provider = TestProvider(original).apply {
                onInsert = { values ->
                    addCopy(values)
                    rows.first().put("name", "Changed")
                    if (returnUri) copyUri else null
                }
            }
            assertUnverified(provider, original)
        }
    }

    private fun assertUnverified(provider: TestProvider, original: ContentValues) {
        val failure = assertThrows(NamedApnCopy.Rejected::class.java) {
            NamedApnCopy.save(ContentResolver.wrap(provider), original, 1, "My copy")
        }
        assertEquals(NamedApnCopy.Failure.UNVERIFIED, failure.reason)
    }

    private class TestProvider(original: ContentValues) : ContentProvider() {
        val rows = mutableListOf(ContentValues(original))
        val copyUri: Uri = ContentUris.withAppendedId(Telephony.Carriers.CONTENT_URI, 20L)
        var onInsert: (ContentValues) -> Uri? = { values ->
            addCopy(values)
            copyUri
        }

        fun addCopy(values: ContentValues): ContentValues = ContentValues(values).apply {
            put("_id", 20L)
            put("edited", Telephony.Carriers.CARRIER_EDITED)
            rows.add(this)
        }

        override fun query(
            uri: Uri, projection: Array<out String>?, selection: String?,
            selectionArgs: Array<out String>?, sortOrder: String?
        ): MatrixCursor {
            assertEquals(Telephony.Carriers.CONTENT_URI, uri)
            val column = requireNotNull(selection).removeSuffix("=?")
            val columns = rows.flatMap { it.keySet() }.distinct().toTypedArray()
            return MatrixCursor(columns).apply {
                rows.filter {
                    it.getAsInteger("edited") != Telephony.Carriers.USER_DELETED &&
                        it.getAsString(column) == selectionArgs?.single()
                }.forEach { row -> addRow(columns.map { row.get(it) }) }
            }
        }

        override fun insert(uri: Uri, values: ContentValues?): Uri? {
            assertEquals(Telephony.Carriers.CONTENT_URI, uri)
            return onInsert(requireNotNull(values))
        }

        override fun onCreate() = true
        override fun getType(uri: Uri): String? = null
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
            error("Copying must not delete APNs")
        override fun update(
            uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?
        ): Int = error("Copying must not update APNs or the preferred selection")
    }
}
