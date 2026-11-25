package com.blkdem.autoban.live

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.blkdem.autoban.AutobanApp

/**
 * Читает уведомление звонилки о входящем: в нём звонилка показывает подпись оператора,
 * которая в фильтр звонков не приходит. Работает, только пока Autoban сопровождает звонок.
 * Нужен «Доступ к уведомлениям» (Настройки → Уведомления → Доступ к уведомлениям).
 */
class DialerNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        Log.i(TAG, "notification listener connected")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName !in DIALERS) return
        val live = AutobanApp.from(this).live
        if (!live.isActive()) return
        val n = sbn.notification
        val ex = n.extras
        val texts = buildList {
            for (key in KEYS) add(ex.getCharSequence(key)?.toString())
            ex.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { add(it?.toString()) }
            add(n.tickerText?.toString())
        }.filterNotNull().filter { it.isNotBlank() }
        Log.i(TAG, "dialer notification ${sbn.packageName} category=${n.category}: $texts")
        live.onNotificationTexts(texts)
    }

    companion object {
        private const val TAG = "Autoban"

        private val DIALERS = setOf(
            "com.samsung.android.incallui", "com.samsung.android.dialer",
            "com.google.android.dialer", "com.android.dialer", "com.android.incallui",
        )

        private val KEYS = listOf(
            Notification.EXTRA_TITLE, Notification.EXTRA_TITLE_BIG, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT, Notification.EXTRA_INFO_TEXT, Notification.EXTRA_SUMMARY_TEXT,
        )
    }
}
