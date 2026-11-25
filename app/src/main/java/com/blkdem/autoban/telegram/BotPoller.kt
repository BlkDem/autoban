package com.blkdem.autoban.telegram

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.blkdem.autoban.AutobanApp
import com.blkdem.autoban.data.CallEvent
import com.blkdem.autoban.data.Repository
import com.blkdem.autoban.rules.Action
import com.blkdem.autoban.rules.Decision
import com.blkdem.autoban.rules.ListType
import com.blkdem.autoban.rules.PhoneNumbers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Обратная связь от бота: команды и кнопки. Телефон опрашивает getUpdates — ответ приходит с задержкой. */
object BotPoller {
    private const val TAG = "Autoban"
    private const val WORK_PERIODIC = "tg-poll"
    private const val WORK_NOW = "tg-poll-now"

    private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Включить/выключить периодический опрос в соответствии с настройками. */
    fun sync(context: Context, s: TelegramSettings.Snapshot) {
        val wm = WorkManager.getInstance(context)
        if (s.enabled && s.configured && s.commands) {
            wm.enqueueUniquePeriodicWork(
                WORK_PERIODIC, ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PollWorker>(15, TimeUnit.MINUTES).setConstraints(network).build(),
            )
        } else {
            wm.cancelUniqueWork(WORK_PERIODIC)
        }
    }

    /** Опросить через [delaySeconds] секунд (например, вскоре после отправки сообщения с кнопками). */
    fun pollSoon(context: Context, delaySeconds: Long = 0) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NOW, ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<PollWorker>().setConstraints(network).setInitialDelay(delaySeconds, TimeUnit.SECONDS).build(),
        )
    }

    /** Кнопки под сообщением о звонке, который звонит прямо сейчас: сбросить, бан + сброс, белый список. */
    fun liveButtons(e: CallEvent): List<TelegramClient.Button> {
        val n = e.number ?: return listOf(TelegramClient.Button("⛔ Сбросить", "hang:-"))
        return listOf(
            TelegramClient.Button("⛔ Сбросить", "hang:$n"),
            TelegramClient.Button("🚫 Бан", "ban:$n"),
            TelegramClient.Button("✅ Белый", "white:$n"),
        )
    }

    /** Кнопки под сообщением о звонке. */
    fun buttonsFor(e: CallEvent): List<TelegramClient.Button> {
        val n = e.number ?: return emptyList()
        val banned = e.action == Action.BAN || e.source == Decision.Source.BLOCKLIST
        return listOf(
            if (banned) TelegramClient.Button("🔓 Разбанить", "unban:$n") else TelegramClient.Button("🚫 В бан", "ban:$n"),
            TelegramClient.Button("✅ В белый список", "white:$n"),
        )
    }

    /** getUpdates с одним offset из двух мест сразу (опрос по расписанию и во время звонка) даёт 409 — по очереди. */
    private val pollLock = Any()

    /**
     * Забрать и выполнить команды бота. [timeoutSec] > 0 — длинный опрос (во время звонка).
     * Возвращает false, если опрос выключен в настройках.
     */
    fun pollOnce(app: AutobanApp, timeoutSec: Int = 0): Boolean = synchronized(pollLock) {
        val ts = app.telegramSettings
        val s = ts.snapshot()
        if (!s.enabled || !s.configured || !s.commands) return false
        val client = s.client()
        for (u in client.updates(ts.offset, timeoutSec)) {
            ts.offset = u.id + 1
            if (u.chatId?.toString() != s.chatId) continue // чужие чаты игнорируем
            val cmd = when {
                u.callbackData != null -> BotCommand.fromCallback(u.callbackData)
                u.text != null -> BotCommand.parse(u.text)
                else -> null
            } ?: continue
            val reply = when (cmd) {
                is BotCommand.Who -> TelegramFormat.who(cmd.number, app.processor.report(cmd.number))
                // Кнопки под входящим: сначала — звонок, который звонит прямо сейчас.
                is BotCommand.Hang, is BotCommand.Ban -> app.live.onBotCommand(cmd) ?: execute(app.repository, cmd)
                else -> execute(app.repository, cmd)
            }
            if (u.callbackId != null) {
                runCatching { client.answerCallback(u.callbackId, reply.replace(Regex("<[^>]+>"), "").take(190)) }
            }
            client.sendMessage(s.chatId, reply, silent = true)
        }
        true
    }

    class PollWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
        override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
            try {
                pollOnce(AutobanApp.from(applicationContext))
                Result.success()
            } catch (e: TelegramClient.TelegramException) {
                Log.w(TAG, "bot poll failed: ${e.message}")
                if (e.retryable) Result.retry() else Result.success()
            }
        }
    }

    fun execute(repo: Repository, cmd: BotCommand): String = when (cmd) {
        BotCommand.Help -> BotCommand.HELP
        BotCommand.Stats -> {
            val day = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val t = repo.stats(day)
            val w = repo.stats(day - 6 * 86_400_000L)
            "📊 <b>Сегодня</b>: ${t.total} · 🚫 ${t.banned} · ⛔ ${t.rejected} · ✅ ${t.allowed}\n" +
                "<b>7 дней</b>: ${w.total} · 🚫 ${w.banned} · ⛔ ${w.rejected} · SIM1 ${w.sim1} / SIM2 ${w.sim2}\n" +
                "Бан-лист: ${repo.list(ListType.BLOCK).size} · белый список: ${repo.list(ListType.WHITE).size}"
        }
        is BotCommand.Last -> {
            val events = repo.events(Repository.EventFilter(limit = cmd.count))
            if (events.isEmpty()) "Журнал пуст." else TelegramFormat.digest(events.reversed())
        }
        is BotCommand.Ban -> if (repo.addToList(ListType.BLOCK, cmd.pattern, note = "из Telegram", ruleName = "Telegram"))
            "🚫 <code>${TelegramFormat.escape(show(cmd.pattern))}</code> — в бан-листе" else "Уже в бан-листе: <code>${TelegramFormat.escape(show(cmd.pattern))}</code>"
        is BotCommand.Unban -> {
            val had = repo.isInList(ListType.BLOCK, cmd.pattern)
            repo.removeNumberFromList(ListType.BLOCK, cmd.pattern)
            if (had) "🔓 <code>${TelegramFormat.escape(show(cmd.pattern))}</code> — убран из бан-листа"
            else "Нет в бан-листе: <code>${TelegramFormat.escape(show(cmd.pattern))}</code> (маски удаляются в приложении или /unban с той же маской)"
        }
        is BotCommand.White -> {
            repo.addToList(ListType.WHITE, cmd.pattern, note = "из Telegram", ruleName = "Telegram")
            "✅ <code>${TelegramFormat.escape(show(cmd.pattern))}</code> — в белом списке (проверяется раньше бан-листа)"
        }
        is BotCommand.Who -> TelegramFormat.who(cmd.number, null)
        is BotCommand.Hang -> "Звонок уже завершён."
        is BotCommand.Unknown -> "Не понял: ${TelegramFormat.escape(cmd.text)}\n\n" + BotCommand.HELP
    }

    private fun show(p: String) = if (p.startsWith("+")) PhoneNumbers.pretty(p) else p
}
