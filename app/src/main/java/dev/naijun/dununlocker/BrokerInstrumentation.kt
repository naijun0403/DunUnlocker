package dev.naijun.dununlocker

import android.Manifest
import android.app.IActivityManager
import android.app.Instrumentation
import android.content.ContentValues
import android.content.Context
import android.database.DatabaseUtils
import android.os.Bundle
import android.net.Uri
import android.content.ContentUris
import android.provider.Telephony
import android.system.Os
import android.telephony.TelephonyManager
import android.util.Log
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import androidx.core.net.toUri
import dev.naijun.dununlocker.util.OneUiUtils
import dev.naijun.dununlocker.data.NamedApnCopy
import dev.naijun.dununlocker.data.ApnTypes
import org.lsposed.hiddenapibypass.HiddenApiBypass
import org.lsposed.lsparanoid.Obfuscate

const val UNLOCKER_TAG = "BrokerInstrumentation"

private const val OPERATION_KEY = "operation"
private const val OPERATION_CREATE = "create"
private const val OPERATION_COPY = "copy_apn"
private const val OPERATION_LIST = "list_apns"

@Obfuscate
class BrokerInstrumentation : Instrumentation() {

    override fun onCreate(arguments: Bundle?) {
        HiddenApiBypass.setHiddenApiExemptions("")

        super.onCreate(arguments)

        if (arguments == null) {
            Log.e(UNLOCKER_TAG, "Arguments is null")
            finish(-1, Bundle())
            return
        }

        val am = IActivityManager.Stub.asInterface(
            ShizukuBinderWrapper(
                SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)
            )
        )
        try {
            am.startDelegateShellPermissionIdentity(
                Os.getuid(),
                arrayOf(
                    Manifest.permission.WRITE_APN_SETTINGS,
                    Manifest.permission.READ_PRIVILEGED_PHONE_STATE
                )
            )

            val operation = arguments.getString(OPERATION_KEY) ?: OPERATION_CREATE
            val subId = arguments.getInt("sub_id", -1)

            when (operation) {
                OPERATION_LIST -> {
                    require(subId >= 0)
                    listApns(subId)
                    return
                }
                OPERATION_COPY -> {
                    require(subId >= 0)
                    val sourceId = arguments.getLong("source_apn_id", -1)
                    require(sourceId >= 0)
                    copyApnWithDun(subId, sourceId, arguments.getString("copy_name"))
                    return
                }
            }

            val name = arguments.getString("name") ?: "DUN"
            val numeric = arguments.getString("numeric") ?: "45005"
            val mcc = arguments.getString("mcc") ?: "450"
            val mnc = arguments.getString("mnc") ?: "05"
            val apn = arguments.getString("apn") ?: ""
            val type = arguments.getString("type") ?: "default,mms,supl,rcs,dun"
            val protocol = arguments.getString("protocol") ?: "IPV4V6"
            val mmsc = arguments.getString("mmsc") ?: ""
            val mmsProxy = arguments.getString("mms_proxy") ?: ""
            val mmsPort = arguments.getString("mms_port") ?: ""
            val roamingProtocol = arguments.getString("roaming_protocol") ?: "IPV4V6"
            val server = arguments.getString("server") ?: "*"
            val authType = arguments.getString("auth_type") ?: "0"
            val user = arguments.getString("user") ?: ""
            val password = arguments.getString("password") ?: ""
            if (apn.isEmpty()) {
                Log.e(UNLOCKER_TAG, "APN address is empty")
                finish(-2, Bundle())
                return
            }

            val telephonyManager = context.getSystemService(TelephonyManager::class.java)

            val apnTypeSets = type.split(",")
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
                put("name", name)
                if (!OneUiUtils.isOneUi() || (OneUiUtils.getOneUiInfo().versionCode ?: 0) < 80500) {
                    put("numeric", numeric)
                }
                put("mcc", mcc)
                put("mnc", mnc)
                put("apn", apn)
                put("type", apnTypeSets.joinToString(","))
                put("protocol", protocol)
                put("roaming_protocol", roamingProtocol)
                put("authtype", authType.toIntOrNull() ?: 0)
                put("carrier_id", telephonyManager.createForSubscriptionId(subId).simSpecificCarrierId)
                put("user_visible", 1)
                put("current", 1)
                put("always_on", 1)

                if (subId >= 0) {
                    put("sub_id", subId)
                }

                if (server.isNotEmpty()) put("server", server)

                if (user.isNotEmpty()) put("user", user)
                if (password.isNotEmpty()) put("password", password)

                if (mmsc.isNotEmpty()) put("mmsc", mmsc)
                if (mmsProxy.isNotEmpty()) put("mmsproxy", mmsProxy)
                if (mmsPort.isNotEmpty()) put("mmsport", mmsPort)
            }

            val insertedUri = contentResolver.insert(uri, values)

            if (insertedUri != null) {
                try {
                    val apnId = insertedUri.lastPathSegment
                    val preferredUri = if (subId >= 0) {
                        "content://telephony/carriers/preferapn/subId/$subId".toUri()
                    } else {
                        "content://telephony/carriers/preferapn".toUri()
                    }
                    val preferredValues = ContentValues().apply {
                        put("apn_id", apnId)
                    }
                    contentResolver.update(preferredUri, preferredValues, null, null)
                } catch (e: Exception) {
                    Log.w(UNLOCKER_TAG, "Failed to set preferred APN: ${e.message}")
                }

                finish(0, Bundle().apply {
                    putString("inserted_uri", insertedUri.toString())
                })
            } else {
                Log.e(UNLOCKER_TAG, "Failed to insert APN")
                finish(-3, Bundle())
            }

        } catch (e: Exception) {
            Log.e(UNLOCKER_TAG, "Error in BrokerInstrumentation", e)
            finish(-100, Bundle().apply {
                putString("error", e.message ?: "Unknown error")
            })
        } finally {
            try {
                am.stopDelegateShellPermissionIdentity()
            } catch (e: Exception) {
                Log.w(UNLOCKER_TAG, "Failed to stop delegate permission: ${e.message}")
            }
        }
    }

    private fun simApnsUri(subId: Int): Uri =
        Uri.withAppendedPath(Telephony.Carriers.SIM_APN_URI, subId.toString())

    private fun preferredUri(subId: Int): Uri =
        "content://telephony/carriers/preferapn/subId/$subId".toUri()

    private fun preferredId(subId: Int): Long? = context.contentResolver.query(
        preferredUri(subId), arrayOf("_id"), null, null, null
    )?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

    private fun listApns(subId: Int) {
        val preferredId = try {
            preferredId(subId)
        } catch (e: Exception) {
            Log.w(UNLOCKER_TAG, "Could not read preferred APN", e)
            null
        }
        val rows = arrayListOf<Bundle>()
        val cursor = context.contentResolver.query(
            simApnsUri(subId), arrayOf("_id", "name", "apn", "type"),
            null, null, "name ASC, _id ASC"
        ) ?: error(context.getString(R.string.apn_list_error))
        cursor.use {
            while (it.moveToNext()) {
                rows.add(Bundle().apply {
                    putLong("id", it.getLong(0))
                    putString("name", it.getString(1))
                    putString("apn", it.getString(2))
                    putString("type", it.getString(3))
                    putBoolean("preferred", it.getLong(0) == preferredId)
                })
            }
        }
        finish(0, Bundle().apply { putParcelableArrayList("apns", rows) })
    }

    private fun copyApnWithDun(subId: Int, sourceId: Long, copyName: String?) {
        val resolver = context.contentResolver
        val values = resolver.query(
            simApnsUri(subId), null, "_id=?", arrayOf(sourceId.toString()), null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            ContentValues().also { DatabaseUtils.cursorRowToContentValues(cursor, it) }
        } ?: error(context.getString(R.string.apn_source_missing))

        if (copyName != null) {
            createNamedCopy(subId, values, copyName)
            return
        }

        val types = values.getAsString("type")
        val typesWithDun = ApnTypes.withDun(types)
        if (typesWithDun == types) {
            finish(0, Bundle())
            return
        }

        val wasPreferred = preferredId(subId) == sourceId
        values.put("type", typesWithDun)
        values.remove("_id")
        values.remove("edited")
        values.remove("owned_by")
        values.put("sub_id", subId)
        val insertedUri = resolver.insert(Telephony.Carriers.CONTENT_URI, values)

        val targetId = insertedUri?.let { ContentUris.parseId(it) }
            ?: findCopiedApnId(subId, values)
            ?: error(context.getString(R.string.apn_copy_failed))

        if (wasPreferred && targetId != sourceId) {
            resolver.update(preferredUri(subId), ContentValues().apply {
                put("apn_id", targetId)
            }, null, null)
        }
        finish(0, Bundle())
    }

    private fun createNamedCopy(subId: Int, source: ContentValues, name: String) {
        val copyId = try {
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
        finish(0, Bundle().apply { putLong("copied_apn_id", copyId) })
    }

    private fun findCopiedApnId(subId: Int, expected: ContentValues): Long? {
        return context.contentResolver.query(
            simApnsUri(subId), null, "apn=?",
            arrayOf(expected.getAsString("apn").orEmpty()), null
        )?.use { cursor ->
            val matches = mutableListOf<Long>()
            while (cursor.moveToNext()) {
                val actual = ContentValues().also {
                    DatabaseUtils.cursorRowToContentValues(cursor, it)
                }
                val matchesFields = expected.keySet().all { key ->
                    when (key) {
                        "type" -> ApnTypes.equivalent(actual.getAsString(key), expected.getAsString(key))
                        // AOSP may assign profile 1 when creating a separate DUN row.
                        "profile_id" -> actual.getAsString(key) == expected.getAsString(key) ||
                            (expected.getAsInteger(key) == 0 && actual.getAsInteger(key) == 1)
                        else -> actual.getAsString(key) == expected.getAsString(key)
                    }
                }
                if (matchesFields) matches.add(actual.getAsLong("_id"))
            }
            matches.singleOrNull()
        }
    }

}
