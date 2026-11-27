package com.blkdem.autoban.live

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import com.blkdem.autoban.AutobanApp
import java.util.concurrent.Executors

/**
 * «Телефон звонит» (PHONE_STATE). Фильтр звонков Android не вызывается для номеров из контактов —
 * определитель узнаёт о таких звонках отсюда. Номер в событии есть только с разрешением «Журнал вызовов»;
 * событие приходит дважды (без номера и с номером) — берём то, что с номером.
 */
class RingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        if (intent.getStringExtra(TelephonyManager.EXTRA_STATE) != TelephonyManager.EXTRA_STATE_RINGING) return
        @Suppress("DEPRECATION")
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)?.takeIf { it.isNotBlank() } ?: return
        val app = AutobanApp.from(context)
        if (!app.telegramSettings.snapshot().callerId) return
        val pending = goAsync()
        executor.execute {
            try {
                // Звонок, прошедший фильтр, к этому моменту уже записан; даём записи чуть времени.
                Thread.sleep(DELAY_MS)
                app.live.onRinging(number)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val DELAY_MS = 700L
        private val executor = Executors.newSingleThreadExecutor()
    }
}
