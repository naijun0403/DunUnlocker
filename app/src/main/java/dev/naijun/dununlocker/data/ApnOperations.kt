package dev.naijun.dununlocker.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.net.Uri
import android.provider.Telephony
import android.telephony.TelephonyManager
import android.util.Log
import androidx.core.net.toUri
import dev.naijun.dununlocker.R
import dev.naijun.dununlocker.domain.model.ApnContent
import dev.naijun.dununlocker.util.OneUiUtils

internal object ApnOperations {
    private const val TAG = "ApnOperations"

    fun simApnsUri(subId: Int): Uri =
        Uri.withAppendedPath(Telephony.Carriers.SIM_APN_URI, subId.toString())

    fun preferredUri(subId: Int): Uri =
        if (subId >= 0) {
            "content://telephony/carriers/preferapn/subId/$subId".toUri()
        } else {
            "content://telephony/carriers/preferapn".toUri()
        }

    fun preferredId(context: Context, subId: Int): Long? = context.contentResolver.query(
        preferredUri(subId), arrayOf("_id"), null, null, null
    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

    fun listApns(context: Context, subId: Int): List<ApnSummary> {
        require(subId >= 0)
        val preferredId = try {
            preferredId(context, subId)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read preferred APN", e)
            null
        }
        val cursor = context.contentResolver.query(
            simApnsUri(subId), arrayOf("_id", "name", "apn", "type"),
            null, null, "name ASC, _id ASC"
        ) ?: error(context.getString(R.string.apn_list_error))
        return cursor.use {
            buildList {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    add(
                        ApnSummary(
                            id = id,
                            name = it.getString(1).orEmpty(),
                            apn = it.getString(2).orEmpty(),
                            type = it.getString(3).orEmpty(),
                            isPreferred = id == preferredId
                        )
                    )
                }
            }
        }
    }

    fun copyApnWithDun(
        context: Context,
        subId: Int,
        sourceId: Long,
        copyName: String?
    ): Long? {
        require(subId >= 0)
        require(sourceId >= 0)
        val resolver = context.contentResolver
        val values = resolver.query(
            simApnsUri(subId), null, "_id=?", arrayOf(sourceId.toString()), null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            ContentValues().also { DatabaseUtils.cursorRowToContentValues(cursor, it) }
        } ?: error(context.getString(R.string.apn_source_missing))

        if (copyName != null) {
            return createNamedCopy(context, subId, values, copyName)
        }

        val types = values.getAsString("type")
        val typesWithDun = ApnTypes.withDun(types)
        if (typesWithDun == types) {
            return sourceId
        }

        val wasPreferred = try {
            preferredId(context, subId) == sourceId
        } catch (e: Exception) {
            Log.w(TAG, "Could not read preferred APN before copy", e)
            false
        }
        values.put("type", typesWithDun)
        values.remove("_id")
        values.remove("edited")
        values.remove("owned_by")
        values.put("sub_id", subId)
        val insertedUri = resolver.insert(Telephony.Carriers.CONTENT_URI, values)

        val targetId = insertedUri?.let { ContentUris.parseId(it) }
            ?: findCopiedApnId(context, subId, values)
            ?: error(context.getString(R.string.apn_copy_failed))

        if (wasPreferred && targetId != sourceId) {
            resolver.update(preferredUri(subId), ContentValues().apply {
                put("apn_id", targetId)
            }, null, null)
        }
        return targetId
    }

    fun createApn(context: Context, content: ApnContent, subId: Int): Uri {
        require(content.apn.isNotEmpty()) { "APN address is empty" }

        val telephonyManager = context.getSystemService(TelephonyManager::class.java)

        val apnTypeSets = content.type.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()

        val contentResolver = context.contentResolver

        val uri = if (subId >= 0) {
            "content://telephony/carriers/subId/$subId".toUri()
        } else {
            "content://telephony/carriers".toUri()
        }

        val values = ContentValues().apply {
            put("name", content.name)
            if (!OneUiUtils.isOneUi() || (OneUiUtils.getOneUiInfo().versionCode ?: 0) < 80500) {
                put("numeric", content.numeric)
            }
            put("mcc", content.mcc)
            put("mnc", content.mnc)
            put("apn", content.apn)
            put("type", apnTypeSets.joinToString(","))
            put("protocol", content.protocol)
            put("roaming_protocol", content.roamingProtocol)
            put("authtype", content.authType.toIntOrNull() ?: 0)
            put("carrier_id", telephonyManager.createForSubscriptionId(subId).simSpecificCarrierId)
            put("user_visible", 1)
            put("current", 1)
            put("always_on", 1)

            if (subId >= 0) {
                put("sub_id", subId)
            }

            if (content.server.isNotEmpty()) put("server", content.server)
            if (content.user.isNotEmpty()) put("user", content.user)
            if (content.password.isNotEmpty()) put("password", content.password)
            if (content.mmsc.isNotEmpty()) put("mmsc", content.mmsc)
            if (content.mmsProxy.isNotEmpty()) put("mmsproxy", content.mmsProxy)
            if (content.mmsPort.isNotEmpty()) put("mmsport", content.mmsPort)
        }

        val insertedUri = contentResolver.insert(uri, values)
        val apnId = insertedUri?.let(ContentUris::parseId)
            ?: findMergedApnId(context, subId, values)
            ?: error(context.getString(R.string.apn_save_unverified))

        val preferred = preferredUri(subId)
        try {
            contentResolver.update(preferred, ContentValues().apply {
                put("apn_id", apnId)
            }, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set preferred APN", e)
        }

        return insertedUri ?: ContentUris.withAppendedId(Telephony.Carriers.CONTENT_URI, apnId)
    }

    private fun createNamedCopy(
        context: Context,
        subId: Int,
        source: ContentValues,
        name: String
    ): Long {
        return try {
            NamedApnCopy.save(context.contentResolver, source, subId, name)
        } catch (e: NamedApnCopy.Rejected) {
            val message = when (e.reason) {
                NamedApnCopy.Failure.INVALID_NAME -> R.string.apn_copy_name_error
                NamedApnCopy.Failure.UNSUPPORTED_PROFILE -> R.string.apn_named_copy_unsupported
                NamedApnCopy.Failure.EXISTING_COPY -> R.string.apn_named_copy_exists
                NamedApnCopy.Failure.READ_FAILED -> R.string.apn_list_error
                NamedApnCopy.Failure.UNVERIFIED -> R.string.apn_named_copy_unverified
            }
            error(context.getString(message))
        }
    }

    private fun findMergedApnId(context: Context, subId: Int, expected: ContentValues): Long? {
        val uri = if (subId >= 0) simApnsUri(subId) else Telephony.Carriers.CONTENT_URI
        return findMatchingApnId(context, uri, expected.getAsString("apn").orEmpty()) { actual ->
            ApnIdentity.matches(expected, actual) && expected.keySet().all { key ->
                when (key) {
                    // The SIM query already scopes the result. The provider owns the current flag.
                    "sub_id", "current" -> true
                    "type" -> ApnTypes.includesAll(actual.getAsString(key), expected.getAsString(key))
                    else -> actual.getAsString(key) == expected.getAsString(key)
                }
            }
        }
    }

    private fun findCopiedApnId(context: Context, subId: Int, expected: ContentValues): Long? =
        findMatchingApnId(context, simApnsUri(subId), expected.getAsString("apn").orEmpty()) { actual ->
            expected.keySet().all { key ->
                when (key) {
                    "type" -> ApnTypes.equivalent(actual.getAsString(key), expected.getAsString(key))
                    // AOSP may assign profile 1 when creating a separate DUN row.
                    "profile_id" -> actual.getAsString(key) == expected.getAsString(key) ||
                        (expected.getAsInteger(key) == 0 && actual.getAsInteger(key) == 1)
                    else -> actual.getAsString(key) == expected.getAsString(key)
                }
            }
        }

    private fun findMatchingApnId(
        context: Context,
        uri: Uri,
        address: String,
        matches: (ContentValues) -> Boolean
    ): Long? {
        return context.contentResolver.query(
            uri, null, "apn=?", arrayOf(address), null
        )?.use { cursor ->
            var matchedId: Long? = null
            while (cursor.moveToNext()) {
                val actual = ContentValues().also {
                    DatabaseUtils.cursorRowToContentValues(cursor, it)
                }
                if (matches(actual)) {
                    val id = actual.getAsLong("_id") ?: return@use null
                    if (matchedId != null) return@use null
                    matchedId = id
                }
            }
            matchedId
        }
    }
}
