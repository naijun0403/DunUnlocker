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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.lsposed.lsparanoid.Obfuscate
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import kotlin.collections.emptyList
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.milliseconds

@Obfuscate
class ApnManager(
    private val context: Context
) {
    companion object {
        private const val TAG = "ApnManager"
        private val brokerMutex = Mutex()
    }

    private val sub: ISub
        get() = ISub.Stub.asInterface(
            TelephonyFrameworkInitializer
                .getTelephonyServiceManager()
                .subscriptionServiceRegisterer
                .get()?.let {
                    ShizukuBinderWrapper(
                        it,
                    )
                }
        )

    fun getActiveSubscriptions(): List<SimInfo> {
        return try {
            val activeSubscriptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
                sub.getActiveSubscriptionInfoList(null, null, true)
            } else {
                sub::class.java.getMethod(
                    "getActiveSubscriptionInfoList",
                    String::class.java,
                    String::class.java,
                ).invoke(sub, null, null)
                    ?.let { it as? List<*> }
                    ?.filterIsInstance<SubscriptionInfo>()
            } ?: return emptyList()

            activeSubscriptions.map { info ->
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
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get subscriptions", e)
            emptyList()
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
            withTimeoutOrNull(10_000.milliseconds) {
                val am = IActivityManager.Stub.asInterface(
                    ShizukuBinderWrapper(
                        SystemServiceHelper.getSystemService(Context.ACTIVITY_SERVICE)
                    )
                )

                suspendCancellableCoroutine<Bundle> { continuation ->
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
                            if (!continuation.isActive) return

                            if (resultCode == 0) {
                                continuation.resume(results ?: Bundle())
                            } else {
                                val message = results?.getString("error")
                                    ?: "APN broker failed with code $resultCode"
                                continuation.resumeWithException(IllegalStateException(message))
                            }
                        }
                    }

                    try {
                        val started = am.startInstrumentation(
                            ComponentName(context, BrokerInstrumentation::class.java),
                            null,
                            ActivityManager.INSTR_FLAG_NO_RESTART,
                            bundle,
                            watcher,
                            UiAutomationConnection(),
                            0,
                            null
                        )

                        if (!started && continuation.isActive) {
                            continuation.resumeWithException(
                                IllegalStateException("Failed to start APN broker")
                            )
                        }
                    } catch (e: Exception) {
                        if (continuation.isActive) {
                            continuation.resumeWithException(e)
                        }
                    }
                }
            } ?: error(context.getString(R.string.apn_broker_timeout))
        }
    }
}
