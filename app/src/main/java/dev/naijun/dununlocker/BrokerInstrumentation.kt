package dev.naijun.dununlocker

import android.Manifest
import android.app.IActivityManager
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import android.system.Os
import android.util.Log
import dev.naijun.dununlocker.data.ApnManager
import dev.naijun.dununlocker.data.ApnOperations
import dev.naijun.dununlocker.domain.model.ApnContent
import org.lsposed.hiddenapibypass.HiddenApiBypass
import org.lsposed.lsparanoid.Obfuscate
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

const val UNLOCKER_TAG = "BrokerInstrumentation"

private const val OPERATION_KEY = "operation"
private const val OPERATION_CREATE = "create"
private const val OPERATION_COPY = "copy_apn"
private const val OPERATION_LIST = "list_apns"

@Obfuscate
class BrokerInstrumentation : Instrumentation() {

    private class ApnPermissionDeniedException(
        message: String,
        cause: Throwable
    ) : Exception(message, cause)

    override fun onCreate(arguments: Bundle?) {
        HiddenApiBypass.setHiddenApiExemptions("")

        super.onCreate(arguments)

        if (arguments == null) {
            Log.e(UNLOCKER_TAG, "Arguments is null")
            finish(-1, Bundle())
            return
        }

        val result = try {
            Result.success(executeWithShizukuDelegation(arguments))
        } catch (e: Exception) {
            Log.e(UNLOCKER_TAG, "Error in BrokerInstrumentation", e)
            Result.failure(e)
        }

        val resultCode = if (result.isSuccess) 0 else -100
        val response = result.getOrElse { error ->
            Bundle().apply {
                putString("error", error.message ?: "Unknown error")
                if (error is ApnPermissionDeniedException) {
                    putBoolean(ApnManager.BROKER_APN_PERMISSION_DENIED, true)
                }
            }
        }
        val requestId = arguments.getString(ApnManager.BROKER_REQUEST_ID)
        if (ApnManager.hasLocalBrokerRequest(requestId)) {
            try {
                // Avoid sending large APN lists through the watcher when the client is in-process.
                finish(resultCode, Bundle().apply { putBoolean(ApnManager.BROKER_LOCAL_RESULT, true) })
            } finally {
                ApnManager.onBrokerFinished(requestId, resultCode, response)
            }
        } else {
            finish(resultCode, response)
        }
    }

    private fun executeWithShizukuDelegation(arguments: Bundle): Bundle {
        val am = IActivityManager.Stub.asInterface(
            ShizukuBinderWrapper(
                SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)
            )
        )
        val uid = Os.getuid()
        var stopDelegation: Method? = null
        return try {
            val stopMethod = resolveStopDelegationMethod()
            am.startDelegateShellPermissionIdentity(
                uid,
                arrayOf(
                    Manifest.permission.WRITE_APN_SETTINGS,
                    Manifest.permission.READ_PRIVILEGED_PHONE_STATE
                )
            )
            stopDelegation = stopMethod
            try {
                runApnOperation(arguments)
            } catch (e: SecurityException) {
                if (isTelephonyApnPermissionError(e)) {
                    throw ApnPermissionDeniedException(
                        e.message ?: "No permission to access APN settings",
                        e
                    )
                }
                throw e
            }
        } finally {
            stopDelegation?.let { method ->
                try {
                    if (method.parameterCount == 1) {
                        method.invoke(am, uid)
                    } else {
                        method.invoke(am)
                    }
                } catch (e: Exception) {
                    Log.w(UNLOCKER_TAG, "Failed to stop delegate permission", e)
                }
            }
        }
    }

    private fun runApnOperation(arguments: Bundle): Bundle {
        val operation = arguments.getString(OPERATION_KEY) ?: OPERATION_CREATE
        val subId = arguments.getInt("sub_id", -1)

        return when (operation) {
            OPERATION_LIST -> {
                val apns = ApnOperations.listApns(context, subId)
                val rows = ArrayList(
                    apns.map { apn ->
                        Bundle().apply {
                            putLong("id", apn.id)
                            putString("name", apn.name)
                            putString("apn", apn.apn)
                            putString("type", apn.type)
                            putBoolean("preferred", apn.isPreferred)
                        }
                    }
                )
                Bundle().apply {
                    putParcelableArrayList("apns", rows)
                }
            }
            OPERATION_COPY -> {
                val sourceId = arguments.getLong("source_apn_id", -1L)
                val copyName = arguments.getString("copy_name")
                ApnOperations.copyApnWithDun(context, subId, sourceId, copyName)
                Bundle()
            }
            else -> {
                val apn = arguments.getString("apn") ?: ""
                require(apn.isNotEmpty()) { "APN address is empty" }

                val content = ApnContent(
                    name = arguments.getString("name") ?: "DUN",
                    numeric = arguments.getString("numeric") ?: "45005",
                    mcc = arguments.getString("mcc") ?: "450",
                    mnc = arguments.getString("mnc") ?: "05",
                    apn = apn,
                    type = arguments.getString("type") ?: "default,mms,supl,rcs,dun",
                    protocol = arguments.getString("protocol") ?: "IPV4V6",
                    mmsc = arguments.getString("mmsc") ?: "",
                    mmsProxy = arguments.getString("mms_proxy") ?: "",
                    mmsPort = arguments.getString("mms_port") ?: "",
                    roamingProtocol = arguments.getString("roaming_protocol") ?: "IPV4V6",
                    server = arguments.getString("server") ?: "*",
                    authType = arguments.getString("auth_type") ?: "0",
                    user = arguments.getString("user") ?: "",
                    password = arguments.getString("password") ?: ""
                )

                ApnOperations.createApn(context, content, subId)
                Bundle()
            }
        }
    }

    private fun isTelephonyApnPermissionError(error: SecurityException): Boolean {
        val msg = error.message.orEmpty()
        return msg.contains("No permission to access APN", ignoreCase = true) ||
            msg.contains("No permission to write APN", ignoreCase = true) ||
            msg.contains("WRITE_APN_SETTINGS", ignoreCase = true)
    }

    private fun resolveStopDelegationMethod(): Method {
        val manager = IActivityManager::class.java
        val name = "stopDelegateShellPermissionIdentity"
        return try {
            manager.getMethod(name, Integer.TYPE)
        } catch (_: NoSuchMethodException) {
            manager.getMethod(name)
        }
    }
}
