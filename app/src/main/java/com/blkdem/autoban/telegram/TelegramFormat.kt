package com.blkdem.autoban.telegram

import com.blkdem.autoban.data.CallEvent
import com.blkdem.autoban.lookup.NumberReport
import com.blkdem.autoban.rules.Action
import com.blkdem.autoban.rules.CallLabel
import com.blkdem.autoban.rules.Decision
import com.blkdem.autoban.rules.ListEntry
import com.blkdem.autoban.rules.PhoneNumbers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Формирование сообщений для Telegram (HTML parse_mode). Чистый Kotlin — покрыт тестами. */
object TelegramFormat {

    fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** Что известно о номере помимо самого звонка: история и списки (из БД на момент отправки). */
    data class Extras(
        /** Сколько звонков с номера было до этого. */
        val previousCalls: Int = 0,
        val lastCall: Long? = null,
        val blocked: ListEntry? = null,
        val white: ListEntry? = null,
    )

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

    private fun date(ts: Long, tz: TimeZone): String =
        SimpleDateFormat("dd.MM.yyyy", Locale.US).apply { timeZone = tz }.format(Date(ts))

    private fun sim(slot: Int?, label: String?): String =
        "SIM" + (slot ?: "?") + (label?.takeIf { it.isNotBlank() }?.let { " (" + escape(it) + ")" } ?: "")

    /**
     * Сообщение о звонке: что сделал Autoban и всё, что известно о номере, —
     * подпись оператора и откуда она, контакт, отзывы, история звонков, списки, причина решения.
     */
    fun event(e: CallEvent, x: Extras = Extras(), tz: TimeZone = TimeZone.getDefault()): String = buildString {
        append(head(e)).append(" · ").append(sim(e.simSlot, e.simLabel)).append('\n')
        append("<code>").append(escape(PhoneNumbers.pretty(e.number))).append("</code>")
        PhoneNumbers.kind(e.number)?.let { append(" · ").append(it) }
        append('\n')
        e.contactName?.let { append("👤 Контакт: <b>").append(escape(it)).append("</b>\n") }
        label(e)?.let { append(it).append('\n') }
        e.report?.let { append(report(it, comments = 2, prefix = "Отзывы: ")).append('\n') }
        if (e.number != null) {
            append("📒 ")
            if (x.previousCalls == 0) append("Раньше не звонил")
            else {
                append("Звонил раньше: ").append(x.previousCalls).append(' ').append(NumberReport.plural(x.previousCalls, "раз", "раза", "раз"))
                x.lastCall?.let { append(", последний ").append(time(it, tz)) }
            }
            append('\n')
        }
        x.white?.let { append("✅ В белом списке").append(entry(it, e, tz)).append('\n') }
        x.blocked?.let {
            append(if (it.createdAt >= e.timestamp) "🚫 Добавлен в бан-лист" else "🚫 В бан-листе").append(entry(it, e, tz)).append('\n')
        }
        append("⚖️ ").append(reason(e)).append('\n')
        e.dryRun?.let { append("🧪 Сработало бы: ").append(escape(it)).append(" → ").append(would(e.wouldAction)).append('\n') }
        append(time(e.timestamp, tz))
        e.report?.let { r -> r.url?.let { append(" · ").append(link(it, r.source)) } }
    }

    /** Подпись звонка и откуда она. null — подписи нет и ждать её не к чему (контакт, скрытый номер). */
    private fun label(e: CallEvent): String? {
        val name = e.callerName
        if (name == null) {
            if (e.contactName != null) return null
            return if (e.outcome == CallEvent.Outcome.RINGING) "🏷 Маркировки пока нет" else "🏷 Маркировки нет"
        }
        val kind = if (CallLabel.isOperatorLabel(name)) "Маркировка" else "Имя от сети"
        val from = e.labelSource?.let { " · <i>" + it.title + "</i>" } ?: ""
        return "🏷 $kind: «${escape(name)}»$from"
    }

    /** « с 25.11.2026 по маске +7495* — ООО Вектор (правило «вектор»)». */
    private fun entry(l: ListEntry, e: CallEvent, tz: TimeZone): String = buildString {
        if (l.createdAt in 1 until e.timestamp) append(" с ").append(date(l.createdAt, tz))
        if (l.pattern != e.number) append(" по маске <code>").append(escape(l.pattern)).append("</code>")
        val note = l.note?.takeIf { it.isNotBlank() && it != l.ruleName }
        note?.let { append(" — ").append(escape(it)) }
        l.ruleName?.takeIf { it.isNotBlank() }?.let { append(" (").append(escape(it)).append(')') }
    }

    /** Почему звонок пропущен или сброшен. */
    private fun reason(e: CallEvent): String = when (e.source) {
        Decision.Source.RULE -> "Правило «" + escape(e.ruleName ?: "?") + "» → " + done(e.action)
        Decision.Source.BLOCKLIST -> "Номер в бан-листе → " + done(e.action)
        Decision.Source.WHITELIST -> "Белый список → пропущен"
        Decision.Source.DISABLED -> "Защита выключена → пропущен" + (e.ruleName?.let { " (сработало бы: " + escape(it) + ")" } ?: "")
        Decision.Source.MANUAL -> escape(e.ruleName ?: "Вручную")
        Decision.Source.CONTACT ->
            if (e.contactName != null) "Номер из контактов → пропущен (фильтр Android их не проверяет)"
            else "Фильтр Android звонок не проверял (обычно так с номерами из контактов) → пропущен"
        Decision.Source.DEFAULT -> "Ни одно правило не сработало → пропущен" + (e.ruleName?.let { "; " + escape(it) } ?: "")
    }

    /** Сводка о номере: оценка и типы, оператор и регион, последние отзывы. Без перевода строки в конце. */
    fun report(r: NumberReport, comments: Int = 2, prefix: String = ""): String = buildList {
        add(r.icon + " " + prefix + escape(r.summary()))
        r.where()?.let { add("📍 " + escape(it)) }
        for (c in r.comments.take(comments)) {
            val text = if (c.text.length > COMMENT_CHARS) c.text.take(COMMENT_CHARS - 1).trimEnd() + "…" else c.text
            val date = c.date?.substringBefore(' ')?.let { "$it: " } ?: ""
            val type = c.type?.let { " (" + escape(it) + ")" } ?: ""
            add("💬 $date<i>${escape(text)}</i>$type")
        }
    }.joinToString("\n")

    /** Ответ на /who. */
    fun who(number: String, r: NumberReport?): String = buildString {
        append("🔎 <code>").append(escape(PhoneNumbers.pretty(number))).append("</code>")
        PhoneNumbers.kind(number)?.let { append(" · ").append(it) }
        append('\n')
        if (r == null) {
            append("Не удалось получить сведения: сайт не ответил.")
        } else {
            append(report(r, comments = 3))
            r.url?.let { append('\n').append(link(it, r.source)) }
        }
    }

    /**
     * Входящая SMS. [from] — номер или буквенное имя отправителя («Sberbank»).
     * Текст обрезается, чтобы сообщение влезло в лимит Telegram (4096 символов).
     */
    fun sms(from: String?, contact: String?, simSlot: Int?, simLabel: String?, text: String, ts: Long, tz: TimeZone = TimeZone.getDefault()): String = buildString {
        append("✉️ <b>SMS</b> · ").append(sim(simSlot, simLabel)).append('\n')
        val number = PhoneNumbers.normalize(from)?.takeIf { it.startsWith("+") }
        when {
            number != null -> append("<code>").append(escape(PhoneNumbers.pretty(number))).append("</code>")
            from.isNullOrBlank() -> append("<code>скрыт</code>")
            else -> append("<b>").append(escape(from)).append("</b>")
        }
        contact?.let { append(" · 👤 <b>").append(escape(it)).append("</b>") }
        append("\n\n")
        val body = if (text.length > MAX_SMS_CHARS) text.take(MAX_SMS_CHARS - 1) + "…" else text
        append(escape(body)).append("\n\n")
        append(time(ts, tz))
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
            e.contactName?.let { append(" 👤").append(escape(it)) }
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

    private fun done(a: Action) = when (a) {
        Action.ALLOW -> "пропущен"
        Action.REJECT -> "сброшен"
        Action.BAN -> "сброшен и забанен"
    }

    private const val MAX_DIGEST_LINES = 40
    private const val COMMENT_CHARS = 200
    private const val MAX_SMS_CHARS = 3500
}
