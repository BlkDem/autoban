package com.blkdem.autoban.telegram

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.blkdem.autoban.AutobanApp
import com.blkdem.autoban.data.CallEvent
import com.blkdem.autoban.data.Repository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Ставит события в очередь на отправку и планирует фоновую доставку. */
class TelegramNotifier(private val context: Context, private val repo: Repository, private val settings: TelegramSettings) {

    fun onEvent(e: CallEvent) {
        val s = settings.snapshot()
        if (!s.wants(e)) return
        repo.setTgStatus(listOf(e.id), CallEvent.TgStatus.PENDING)
        schedule(s)
    }

    fun schedule(s: TelegramSettings.Snapshot = settings.snapshot()) {
        BotPoller.sync(context, s)
        val wm = WorkManager.getInstance(context)
        val req = OneTimeWorkRequestBuilder<SendWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        if (s.mode == TelegramSettings.Mode.DIGEST) {
            // Сводка: первое событие запускает таймер, остальные копятся до его срабатывания.
            wm.enqueueUniqueWork(WORK_DIGEST, ExistingWorkPolicy.KEEP,
                req.setInitialDelay(s.digestMinutes.toLong(), TimeUnit.MINUTES).build())
        } else {
            wm.enqueueUniqueWork(WORK_INSTANT, ExistingWorkPolicy.APPEND_OR_REPLACE, req.build())
        }
    }

    /** Отправляет все PENDING-события: по одному сообщению или одной сводкой. */
    class SendWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
            val app = AutobanApp.from(applicationContext)
            val s = app.telegramSettings.snapshot()
            val repo = app.repository
            val pending = repo.eventsWithTgStatus(CallEvent.TgStatus.PENDING, limit = 200)
            if (pending.isEmpty()) return@withContext Result.success()
            if (!s.configured) {
                repo.setTgStatus(pending.map { it.id }, CallEvent.TgStatus.FAILED)
                return@withContext Result.success()
            }
            val client = s.client()
            try {
                if (s.mode == TelegramSettings.Mode.DIGEST && pending.size > 1) {
                    client.sendMessage(s.chatId, TelegramFormat.digest(pending), s.silent)
                    repo.setTgStatus(pending.map { it.id }, CallEvent.TgStatus.SENT)
                } else {
                    for (e in pending) {
                        val buttons = if (s.commands) BotPoller.buttonsFor(e) else emptyList()
                        client.sendMessage(s.chatId, TelegramFormat.event(e), s.silent, buttons)
                        repo.setTgStatus(listOf(e.id), CallEvent.TgStatus.SENT)
                    }
                }
                // Пользователь может нажать кнопку вскоре после сообщения — опросим бота через пару минут.
                if (s.commands) BotPoller.pollSoon(applicationContext, delaySeconds = 120)
                Result.success()
            } catch (e: TelegramClient.TelegramException) {
                Log.w(TAG, "telegram send failed (attempt $runAttemptCount): ${e.message}")
                if (e.retryable && runAttemptCount < MAX_ATTEMPTS) {
                    Result.retry()
                } else {
                    repo.setTgStatus(repo.eventsWithTgStatus(CallEvent.TgStatus.PENDING, 200).map { it.id }, CallEvent.TgStatus.FAILED)
                    Result.success()
                }
            }
        }
    }

    companion object {
        private const val TAG = "Autoban"
        private const val WORK_INSTANT = "tg-instant"
        private const val WORK_DIGEST = "tg-digest"
        private const val MAX_ATTEMPTS = 10
    }
}
