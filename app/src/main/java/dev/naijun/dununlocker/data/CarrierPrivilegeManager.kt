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
        block: () -> T
    ): T {
        require(subId >= 0) { context.getString(R.string.carrier_privilege_no_sub) }

        val telephonyManager = context.getSystemService(TelephonyManager::class.java)
            .createForSubscriptionId(subId)
        val selfCertRules = getSelfCertificateRules(context)
        check(selfCertRules.isNotEmpty()) { "Could not read app signing certificate" }

        val loader = getCarrierConfigLoader()
        val existingCerts = readExistingCertificates(loader, subId)
        val hasStaleSelfRule = containsSelfCertificateRule(existingCerts, selfCertRules)
        val baseCerts = removeSelfCertificateRules(existingCerts, selfCertRules)

        // Only bypass override management when the app already holds genuine carrier privileges
        // rather than a stale carrier config rule left behind by an earlier crash.
        if (telephonyManager.hasCarrierPrivileges() && !hasStaleSelfRule) {
            return block()
        }

        if (!telephonyManager.hasCarrierPrivileges()) {
            val mergedCerts = mergeCertificateRules(baseCerts, selfCertRules)
            overrideCarrierCertificates(loader, subId, mergedCerts)
        }

        return try {
            if (!waitForCarrierPrivileges(telephonyManager)) {
                error(context.getString(R.string.carrier_privilege_timeout))
            }
            block()
        } finally {
            withContext(NonCancellable) {
                try {
                    overrideCarrierCertificates(loader, subId, baseCerts.toTypedArray())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to restore temporary carrier certificate override", e)
                }
            }
        }
    }

    /**
     * Cleans up a leftover self-certificate rule if a previous process crashed before finally ran.
     * Costs zero Shizuku IPCs in the normal case where carrier privileges are not active.
     */
    fun cleanupStaleOverrideIfNeeded(context: Context, subId: Int) {
        if (subId < 0) return
        val telephonyManager = context.getSystemService(TelephonyManager::class.java)
            .createForSubscriptionId(subId)
        if (!telephonyManager.hasCarrierPrivileges()) return

        val selfCertRules = getSelfCertificateRules(context)
        if (selfCertRules.isEmpty()) return

        val loader = getCarrierConfigLoader()
        val existingCerts = readExistingCertificates(loader, subId)
        if (!containsSelfCertificateRule(existingCerts, selfCertRules)) return

        val baseCerts = removeSelfCertificateRules(existingCerts, selfCertRules)
        overrideCarrierCertificates(loader, subId, baseCerts.toTypedArray())
    }

    internal fun containsSelfCertificateRule(
        existingCerts: List<String>,
        selfCertRules: List<String>
    ): Boolean = existingCerts.any { existing ->
        selfCertRules.any { rule -> existing.equals(rule, ignoreCase = true) }
    }

    internal fun removeSelfCertificateRules(
        existingCerts: List<String>,
        selfCertRules: List<String>
    ): List<String> = existingCerts.filterNot { existing ->
        selfCertRules.any { rule -> existing.equals(rule, ignoreCase = true) }
    }

    internal fun mergeCertificateRules(
        baseCerts: List<String>,
        selfCertRules: List<String>
    ): Array<String> = (baseCerts + selfCertRules)
        .distinctBy { it.lowercase() }
        .toTypedArray()

    private fun getCarrierConfigLoader(): ICarrierConfigLoader {
        val rawBinder = SystemServiceHelper.getSystemService(Context.CARRIER_CONFIG_SERVICE)
            ?: error("CarrierConfig service unavailable")
        return ICarrierConfigLoader.Stub.asInterface(ShizukuBinderWrapper(rawBinder))
    }

    /**
     * Updates only KEY_CARRIER_CERTIFICATE_STRING_ARRAY in the non-persistent override bundle.
     * Unlike passing null (which clears mOverrideConfigs[phoneId] entirely), passing a single-key
     * PersistableBundle merges via putAll() and preserves unrelated overrides set by third-party tools.
     */
    private fun overrideCarrierCertificates(
        loader: ICarrierConfigLoader,
        subId: Int,
        certificates: Array<String>
    ) {
        val overrideBundle = PersistableBundle().apply {
            putStringArray(CarrierConfigManager.KEY_CARRIER_CERTIFICATE_STRING_ARRAY, certificates)
        }
        loader.overrideConfig(subId, overrideBundle, false)
    }

    private fun readExistingCertificates(
        loader: ICarrierConfigLoader,
        subId: Int
    ): List<String> {
        val bundle = loader.getConfigForSubIdWithFeature(subId, SHELL_PACKAGE, null)
        return bundle
            ?.getStringArray(CarrierConfigManager.KEY_CARRIER_CERTIFICATE_STRING_ARRAY)
            ?.toList()
            .orEmpty()
    }

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
