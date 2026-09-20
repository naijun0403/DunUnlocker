package dev.naijun.dununlocker.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.DatabaseUtils
import android.provider.Telephony

internal object NamedApnCopy {
    enum class Failure { INVALID_NAME, UNSUPPORTED_PROFILE, EXISTING_COPY, READ_FAILED, UNVERIFIED }
    class Rejected(val reason: Failure) : IllegalArgumentException(reason.name)

    private val textKeyDefaults = mapOf(
        "numeric" to null, "mcc" to null, "mnc" to null,
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

    private fun conflicts(copy: ContentValues, existing: ContentValues): Boolean {
        fun value(row: ContentValues, column: String, default: String?): String? {
            val raw = if (row.containsKey(column)) row.getAsString(column) else default
            if (column !in integerKeyDefaults) return raw
            return when {
                raw.equals("true", ignoreCase = true) -> "1"
                raw.equals("false", ignoreCase = true) -> "0"
                else -> raw?.toLongOrNull()?.toString() ?: raw
            }
        }
        return uniqueKeyDefaults.all { (column, default) ->
            val expected = value(copy, column, default)
            expected != null && expected == value(existing, column, default)
        }
    }

    fun save(resolver: ContentResolver, source: ContentValues, subId: Int, name: String): Long {
        val sourceId = source.getAsLong("_id") ?: throw Rejected(Failure.UNVERIFIED)
        val address = source.getAsString("apn").orEmpty()
        val existing = readRows(resolver, "apn", address, Failure.READ_FAILED)
        val expected = prepare(source, existing, name).apply { put("sub_id", subId) }
        val inserted = resolver.insert(Telephony.Carriers.CONTENT_URI, ContentValues(expected))
        val candidates = if (inserted != null) {
            readRows(resolver, "_id", ContentUris.parseId(inserted).toString())
        } else {
            // AOSP can restore a soft-deleted row through a merge and return no URI.
            readRows(resolver, "apn", address)
        }
        val original = readRows(resolver, "_id", sourceId.toString()).singleOrNull()
        if (original == null || !matches(source, original)) throw Rejected(Failure.UNVERIFIED)

        val existingIds = existing.map { it.getAsLong("_id") }.toSet()
        val copy = candidates.filter {
            val id = it.getAsLong("_id")
            id != null && id != sourceId && id !in existingIds &&
                matches(expected, it, normalizeTypes = true)
        }.singleOrNull() ?: throw Rejected(Failure.UNVERIFIED)
        return copy.getAsLong("_id")
    }

    private fun readRows(
        resolver: ContentResolver,
        column: String,
        value: String,
        failure: Failure = Failure.UNVERIFIED
    ): List<ContentValues> = resolver.query(
        Telephony.Carriers.CONTENT_URI, null, "$column=?", arrayOf(value), null
    )?.use { cursor ->
        buildList {
            while (cursor.moveToNext()) {
                add(ContentValues().also { DatabaseUtils.cursorRowToContentValues(cursor, it) })
            }
        }
    } ?: throw Rejected(failure)

    private fun matches(
        expected: ContentValues,
        actual: ContentValues,
        normalizeTypes: Boolean = false
    ): Boolean = expected.keySet().all { column ->
        actual.containsKey(column) && if (normalizeTypes && column == "type") {
            ApnTypes.equivalent(expected.getAsString(column), actual.getAsString(column))
        } else {
            expected.getAsString(column) == actual.getAsString(column)
        }
    }

    fun prepare(source: ContentValues, sameAddressRows: List<ContentValues>, name: String): ContentValues {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty() || trimmedName == source.getAsString("name")) {
            throw Rejected(Failure.INVALID_NAME)
        }
        if (source.getAsInteger("profile_id") != 0 ||
            source.getAsInteger("modem_cognitive") != 0) {
            throw Rejected(Failure.UNSUPPORTED_PROFILE)
        }
        val copy = ContentValues(source).apply {
            remove("_id")
            remove("edited")
            put("owned_by", 1)
            put("name", trimmedName)
            put("profile_id", 1)
            put("user_visible", 1)
            put("type", ApnTypes.withDun(getAsString("type")))
        }
        if (sameAddressRows.any { conflicts(copy, it) }) {
            throw Rejected(Failure.EXISTING_COPY)
        }
        return copy
    }
}
