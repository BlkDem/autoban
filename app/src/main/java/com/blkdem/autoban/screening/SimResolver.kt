package com.blkdem.autoban.screening

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

/** Определяет SIM по PhoneAccountHandle звонка. Несколько стратегий — производители ведут себя по-разному. */
class SimResolver(private val context: Context) {

    data class Sim(val slot: Int, val subscriptionId: Int, val label: String?)

    @SuppressLint("MissingPermission")
    fun resolve(handle: PhoneAccountHandle?): Sim? {
        if (handle == null) return null
        val subs: List<SubscriptionInfo> = try {
            context.getSystemService(SubscriptionManager::class.java)
                ?.activeSubscriptionInfoList.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }

        // 1. API 30+: официальный способ.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val subId = context.getSystemService(TelephonyManager::class.java)
                ?.getSubscriptionId(handle) ?: SubscriptionManager.INVALID_SUBSCRIPTION_ID
            subs.firstOrNull { it.subscriptionId == subId }?.let { return it.toSim() }
        }

        // 2. Android 10 / Samsung: id аккаунта — это subId или ICCID.
        val id = handle.id
        subs.firstOrNull { it.subscriptionId.toString() == id }?.let { return it.toSim() }
        subs.firstOrNull { !it.iccId.isNullOrEmpty() && id.startsWith(it.iccId) }?.let { return it.toSim() }

        // 3. Одна активная SIM — значит она.
        return subs.singleOrNull()?.toSim()
    }

    private fun SubscriptionInfo.toSim() =
        Sim(slot = simSlotIndex + 1, subscriptionId = subscriptionId, label = displayName?.toString())
}
