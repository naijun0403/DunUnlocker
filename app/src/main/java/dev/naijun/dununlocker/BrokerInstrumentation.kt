package dev.naijun.dununlocker

import android.Manifest
import android.app.IActivityManager
import android.app.Instrumentation
import android.content.Context
import android.os.Bundle
import android.system.Os
import android.telephony.SubscriptionManager
import android.util.Log
import dev.naijun.dununlocker.data.ApnManager
import dev.naijun.dununlocker.data.ApnOperations
import dev.naijun.dununlocker.data.CarrierPrivilegeManager
import dev.naijun.dununlocker.domain.model.ApnContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.lsposed.hiddenapibypass.HiddenApiBypass
import org.lsposed.lsparanoid.Obfuscate
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

const val UNLOCKER_TAG = "BrokerInstrumentation"

private const val PERMISSION_READ_PRIVILEGED_PHONE_STATE = "android.permission.READ_PRIVILEGED_PHONE_STATE"
private const val PERMISSION_MODIFY_PHONE_STATE = "android.permission.MODIFY_PHONE_STATE"
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

        val useAdbShell = arguments.getString("use_adb_shell") == "true" ||
            arguments.getBoolean("use_adb_shell", false)
        val hasShellWriteApn = CarrierPrivilegeManager.hasShellWriteApnPermission(context)

        val result = try {
            val bundle = if (useAdbShell || !hasShellWriteApn) {
                executeWithAdbShell(arguments, hasShellWriteApn)
            } else {
                executeWithShizukuDelegation(arguments)
            }
            Result.success(bundle)
        } catch (e: Exception) {
            Log.e(UNLOCKER_TAG, "Error in BrokerInstrumentation", e)
            Result.failure(e)
        }

        val resultCode = if (result.isSuccess) 0 else -100
        val response = result.getOrElse { error ->
            Bundle().apply { putString("error", error.message ?: "Unknown error") }
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
                    PERMISSION_READ_PRIVILEGED_PHONE_STATE
                )
            )
            stopDelegation = stopMethod
            runApnOperation(arguments)
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

    private fun executeWithAdbShell(arguments: Bundle, hasShellWriteApn: Boolean): Bundle {
        val automation = uiAutomation
        if (hasShellWriteApn) {
            automation.adoptShellPermissionIdentity(
                Manifest.permission.WRITE_APN_SETTINGS,
                PERMISSION_READ_PRIVILEGED_PHONE_STATE
            )
            return try {
                runApnOperation(arguments)
            } finally {
                automation.dropShellPermissionIdentity()
            }
        }

        val subId = parseSubId(arguments).let {
            if (it >= 0) it else SubscriptionManager.getDefaultDataSubscriptionId()
        }
        return runBlocking(Dispatchers.IO) {
            CarrierPrivilegeManager.withCarrierPrivileges(
                context = context,
                subId = subId,
                useShizuku = false,
                callingPackage = context.packageName,
                withShellIdentity = { action ->
                    automation.adoptShellPermissionIdentity(
                        PERMISSION_MODIFY_PHONE_STATE,
                        PERMISSION_READ_PRIVILEGED_PHONE_STATE
                    )
                    try {
                        action()
                    } finally {
                        automation.dropShellPermissionIdentity()
                    }
                }
            ) {
                runApnOperation(arguments, fallbackSubId = subId)
            }
        }
    }

    private fun runApnOperation(arguments: Bundle, fallbackSubId: Int = -1): Bundle {
        val operation = arguments.getString(OPERATION_KEY) ?: OPERATION_CREATE
        val subId = parseSubId(arguments).let { if (it >= 0) it else fallbackSubId }

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
                    putString(
                        "apn_summary",
                        apns.joinToString(" | ") {
                            "#${it.id}:${it.name}(${it.apn})[${it.type}]${if (it.isPreferred) "*" else ""}"
                        }
                    )
                }
            }
            OPERATION_COPY -> {
                val sourceId = parseLongArg(arguments, "source_apn_id", -1L)
                val copyName = arguments.getString("copy_name")
                val copyId = ApnOperations.copyApnWithDun(context, subId, sourceId, copyName)
                Bundle().apply {
                    if (copyId != null) {
                        putLong("copied_apn_id", copyId)
                    }
                }
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

                val savedUri = ApnOperations.createApn(context, content, subId)
                Bundle().apply {
                    putString("inserted_uri", savedUri.toString())
                }
            }
        }
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

    private fun parseSubId(arguments: Bundle): Int {
        val rawString = arguments.getString("sub_id")
        if (rawString != null) {
            return rawString.toIntOrNull() ?: -1
        }
        return arguments.getInt("sub_id", -1)
    }

    private fun parseLongArg(arguments: Bundle, key: String, defaultValue: Long): Long {
        val rawString = arguments.getString(key)
        if (rawString != null) {
            return rawString.toLongOrNull() ?: defaultValue
        }
        return arguments.getLong(key, defaultValue)
    }
}
