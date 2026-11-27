package dev.naijun.dununlocker.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PersistableBundle
import android.telephony.CarrierConfigManager
import android.telephony.TelephonyManager
import android.util.Log
import com.android.internal.telephony.ICarrierConfigLoader
import dev.naijun.dununlocker.R
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.lsposed.lsparanoid.Obfuscate
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.security.MessageDigest

@Obfuscate
internal object CarrierPrivilegeManager {
    private const val TAG = "CarrierPrivilegeManager"
    private const val SHELL_PACKAGE = "com.android.shell"
    private const val PRIVILEGE_WAIT_TIMEOUT_MS = 5_000L
    private const val PRIVILEGE_POLL_INTERVAL_MS = 50L

    fun hasShellWriteApnPermission(context: Context): Boolean {
        return context.packageManager.checkPermission(
            Manifest.permission.WRITE_APN_SETTINGS,
            SHELL_PACKAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun <T> withCarrierPrivileges(
        context: Context,
        subId: Int,
        useShizuku: Boolean = true,
        callingPackage: String = if (useShizuku) SHELL_PACKAGE else context.packageName,
        withShellIdentity: ((() -> Unit) -> Unit)? = null,
        block: () -> T
    ): T {
        require(subId >= 0) { context.getString(R.string.carrier_privilege_no_sub) }

        val telephonyManager = context.getSystemService(TelephonyManager::class.java)
            .createForSubscriptionId(subId)

        if (telephonyManager.hasCarrierPrivileges()) {
            return block()
        }

        val rawBinder = SystemServiceHelper.getSystemService(Context.CARRIER_CONFIG_SERVICE)
            ?: error("CarrierConfig service unavailable")
        val binder = if (useShizuku) ShizukuBinderWrapper(rawBinder) else rawBinder
        val loader = ICarrierConfigLoader.Stub.asInterface(binder)

        val runPrivileged: (() -> Unit) -> Unit = withShellIdentity ?: { action -> action() }

        var existingCerts: List<String> = emptyList()
        runPrivileged {
            existingCerts = readExistingCertificates(loader, subId, callingPackage)
        }

        val selfCertRules = getSelfCertificateRules(context)
        check(selfCertRules.isNotEmpty()) { "Could not read app signing certificate" }

        val mergedCerts = (existingCerts + selfCertRules).distinct().toTypedArray()
        val overrideBundle = PersistableBundle().apply {
            putBoolean(CarrierConfigManager.KEY_CARRIER_CONFIG_APPLIED_BOOL, true)
            putStringArray(CarrierConfigManager.KEY_CARRIER_CERTIFICATE_STRING_ARRAY, mergedCerts)
        }

        runPrivileged {
            loader.overrideConfig(subId, overrideBundle, false)
        }
        return try {
            val granted = waitForCarrierPrivileges(telephonyManager)
            if (!granted) {
                error(context.getString(R.string.carrier_privilege_timeout))
            }
            block()
        } finally {
            withContext(NonCancellable) {
                try {
                    runPrivileged {
                        loader.overrideConfig(subId, null, false)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to clear temporary carrier config override", e)
                }
            }
        }
    }

    private fun readExistingCertificates(
        loader: ICarrierConfigLoader,
        subId: Int,
        callingPackage: String
    ): List<String> {
        val bundle = try {
            loader.getConfigForSubIdWithFeature(subId, callingPackage, null)
        } catch (e: Throwable) {
            try {
                loader.getConfigForSubId(subId, callingPackage)
            } catch (fallbackError: Throwable) {
                Log.w(TAG, "Could not read existing carrier certificates", fallbackError)
                null
            }
        }
        return bundle
            ?.getStringArray(CarrierConfigManager.KEY_CARRIER_CERTIFICATE_STRING_ARRAY)
            ?.toList()
            .orEmpty()
    }

    @Suppress("DEPRECATION")
    private fun getSelfCertificateRules(context: Context): List<String> {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_SIGNING_CERTIFICATES
        )
        val signatures = packageInfo.signingInfo?.let { info ->
            if (info.hasMultipleSigners()) info.apkContentsSigners else info.signingCertificateHistory
        }.orEmpty()
        val digest = MessageDigest.getInstance("SHA-256")
        return signatures.map { sig ->
            val sha256Hex = digest.digest(sig.toByteArray())
                .joinToString("") { "%02x".format(it) }
            "$sha256Hex:${context.packageName}"
        }
    }

    private suspend fun waitForCarrierPrivileges(telephonyManager: TelephonyManager): Boolean {
        val deadline = System.currentTimeMillis() + PRIVILEGE_WAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            if (telephonyManager.hasCarrierPrivileges()) {
                return true
            }
            delay(PRIVILEGE_POLL_INTERVAL_MS)
        }
        return telephonyManager.hasCarrierPrivileges()
    }
}
