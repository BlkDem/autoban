package com.blkdem.autoban.live

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import android.util.Log
import com.blkdem.autoban.AutobanApp
import com.blkdem.autoban.data.CallEvent
import com.blkdem.autoban.rules.Action
import com.blkdem.autoban.rules.CallInfo
import com.blkdem.autoban.rules.CallLabel
import com.blkdem.autoban.rules.Decision
import com.blkdem.autoban.rules.ListType
import com.blkdem.autoban.rules.PhoneNumbers
import com.blkdem.autoban.telegram.BotCommand
import com.blkdem.autoban.telegram.BotPoller
import com.blkdem.autoban.telegram.TelegramClient
import com.blkdem.autoban.telegram.TelegramFormat
import java.util.concurrent.Executors

/**
 * Сопровождение звонка, который фильтр пропустил: пока телефон звонит —
 *  - ловит подпись оператора (маркировку) из системного лога или уведомления звонилки и сбрасывает звонок, если её ловит правило;
 *  - присылает в Telegram сообщение с подписью и отзывами и кнопками «Сбросить» / «Бан»,
 *    а бота опрашивает непрерывно, чтобы кнопка сработала сразу;
 *  - после звонка берёт подпись из журнала вызовов и обновляет журнал и сообщение.
 *
 * Подпись в фильтр звонков не приходит: оператор присылает её позже (см. PLAN.md, «Диагностика на Note 9»).
 */
class LiveCalls(private val app: AutobanApp) {

    private class Session(val eventId: Long, val info: CallInfo, val started: Long, lookupPending: Boolean) {
        @Volatile var label: String? = null
        @Volatile var reportReady = !lookupPending
        @Volatile var ringing = false
        @Volatile var ended = false
        @Volatile var hungUp: CallEvent.Outcome? = null
        /** Сообщение о звонке уже отправлено или решено не отправлять (фильтр уведомлений). */
        @Volatile var tgDone = false
        val lock = Any()
    }

    @Volatile private var current: Session? = null
    private val pool = Executors.newCachedThreadPool()
    private val repo get() = app.repository

    private val telephony get() = app.getSystemService(TelephonyManager::class.java)
    private val telecom get() = app.getSystemService(TelecomManager::class.java)

    private fun granted(p: String) = app.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    /** Telegram для звонков берём на себя, если он настроен. */
    private fun telegramOn() = app.telegramSettings.snapshot().let { it.enabled && it.configured }

    /** Сопровождается ли событие (тогда обычная отправка в Telegram его пропускает). */
    fun handles(eventId: Long): Boolean = current?.takeIf { !it.ended || !it.tgDone }?.eventId == eventId

    /** Есть ли звонок, который звонит прямо сейчас. */
    fun isActive(): Boolean = current?.let { !it.ended } == true

    /**
     * Фильтр пропустил звонок — следим за ним. Возвращает true, если сообщение в Telegram
     * отправит сопровождение (а не обычная очередь).
     */
    fun start(e: CallEvent, info: CallInfo, lookupPending: Boolean): Boolean {
        val s = Session(e.id, info, System.currentTimeMillis(), lookupPending)
        // Подпись могла прийти уже в фильтр (из системного лога).
        s.label = info.callerName?.takeIf(CallLabel::isOperatorLabel)
        current?.let { it.ended = true }
        current = s
        update(s) { it.copy(outcome = CallEvent.Outcome.RINGING) }
        pool.execute { runCatching { monitor(s) }.onFailure { Log.e(TAG, "live monitor failed", it) } }
        return telegramOn()
    }

    /** Отзывы о номере готовы (или не нашлись). */
    fun onReport(eventId: Long) {
        val s = current?.takeIf { it.eventId == eventId } ?: return
        s.reportReady = true
        if (s.tgDone) pool.execute { refreshTelegram(s) }
    }

    /** Тексты уведомления звонилки (см. [DialerNotificationListener]). */
    fun onNotificationTexts(texts: List<String?>) {
        val s = current?.takeIf { !it.ended } ?: return
        val label = CallLabel.fromTexts(texts) ?: return
        if (label == s.label) return
        pool.execute { applyLabel(s, label, "notification") }
    }

    /** Кнопки «Сбросить» и «Бан» под сообщением о входящем. null — звонок уже не идёт, пусть выполнит обычный обработчик. */
    fun onBotCommand(cmd: BotCommand): String? {
        val (number, ban) = when (cmd) {
            is BotCommand.Hang -> cmd.number to false
            is BotCommand.Ban -> cmd.pattern to true
            else -> return null
        }
        val s = current?.takeIf { !it.ended && (number == null || it.info.number == number) }
        if (s == null) return if (ban) null else "Звонок уже завершён."
        val n = s.info.number
        if (ban && n != null) repo.addToList(ListType.BLOCK, n, note = s.label ?: "из Telegram", ruleName = "Telegram")
        val ok = hangUp(s, CallEvent.Outcome.HUNG_UP_BOT)
        if (ok) {
            update(s) {
                it.copy(
                    action = if (ban) Action.BAN else Action.REJECT, source = Decision.Source.MANUAL,
                    ruleId = null, ruleName = if (ban) "Сброшен и забанен из Telegram" else "Сброшен из Telegram",
                    outcome = CallEvent.Outcome.HUNG_UP_BOT,
                )
            }
            pool.execute { refreshTelegram(s) }
        }
        val who = TelegramFormat.escape(PhoneNumbers.pretty(n))
        return when {
            ok && ban -> "🚫 <code>$who</code> — сброшен и в бан-листе"
            ok -> "⛔ <code>$who</code> — сброшен"
            ban -> "🚫 <code>$who</code> — в бан-листе. Сбросить не вышло: " + hangUpProblem()
            else -> "Сбросить не вышло: " + hangUpProblem()
        }
    }

    private fun hangUpProblem() =
        if (!granted(Manifest.permission.ANSWER_PHONE_CALLS)) "нет разрешения «Управление звонками»" else "звонок уже не звонит"

    // ---------------------------------------------------------------------------------------------

    private fun monitor(s: Session) {
        // Фильтр отвечает до того, как телефон зазвонит: ждём звонка.
        val waitUntil = s.started + 6_000
        while (System.currentTimeMillis() < waitUntil && callState() != TelephonyManager.CALL_STATE_RINGING) Thread.sleep(150)
        s.ringing = callState() == TelephonyManager.CALL_STATE_RINGING
        if (s.ringing) {
            pool.execute { botLoop(s) }
            val maxEnd = s.started + MAX_RING_MS
            var radioAt = 0L
            while (callState() == TelephonyManager.CALL_STATE_RINGING && System.currentTimeMillis() < maxEnd) {
                if (s.label == null && System.currentTimeMillis() - radioAt > 1_000) {
                    radioAt = System.currentTimeMillis()
                    RadioLog.label(app, s.started - 3_000)?.let { applyLabel(s, it, "radio log") }
                }
                if (!s.tgDone && readyToSend(s)) sendTelegram(s)
                Thread.sleep(250)
            }
        }
        val answered = callState() == TelephonyManager.CALL_STATE_OFFHOOK
        s.ended = true
        val outcome = s.hungUp ?: when {
            !s.ringing -> null
            answered -> CallEvent.Outcome.ANSWERED
            else -> CallEvent.Outcome.MISSED
        }
        update(s) { it.copy(outcome = outcome) }
        Log.i(TAG, "live ${s.info.number ?: "hidden"}: ${outcome ?: "not ringing"} label=${s.label}")

        // Звонилка пишет подпись в журнал вызовов при отбое — даём ей записать и забираем.
        Thread.sleep(1_500)
        if (s.label == null) CallLogReader.label(app, s.info.number, s.started)?.let { applyLabel(s, it, "call log") }
        if (!s.tgDone) sendTelegram(s) else refreshTelegram(s)
        // Кнопки под сообщением можно нажать и позже — бот опросится через пару минут.
        if (telegramOn()) BotPoller.pollSoon(app, delaySeconds = 120)
    }

    /** Отправляем, когда есть отзывы и подпись — или когда ждать их уже долго. */
    private fun readyToSend(s: Session): Boolean {
        val waited = System.currentTimeMillis() - s.started
        return (s.reportReady || waited > WAIT_REPORT_MS) && (s.label != null || waited > WAIT_LABEL_MS)
    }

    /** Пока звонит — непрерывно слушаем бота, чтобы кнопка «Сбросить» сработала сразу. */
    private fun botLoop(s: Session) {
        if (!telegramOn()) return
        while (!s.ended) {
            try {
                if (!BotPoller.pollOnce(app, timeoutSec = 5)) return
            } catch (e: TelegramClient.TelegramException) {
                Log.w(TAG, "live bot poll: ${e.message}")
                Thread.sleep(1_000)
            }
        }
    }

    /** Подпись оператора пришла: записываем её и пересчитываем правила. Во время звонка — сбрасываем, если правило велит. */
    private fun applyLabel(s: Session, label: String, from: String) {
        val duringCall = from != "call log"
        synchronized(s.lock) {
            if (label == s.label) return
            s.label = label
        }
        Log.i(TAG, "live ${s.info.number ?: "hidden"}: label «$label» ($from)")
        val event = repo.event(s.eventId) ?: return
        val d = app.processor.decide(
            s.info.copy(callerName = label, callerNamePresentation = CallInfo.Presentation.PRESENTED, report = event.report),
        )
        val dryRun = d.dryRunHits.takeIf { it.isNotEmpty() }?.joinToString("; ") { it.name }
        val byRule = d.source == Decision.Source.RULE && d.rejects
        val n = s.info.number
        var hung = false
        if (byRule && duringCall && !s.ended && s.hungUp == null) hung = hangUp(s, CallEvent.Outcome.HUNG_UP_RULE)
        if (byRule && d.action == Action.BAN && n != null) {
            repo.addToList(ListType.BLOCK, n, note = label, ruleName = d.ruleName)
        }
        update(s) { e ->
            var x = e.copy(callerName = label, dryRun = dryRun, wouldAction = if (byRule) d.action else d.wouldAction)
            when {
                hung -> x = x.copy(action = d.action, source = d.source, ruleId = d.ruleId, ruleName = d.ruleName, outcome = CallEvent.Outcome.HUNG_UP_RULE)
                // Не успели или нечем сбросить: звонок прошёл, но номер забанен на будущее.
                byRule && d.action == Action.BAN && n != null -> x = x.copy(ruleName = "${d.ruleName}: номер забанен после звонка")
                byRule -> x = x.copy(dryRun = listOfNotNull("${d.ruleName} (подпись пришла поздно)", dryRun).joinToString("; "))
            }
            x
        }
        if (s.tgDone) refreshTelegram(s) else if (s.ended || hung) sendTelegram(s)
    }

    private fun hangUp(s: Session, by: CallEvent.Outcome): Boolean {
        if (s.ended || callState() != TelephonyManager.CALL_STATE_RINGING) return false
        if (!granted(Manifest.permission.ANSWER_PHONE_CALLS)) {
            Log.w(TAG, "live: no ANSWER_PHONE_CALLS, cannot hang up")
            return false
        }
        @Suppress("DEPRECATION")
        val ok = runCatching { telecom.endCall() }.onFailure { Log.w(TAG, "endCall failed", it) }.getOrDefault(false)
        if (ok) s.hungUp = by
        Log.i(TAG, "live ${s.info.number ?: "hidden"}: hang up by $by -> $ok")
        return ok
    }

    @Suppress("DEPRECATION")
    private fun callState(): Int =
        if (granted(Manifest.permission.READ_PHONE_STATE)) runCatching { telephony.callState }.getOrDefault(TelephonyManager.CALL_STATE_IDLE)
        else TelephonyManager.CALL_STATE_IDLE

    // ---------------------------------------------------------------------------------------------

    private fun update(s: Session, change: (CallEvent) -> CallEvent) {
        synchronized(repo.eventLock) {
            val e = repo.event(s.eventId) ?: return
            val n = change(e)
            if (n != e) repo.updateEvent(n)
        }
    }

    /** Первое сообщение о звонке (если по настройкам о нём надо сообщать). */
    private fun sendTelegram(s: Session) {
        synchronized(s.lock) {
            if (s.tgDone) return
            val st = app.telegramSettings.snapshot()
            val e = repo.event(s.eventId) ?: return
            if (!st.enabled || !st.configured || !st.wants(e)) {
                // Сообщать не о чем — но если позже сработает правило, попробуем ещё раз.
                if (s.ended) s.tgDone = true
                return
            }
            s.tgDone = true
            try {
                val id = st.client().sendMessage(st.chatId, TelegramFormat.event(e), st.silent, buttons(e, st.commands))
                update(s) { it.copy(tgStatus = CallEvent.TgStatus.SENT, tgMessageId = id) }
                Log.i(TAG, "live ${s.info.number ?: "hidden"}: telegram sent in ${System.currentTimeMillis() - s.started}ms")
            } catch (ex: TelegramClient.TelegramException) {
                Log.w(TAG, "live telegram send failed: ${ex.message}")
                // Обычная очередь с повторами.
                update(s) { it.copy(tgStatus = CallEvent.TgStatus.PENDING) }
                app.telegram.schedule()
            }
        }
    }

    /** Обновить уже отправленное сообщение: подпись, отзывы, чем кончился звонок. */
    private fun refreshTelegram(s: Session) {
        synchronized(s.lock) {
            val e = repo.event(s.eventId) ?: return
            val id = e.tgMessageId ?: return
            val st = app.telegramSettings.snapshot()
            if (!st.configured) return
            runCatching { st.client().editMessage(st.chatId, id, TelegramFormat.event(e), buttons(e, st.commands)) }
                .onFailure { Log.w(TAG, "live telegram edit failed: ${it.message}") }
        }
    }

    private fun buttons(e: CallEvent, commands: Boolean): List<TelegramClient.Button> = when {
        !commands -> emptyList()
        e.outcome == CallEvent.Outcome.RINGING -> BotPoller.liveButtons(e)
        else -> BotPoller.buttonsFor(e)
    }

    companion object {
        private const val TAG = "Autoban"
        private const val MAX_RING_MS = 3 * 60_000L
        /** Сколько ждать подпись оператора и отзывы, прежде чем отправить сообщение без них. */
        private const val WAIT_LABEL_MS = 3_000L
        private const val WAIT_REPORT_MS = 4_000L

        fun hasNotificationAccess(context: Context): Boolean =
            androidx.core.app.NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
}
