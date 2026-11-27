package dev.naijun.dununlocker.data

import android.app.ActivityManager
import android.app.IActivityManager
import android.app.IInstrumentationWatcher
import android.app.UiAutomationConnection
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyFrameworkInitializer
import android.util.Log
import com.android.internal.telephony.ISub
import dev.naijun.dununlocker.BrokerInstrumentation
import dev.naijun.dununlocker.R
import dev.naijun.dununlocker.domain.model.ApnContent
import dev.naijun.dununlocker.domain.model.CarrierType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.lsposed.lsparanoid.Obfuscate
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.seconds

@Obfuscate
class ApnManager(
    private val context: Context
) {
    private class ApnPermissionDeniedException(message: String) : IllegalStateException(message)

    private data class CachedApns(
        val version: Int,
        val apns: List<ApnSummary>
    )

    companion object {
        private const val TAG = "ApnManager"
        private val brokerMutex = Mutex()
        internal const val BROKER_REQUEST_ID = "broker_request_id"
        internal const val BROKER_LOCAL_RESULT = "broker_local_result"
        internal const val BROKER_APN_PERMISSION_DENIED = "broker_apn_permission_denied"
        private val pendingBrokerResult = AtomicReference<PendingBrokerRequest?>()
        private val _apnChanges = MutableStateFlow(0)
        val apnChanges = _apnChanges.asStateFlow()
        private val carrierPrivilegeApnCache = mutableMapOf<Int, CachedApns>()

        internal fun hasLocalBrokerRequest(requestId: String?): Boolean =
            requestId != null && pendingBrokerResult.get()?.id == requestId

        internal fun onBrokerFinished(requestId: String?, resultCode: Int, results: Bundle?) {
            val pending = pendingBrokerResult.get() ?: return
            if (pending.id != requestId || !pendingBrokerResult.compareAndSet(pending, null)) return
            if (pending.changesApns) _apnChanges.update { it + 1 }

            if (resultCode == 0) {
                pending.completion.complete(results ?: Bundle())
            } else {
                val message = results?.getString("error") ?: "APN broker failed with code $resultCode"
                val exception = if (results?.getBoolean(BROKER_APN_PERMISSION_DENIED) == true) {
                    ApnPermissionDeniedException(message)
                } else {
                    IllegalStateException(message)
                }
                pending.completion.completeExceptionally(exception)
            }
        }
    }

    private class PendingBrokerRequest(val changesApns: Boolean) {
        val id: String = UUID.randomUUID().toString()
        val completion = CompletableDeferred<Bundle>()
    }

    // The shell's granted permissions only change with a system update, so check once instead of
    // making a PackageManager IPC on every recomposition and APN operation.
    val usesCarrierPrivilegeMode: Boolean by lazy {
        !CarrierPrivilegeManager.hasShellWriteApnPermission(context)
    }

    private val sub: ISub
        get() {
            val subscriptionService = TelephonyFrameworkInitializer.getTelephonyServiceManager()
                ?.subscriptionServiceRegisterer
                ?.get()
                ?: error("Subscription service is unavailable")
            return ISub.Stub.asInterface(ShizukuBinderWrapper(subscriptionService))
        }

    private fun fetchActiveSubscriptions(): List<SubscriptionInfo> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            sub.getActiveSubscriptionInfoList(null, null, true)
        } else {
            ISub::class.java.getMethod(
                "getActiveSubscriptionInfoList",
                String::class.java,
                String::class.java,
            ).invoke(sub, null, null)
                ?.let { it as? List<*> }
                ?.filterIsInstance<SubscriptionInfo>()
        }.orEmpty()

    suspend fun getActiveSubscriptions(): Result<List<SimInfo>> = withContext(Dispatchers.IO) {
        try {
            val activeSubscriptions = fetchActiveSubscriptions()

            if (usesCarrierPrivilegeMode && activeSubscriptions.isNotEmpty() && brokerMutex.tryLock()) {
                try {
                    activeSubscriptions.forEach { info ->
                        runCatching {
                            CarrierPrivilegeManager.cleanupStaleOverrideIfNeeded(context, info.subscriptionId)
                        }.onFailure { error ->
                            Log.w(TAG, "Failed to clean up stale carrier privilege override for subId=${info.subscriptionId}", error)
                        }
                    }
                } finally {
                    brokerMutex.unlock()
                }
            }

            Result.success(activeSubscriptions.map { info ->
                SimInfo(
                    subscriptionId = info.subscriptionId,
                    slotIndex = info.simSlotIndex,
                    carrierName = info.carrierName?.toString()
                        ?: context.getString(R.string.sim_unknown_carrier),
                    displayName = info.displayName?.toString()
                        ?: context.getString(R.string.sim_default_name, info.simSlotIndex + 1),
                    mcc = info.mccString ?: "",
                    mnc = info.mncString ?: ""
                )
            })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get subscriptions", e)
            Result.failure(e)
        }
    }

    suspend fun applyApnConfig(
        carrierType: CarrierType,
        customApnContent: ApnContent? = null,
        subscriptionId: Int? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val apnContent = customApnContent ?: ApnContent.getDefaultConfig(carrierType)

            val bundle = Bundle().apply {
                putString("name", apnContent.name)
                putString("numeric", apnContent.numeric)
                putString("mcc", apnContent.mcc)
                putString("mnc", apnContent.mnc)
                putString("apn", apnContent.apn)
                putString("type", apnContent.type)
                putString("protocol", apnContent.protocol)
                putString("mmsc", apnContent.mmsc)
                putString("mms_proxy", apnContent.mmsProxy)
                putString("mms_port", apnContent.mmsPort)
                putString("roaming_protocol", apnContent.roamingProtocol)
                putString("server", apnContent.server)
                putString("auth_type", apnContent.authType)
                putString("user", apnContent.user)
                putString("password", apnContent.password)

                subscriptionId?.let { putInt("sub_id", it) }
            }

            brokerMutex.withLock {
                executeApnOperationLocked(
                    subscriptionId = subscriptionId,
                    changesApns = true,
                    brokerAction = { overrideConfigUsingBrokerLocked(bundle) },
                    directAction = { resolvedSubId ->
                        ApnOperations.createApn(context, apnContent, subscriptionId ?: resolvedSubId)
                    }
                )
            }

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply APN config", e)
            Result.failure(e)
        }
    }

    suspend fun getApns(
        subscriptionId: Int,
        forceRefresh: Boolean = false
    ): Result<List<ApnSummary>> = withContext(Dispatchers.IO) {
        try {
            val apns = brokerMutex.withLock {
                if (!forceRefresh) {
                    getCachedCarrierPrivilegeApnsLocked(subscriptionId)?.let { cached ->
                        return@withLock cached
                    }
                }
                executeApnOperationLocked(
                    subscriptionId = subscriptionId,
                    changesApns = false,
                    brokerAction = {
                        val result = overrideConfigUsingBrokerLocked(Bundle().apply {
                            putString("operation", "list_apns")
                            putInt("sub_id", subscriptionId)
                        })
                        val rows = result.getParcelableArrayList("apns", Bundle::class.java).orEmpty()
                        rows.map { row ->
                            ApnSummary(
                                id = row.getLong("id"),
                                name = row.getString("name").orEmpty(),
                                apn = row.getString("apn").orEmpty(),
                                type = row.getString("type").orEmpty(),
                                isPreferred = row.getBoolean("preferred")
                            )
                        }
                    },
                    directAction = { resolvedSubId ->
                        ApnOperations.listApns(context, resolvedSubId).also { rows ->
                            carrierPrivilegeApnCache[resolvedSubId] = CachedApns(_apnChanges.value, rows)
                        }
                    }
                )
            }
            Result.success(apns)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun addDunToApn(subscriptionId: Int, sourceApnId: Long): Result<Unit> =
        copyApnWithDun(subscriptionId, sourceApnId, copyName = null)

    suspend fun createNamedApnCopy(subscriptionId: Int, sourceApnId: Long, name: String): Result<Unit> =
        copyApnWithDun(subscriptionId, sourceApnId, copyName = name)

    private suspend fun copyApnWithDun(
        subscriptionId: Int,
        sourceApnId: Long,
        copyName: String?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            brokerMutex.withLock {
                executeApnOperationLocked(
                    subscriptionId = subscriptionId,
                    changesApns = true,
                    brokerAction = {
                        overrideConfigUsingBrokerLocked(Bundle().apply {
                            putString("operation", "copy_apn")
                            putInt("sub_id", subscriptionId)
                            putLong("source_apn_id", sourceApnId)
                            copyName?.let { putString("copy_name", it) }
                        })
                    },
                    directAction = { resolvedSubId ->
                        ApnOperations.copyApnWithDun(context, resolvedSubId, sourceApnId, copyName)
                    }
                )
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy APN", e)
            Result.failure(e)
        }
    }

    private fun getCachedCarrierPrivilegeApnsLocked(subscriptionId: Int): List<ApnSummary>? {
        val cached = carrierPrivilegeApnCache[subscriptionId] ?: return null
        return if (cached.version == _apnChanges.value) cached.apns else null
    }

    private suspend fun <T> executeApnOperationLocked(
        subscriptionId: Int?,
        changesApns: Boolean,
        brokerAction: suspend () -> T,
        directAction: (Int) -> T
    ): T = withContext(NonCancellable) {
        if (usesCarrierPrivilegeMode) {
            return@withContext runDirectWithCarrierPrivilegesLocked(subscriptionId, changesApns, directAction)
        }

        try {
            brokerAction()
        } catch (e: ApnPermissionDeniedException) {
            Log.w(TAG, "Shell APN delegation denied by TelephonyProvider; falling back to carrier privilege method", e)
            runDirectWithCarrierPrivilegesLocked(subscriptionId, changesApns, directAction)
        }
    }

    private suspend fun <T> runDirectWithCarrierPrivilegesLocked(
        subscriptionId: Int?,
        changesApns: Boolean,
        directAction: (Int) -> T
    ): T {
        val resolvedSubId = resolveSubscriptionId(subscriptionId)
        var mutationStarted = false
        var refreshedApns: List<ApnSummary>? = null
        try {
            return CarrierPrivilegeManager.withCarrierPrivileges(context, resolvedSubId) {
                mutationStarted = changesApns
                try {
                    directAction(resolvedSubId)
                } finally {
                    if (changesApns) {
                        // Prefetch within the same carrier privilege session so the refresh
                        // triggered by apnChanges does not need a second override cycle. This
                        // also runs after a failed write, which may have changed the provider.
                        refreshedApns = runCatching {
                            ApnOperations.listApns(context, resolvedSubId)
                        }.onFailure { error ->
                            Log.w(TAG, "Failed to prefetch APN list after mutation", error)
                        }.getOrNull()
                    }
                }
            }
        } finally {
            // Like the broker path, publish a change whenever a write was attempted, not only
            // when it succeeded, so the UI and cache never keep a pre-write APN list.
            if (mutationStarted) {
                publishCarrierPrivilegeApnChangeLocked(resolvedSubId, refreshedApns)
            }
        }
    }

    private fun publishCarrierPrivilegeApnChangeLocked(subId: Int, refreshedApns: List<ApnSummary>?) {
        // Callers hold brokerMutex, so getApns() cannot read the cache before it is updated.
        val version = _apnChanges.updateAndGet { it + 1 }
        carrierPrivilegeApnCache.clear()
        refreshedApns?.let { apns ->
            carrierPrivilegeApnCache[subId] = CachedApns(version, apns)
        }
    }

    private fun resolveSubscriptionId(subscriptionId: Int?): Int {
        if (subscriptionId != null && subscriptionId >= 0) return subscriptionId
        val defaultDataSubId = SubscriptionManager.getDefaultDataSubscriptionId()
        if (SubscriptionManager.isValidSubscriptionId(defaultDataSubId)) {
            return defaultDataSubId
        }
        return runCatching { fetchActiveSubscriptions().firstOrNull()?.subscriptionId }.getOrNull()
            ?: error(context.getString(R.string.carrier_privilege_no_sub))
    }

    private suspend fun overrideConfigUsingBrokerLocked(bundle: Bundle): Bundle {
        val am = IActivityManager.Stub.asInterface(
            ShizukuBinderWrapper(
                SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)
            )
        )
        val pending = PendingBrokerRequest(bundle.getString("operation") != "list_apns")
        bundle.putString(BROKER_REQUEST_ID, pending.id)
        check(pendingBrokerResult.compareAndSet(null, pending)) {
            context.getString(R.string.apn_broker_pending)
        }
        val watcher = object : IInstrumentationWatcher.Stub() {
            override fun instrumentationStatus(
                name: ComponentName?,
                resultCode: Int,
                results: Bundle?
            ) = Unit

            override fun instrumentationFinished(
                name: ComponentName?,
                resultCode: Int,
                results: Bundle?
            ) {
                if (results?.getBoolean(BROKER_LOCAL_RESULT) != true) {
                    onBrokerFinished(pending.id, resultCode, results)
                }
            }
        }

        try {
            check(am.startInstrumentation(
                ComponentName(context, BrokerInstrumentation::class.java),
                null,
                ActivityManager.INSTR_FLAG_NO_RESTART,
                bundle,
                watcher,
                UiAutomationConnection(),
                0,
                null
            )) { "Failed to start APN broker" }
        } catch (e: Exception) {
            pendingBrokerResult.compareAndSet(pending, null)
            throw e
        }

        // Timing out the waiter must not release the running instrumentation's slot.
        return withTimeoutOrNull(10.seconds) { pending.completion.await() }
            ?: error(context.getString(R.string.apn_broker_timeout))
    }
}
