package com.blkdem.autoban.sms

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.blkdem.autoban.AutobanApp
import com.blkdem.autoban.live.Contacts
import com.blkdem.autoban.rules.PhoneNumbers
import com.blkdem.autoban.telegram.TelegramClient
import com.blkdem.autoban.telegram.TelegramFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Пересылка входящих SMS в Telegram. Приложение не становится SMS-приложением по умолчанию —
 * только получает копию входящих (разрешение «SMS»). Текст в лог не пишется.
 */
class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val s = AutobanApp.from(context).telegramSettings.snapshot()
        if (!s.enabled || !s.configured || !s.forwardSms) return
        val parts = runCatching { Telephony.Sms.Intents.getMessagesFromIntent(intent) }.getOrNull()?.filterNotNull().orEmpty()
        if (parts.isEmpty()) return
        val subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, intent.getIntExtra("subscription", -1))
        val (slot, label) = sim(context, subId)
        // Длинная SMS приходит частями; в одном событии могут быть сообщения от разных отправителей.
        for ((from, msgs) in parts.groupBy { it.displayOriginatingAddress ?: it.originatingAddress }) {
            val text = msgs.joinToString("") { it.displayMessageBody ?: it.messageBody ?: "" }
            val ts = msgs.minOf { it.timestampMillis }.takeIf { it > 0 } ?: System.currentTimeMillis()
            Log.i(TAG, "sms from ${from?.let(PhoneNumbers::normalize)?.takeLast(4) ?: "?"}: ${text.length} chars -> telegram")
            SmsWorker.enqueue(context, from, text, ts, slot, label)
        }
    }

    @SuppressLint("MissingPermission")
    private fun sim(context: Context, subId: Int): Pair<Int?, String?> {
        if (subId < 0) return null to null
        val info = runCatching { context.getSystemService(SubscriptionManager::class.java)?.getActiveSubscriptionInfo(subId) }.getOrNull()
            ?: return null to null
        return (info.simSlotIndex + 1) to info.displayName?.toString()
    }

    companion object { private const val TAG = "Autoban" }
}

class SmsWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = AutobanApp.from(applicationContext)
        val s = app.telegramSettings.snapshot()
        if (!s.enabled || !s.configured) return@withContext Result.success()
        val d = inputData
        val from = d.getString(K_FROM)
        val number = PhoneNumbers.normalize(from)?.takeIf { it.startsWith("+") }
        val text = TelegramFormat.sms(
            from = from,
            contact = Contacts.name(applicationContext, number ?: from),
            simSlot = d.getInt(K_SLOT, 0).takeIf { it > 0 },
            simLabel = d.getString(K_LABEL),
            text = d.getString(K_TEXT).orEmpty(),
            ts = d.getLong(K_TS, System.currentTimeMillis()),
        )
        val buttons = if (s.commands && number != null) listOf(
            TelegramClient.Button("🔎 Кто это", "who:$number"),
            TelegramClient.Button("🚫 Номер в бан", "ban:$number"),
        ) else emptyList()
        try {
            s.client().sendMessage(s.chatId, text, s.silent, buttons)
            if (buttons.isNotEmpty()) com.blkdem.autoban.telegram.BotPoller.pollSoon(applicationContext, delaySeconds = 120)
            Result.success()
        } catch (e: TelegramClient.TelegramException) {
            Log.w(TAG, "sms forward failed (attempt $runAttemptCount): ${e.message}")
            if (e.retryable && runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.success()
        }
    }

    companion object {
        private const val TAG = "Autoban"
        private const val MAX_ATTEMPTS = 10
        /** Data у WorkManager ограничена 10 КБ; Telegram — 4096 символами. */
        private const val MAX_CHARS = 3500
        private const val K_FROM = "from"
        private const val K_TEXT = "text"
        private const val K_TS = "ts"
        private const val K_SLOT = "slot"
        private const val K_LABEL = "label"

        fun enqueue(context: Context, from: String?, text: String, ts: Long, slot: Int?, label: String?) {
            val req = OneTimeWorkRequestBuilder<SmsWorker>()
                .setInputData(workDataOf(
                    K_FROM to from, K_TEXT to text.take(MAX_CHARS), K_TS to ts, K_SLOT to (slot ?: 0), K_LABEL to label,
                ))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }
    }
}
