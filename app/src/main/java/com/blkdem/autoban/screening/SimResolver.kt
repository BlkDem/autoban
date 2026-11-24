package com.blkdem.autoban.screening

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telephony.PhoneStateListener
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Определяет, на какую SIM пришёл звонок.
 *
 * Сервису фильтрации система обычно не передаёт PhoneAccountHandle (Call.Details урезаны),
 * поэтому основной способ — посмотреть, на какой подписке радиомодуль сейчас «звонит» (CALL_STATE_RINGING).
 * Входящий уже существует на уровне телефонии, пока Telecom ждёт ответа фильтра.
 */
class SimResolver(private val context: Context) {

    data class Sim(val slot: Int, val subscriptionId: Int, val label: String?, val method: String)

    @SuppressLint("MissingPermission")
    fun resolve(handle: PhoneAccountHandle?): Sim? {
        val subs: List<SubscriptionInfo> = try {
            context.getSystemService(SubscriptionManager::class.java)?.activeSubscriptionInfoList.orEmpty()
        } catch (_: SecurityException) {
            emptyList()
        }
        if (subs.isEmpty()) return null
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return null

        // 1. Если система всё же передала аккаунт.
        if (handle != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val subId = runCatching { tm.getSubscriptionId(handle) }.getOrDefault(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                subs.firstOrNull { it.subscriptionId == subId }?.let { return it.toSim("handle") }
            }
            val id = handle.id
            subs.firstOrNull { it.subscriptionId.toString() == id }?.let { return it.toSim("handle-id") }
            subs.firstOrNull { !it.iccId.isNullOrEmpty() && id.startsWith(it.iccId) }?.let { return it.toSim("handle-iccid") }
        }

        // 2. Какая подписка сейчас звонит.
        val ringing = try {
            ringingSubscriptions(tm, subs.map { it.subscriptionId })
        } catch (t: Throwable) {
            Log.w(TAG, "ringing state lookup failed", t)
            emptySet()
        }
        Log.d(TAG, "subs=${subs.map { "${it.subscriptionId}@${it.simSlotIndex}" }} ringing=$ringing")
        subs.filter { it.subscriptionId in ringing }.singleOrNull()?.let { return it.toSim("ringing") }

        // 3. Одна SIM — значит она.
        return subs.singleOrNull()?.toSim("single")
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun ringingSubscriptions(tm: TelephonyManager, subIds: List<Int>): Set<Int> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            return subIds.filter {
                tm.createForSubscriptionId(it).callStateForSubscription == TelephonyManager.CALL_STATE_RINGING
            }.toSet()
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptySet()

        // Android 10–11: слушатель получает текущее состояние сразу после подписки.
        val states = ConcurrentHashMap<Int, Int>()
        val latch = CountDownLatch(subIds.size)
        val listeners = subIds.map { subId ->
            val sub = tm.createForSubscriptionId(subId)
            val l = object : PhoneStateListener(listenerExecutor) {
                @Deprecated("Deprecated in Java")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    if (states.putIfAbsent(subId, state) == null) latch.countDown()
                }
            }
            sub.listen(l, PhoneStateListener.LISTEN_CALL_STATE)
            sub to l
        }
        latch.await(1500, TimeUnit.MILLISECONDS)
        listeners.forEach { (sub, l) -> sub.listen(l, PhoneStateListener.LISTEN_NONE) }
        return states.filterValues { it == TelephonyManager.CALL_STATE_RINGING }.keys
    }

    private fun SubscriptionInfo.toSim(method: String) =
        Sim(slot = simSlotIndex + 1, subscriptionId = subscriptionId, label = displayName?.toString(), method = method)

    companion object {
        private const val TAG = "Autoban"
        private val listenerExecutor = Executors.newSingleThreadExecutor()
    }
}
