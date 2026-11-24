package com.blkdem.autoban.telegram

import com.blkdem.autoban.data.CallEvent
import com.blkdem.autoban.rules.Action
import com.blkdem.autoban.rules.PhoneNumbers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Формирование сообщений для Telegram (HTML parse_mode). Чистый Kotlin — покрыт тестами. */
object TelegramFormat {

    fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun head(e: CallEvent): String = when {
        e.action == Action.BAN -> "🚫 <b>БАН</b>"
        e.action == Action.REJECT -> "⛔ <b>СБРОС</b>"
        e.dryRun != null -> "🧪 <b>DRY-RUN</b>"
        else -> "✅ <b>ПРОПУЩЕН</b>"
    }

    private fun time(ts: Long, tz: TimeZone): String =
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).apply { timeZone = tz }.format(Date(ts))

    fun event(e: CallEvent, tz: TimeZone = TimeZone.getDefault()): String = buildString {
        append(head(e)).append(" · SIM").append(e.simSlot ?: "?").append('\n')
        append("<code>").append(escape(PhoneNumbers.pretty(e.number))).append("</code>")
        e.callerName?.let { append(" · «").append(escape(it)).append('»') }
        append('\n')
        e.ruleName?.let { append("Правило: ").append(escape(it)).append('\n') }
        e.dryRun?.let { append("🧪 Сработало бы: ").append(escape(it)).append(" → ").append(would(e.wouldAction)).append('\n') }
        append(time(e.timestamp, tz))
    }

    fun digest(events: List<CallEvent>, tz: TimeZone = TimeZone.getDefault()): String = buildString {
        val banned = events.count { it.action == Action.BAN }
        val rejected = events.count { it.action == Action.REJECT }
        append("📋 <b>Autoban</b>: ").append(events.size).append(" соб.")
        if (banned > 0) append(" · 🚫 ").append(banned)
        if (rejected > 0) append(" · ⛔ ").append(rejected)
        append('\n')
        val shortTime = SimpleDateFormat("HH:mm", Locale.US).apply { timeZone = tz }
        for (e in events.take(MAX_DIGEST_LINES)) {
            append('\n')
            append(shortTime.format(Date(e.timestamp))).append(' ')
            append(icon(e)).append(" SIM").append(e.simSlot ?: "?").append(' ')
            append("<code>").append(escape(PhoneNumbers.pretty(e.number))).append("</code>")
            e.callerName?.let { append(" «").append(escape(it)).append('»') }
            (e.ruleName ?: e.dryRun)?.let { append(" — ").append(escape(it)) }
        }
        if (events.size > MAX_DIGEST_LINES) append("\n… и ещё ").append(events.size - MAX_DIGEST_LINES)
    }

    private fun icon(e: CallEvent) = when {
        e.action == Action.BAN -> "🚫"
        e.action == Action.REJECT -> "⛔"
        e.dryRun != null -> "🧪"
        else -> "✅"
    }

    private fun would(a: Action) = when (a) {
        Action.ALLOW -> "пропустить"
        Action.REJECT -> "сброс"
        Action.BAN -> "бан"
    }

    private const val MAX_DIGEST_LINES = 40
}
