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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
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
    companion object {
        private const val TAG = "ApnManager"
        private val brokerMutex = Mutex()
        internal const val BROKER_REQUEST_ID = "broker_request_id"
        internal const val BROKER_LOCAL_RESULT = "broker_local_result"
        private val pendingBrokerResult = AtomicReference<PendingBrokerRequest?>()
        private val _apnChanges = MutableStateFlow(0)
        val apnChanges = _apnChanges.asStateFlow()

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
                pending.completion.completeExceptionally(IllegalStateException(message))
            }
        }
    }

    private class PendingBrokerRequest(val changesApns: Boolean) {
        val id: String = UUID.randomUUID().toString()
        val completion = CompletableDeferred<Bundle>()
    }

    private val sub: ISub
        get() {
            val subscriptionService = TelephonyFrameworkInitializer.getTelephonyServiceManager()
                ?.subscriptionServiceRegisterer
                ?.get()
                ?: error("Subscription service is unavailable")
            return ISub.Stub.asInterface(ShizukuBinderWrapper(subscriptionService))
        }

    suspend fun getActiveSubscriptions(): Result<List<SimInfo>> = withContext(Dispatchers.IO) {
        try {
            val activeSubscriptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
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

            overrideConfigUsingBroker(bundle)

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to apply APN config", e)
            Result.failure(e)
        }
    }

    suspend fun getApns(subscriptionId: Int): Result<List<ApnSummary>> = withContext(Dispatchers.IO) {
        try {
            val result = overrideConfigUsingBroker(Bundle().apply {
                putString("operation", "list_apns")
                putInt("sub_id", subscriptionId)
            })
            val rows = result.getParcelableArrayList("apns", Bundle::class.java).orEmpty()
            Result.success(rows.map { row ->
                ApnSummary(
                    id = row.getLong("id"),
                    name = row.getString("name").orEmpty(),
                    apn = row.getString("apn").orEmpty(),
                    type = row.getString("type").orEmpty(),
                    isPreferred = row.getBoolean("preferred")
                )
            })
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
            overrideConfigUsingBroker(Bundle().apply {
                putString("operation", "copy_apn")
                putInt("sub_id", subscriptionId)
                putLong("source_apn_id", sourceApnId)
                copyName?.let { putString("copy_name", it) }
            })
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy APN", e)
            Result.failure(e)
        }
    }

    private suspend fun overrideConfigUsingBroker(bundle: Bundle): Bundle = brokerMutex.withLock {
        withContext(NonCancellable) {
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
            withTimeoutOrNull(10.seconds) { pending.completion.await() }
                ?: error(context.getString(R.string.apn_broker_timeout))
        }
    }
}
