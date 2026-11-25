package com.blkdem.autoban.telegram

import com.blkdem.autoban.data.CallEvent
import com.blkdem.autoban.lookup.NumberReport
import com.blkdem.autoban.rules.Action
import com.blkdem.autoban.rules.PhoneNumbers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Формирование сообщений для Telegram (HTML parse_mode). Чистый Kotlin — покрыт тестами. */
object TelegramFormat {

    fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun head(e: CallEvent): String = when (e.outcome) {
        CallEvent.Outcome.RINGING -> "📞 <b>ЗВОНИТ</b>"
        CallEvent.Outcome.HUNG_UP_BOT -> if (e.action == Action.BAN) "🚫 <b>БАН</b>, сброшен из Telegram" else "⛔ <b>СБРОШЕН</b> из Telegram"
        CallEvent.Outcome.HUNG_UP_RULE -> if (e.action == Action.BAN) "🚫 <b>БАН</b> по подписи" else "⛔ <b>СБРОС</b> по подписи"
        CallEvent.Outcome.ANSWERED -> "✅ <b>ПРИНЯТ</b>"
        CallEvent.Outcome.MISSED -> "📵 <b>НЕ ОТВЕЧЕН</b>"
        null -> headByAction(e)
    }

    private fun headByAction(e: CallEvent): String = when {
        e.action == Action.BAN -> "🚫 <b>БАН</b>"
        e.action == Action.REJECT -> "⛔ <b>СБРОС</b>"
        e.dryRun != null -> "🧪 <b>DRY-RUN</b>"
        else -> "📞 <b>ВХОДЯЩИЙ</b>"
    }

    private fun time(ts: Long, tz: TimeZone): String =
        SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.US).apply { timeZone = tz }.format(Date(ts))

    fun event(e: CallEvent, tz: TimeZone = TimeZone.getDefault()): String = buildString {
        append(head(e)).append(" · SIM").append(e.simSlot ?: "?").append('\n')
        append("<code>").append(escape(PhoneNumbers.pretty(e.number))).append("</code>")
        e.callerName?.let { append(" · «").append(escape(it)).append('»') }
        append('\n')
        e.report?.let { append(report(it, comments = 1)).append('\n') }
        e.ruleName?.let { append("Правило: ").append(escape(it)).append('\n') }
        e.dryRun?.let { append("🧪 Сработало бы: ").append(escape(it)).append(" → ").append(would(e.wouldAction)).append('\n') }
        append(time(e.timestamp, tz))
        e.report?.let { r -> r.url?.let { append(" · ").append(link(it, r.source)) } }
    }

    /** Сводка о номере: оценка и типы, оператор и регион, последние отзывы. Без перевода строки в конце. */
    fun report(r: NumberReport, comments: Int = 2): String = buildList {
        add(r.icon + " " + escape(r.summary()))
        r.where()?.let { add(escape(it)) }
        for (c in r.comments.take(comments)) {
            val text = if (c.text.length > COMMENT_CHARS) c.text.take(COMMENT_CHARS - 1).trimEnd() + "…" else c.text
            val date = c.date?.substringBefore(' ')?.let { "$it: " } ?: ""
            val type = c.type?.let { " (" + escape(it) + ")" } ?: ""
            add("💬 $date<i>${escape(text)}</i>$type")
        }
    }.joinToString("\n")

    /** Ответ на /who. */
    fun who(number: String, r: NumberReport?): String = buildString {
        append("🔎 <code>").append(escape(PhoneNumbers.pretty(number))).append("</code>\n")
        if (r == null) {
            append("Не удалось получить сведения: сайт не ответил.")
        } else {
            append(report(r, comments = 3))
            r.url?.let { append('\n').append(link(it, r.source)) }
        }
    }

    private fun link(url: String, title: String) = "<a href=\"" + escape(url).replace("\"", "&quot;") + "\">" + escape(title) + "</a>"

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
            e.report?.takeIf { it.found && it.reviews > 0 }?.let { r ->
                append(' ').append(r.icon)
                r.rating?.let { append(NumberReport.fmtRating(it)).append("/5") }
                r.categories.firstOrNull()?.let { append(' ').append(escape(it)) }
            }
            (e.ruleName ?: e.dryRun)?.let { append(" — ").append(escape(it)) }
        }
        if (events.size > MAX_DIGEST_LINES) append("\n… и ещё ").append(events.size - MAX_DIGEST_LINES)
    }

    private fun icon(e: CallEvent) = when {
        e.action == Action.BAN -> "🚫"
        e.action == Action.REJECT -> "⛔"
        e.dryRun != null -> "🧪"
        else -> "📞"
    }

    private fun would(a: Action) = when (a) {
        Action.ALLOW -> "пропустить"
        Action.REJECT -> "сброс"
        Action.BAN -> "бан"
    }

    private const val MAX_DIGEST_LINES = 40
    private const val COMMENT_CHARS = 200
}
