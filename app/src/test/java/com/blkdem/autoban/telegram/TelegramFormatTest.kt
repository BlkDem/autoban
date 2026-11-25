package com.blkdem.autoban.telegram

import com.blkdem.autoban.data.CallEvent
import com.blkdem.autoban.lookup.NumberReport
import com.blkdem.autoban.rules.Action
import com.blkdem.autoban.rules.CallInfo
import com.blkdem.autoban.rules.Decision
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class TelegramFormatTest {

    private val tz = TimeZone.getTimeZone("Asia/Novosibirsk")
    private val ts = java.time.ZonedDateTime.of(2026, 11, 25, 14, 12, 0, 0, tz.toZoneId()).toInstant().toEpochMilli()

    private fun ev(action: Action, number: String? = "+79123456789", name: String? = null, rule: String? = null, dry: String? = null, would: Action = action) =
        CallEvent(
            id = 1, timestamp = ts, rawNumber = number, number = number,
            presentation = if (number == null) CallInfo.Presentation.HIDDEN else CallInfo.Presentation.PRESENTED,
            callerName = name, simSlot = 2, simLabel = null, action = action, source = Decision.Source.RULE,
            ruleId = 1, ruleName = rule, dryRun = dry, wouldAction = would,
        )

    @Test fun banMessage() {
        val m = TelegramFormat.event(ev(Action.BAN, name = "Ростелеком", rule = "Маркированные на SIM2"), tz)
        assertEquals(
            "🚫 <b>БАН</b> · SIM2\n<code>+7 912 345-67-89</code> · «Ростелеком»\nПравило: Маркированные на SIM2\n25.11.2026 14:12",
            m,
        )
    }

    @Test fun hiddenRejected() {
        val m = TelegramFormat.event(ev(Action.REJECT, number = null, rule = "Скрытые на SIM1"), tz)
        assertTrue(m.startsWith("⛔ <b>СБРОС</b>"))
        assertTrue(m.contains("<code>скрыт</code>"))
    }

    @Test fun dryRunMessage() {
        val m = TelegramFormat.event(ev(Action.ALLOW, dry = "Скрытые на SIM1", would = Action.REJECT), tz)
        assertTrue(m.startsWith("🧪 <b>DRY-RUN</b>"))
        assertTrue(m.contains("Сработало бы: Скрытые на SIM1 → сброс"))
    }

    @Test fun escapesHtml() {
        val m = TelegramFormat.event(ev(Action.BAN, name = "<ООО & Ко>"), tz)
        assertTrue(m.contains("«&lt;ООО &amp; Ко&gt;»"))
    }

    @Test fun allowedWithReport() {
        val r = NumberReport(
            "+79123456789", found = true, rating = 1.35, reviews = 7631, categories = listOf("Мошенники", "Реклама"),
            operator = "ПАО \"МТС\"", region = "г. Москва",
            comments = listOf(NumberReport.Comment("18.10.2026 19:53", "Мошенники", "Звонят <снова>")),
            url = "https://zvonili.com/phone/9123456789",
        )
        val e = ev(Action.ALLOW).copy(source = Decision.Source.DEFAULT, ruleName = null, report = r)
        assertEquals(
            "📞 <b>ВХОДЯЩИЙ</b> · SIM2\n<code>+7 912 345-67-89</code>\n" +
                "⚠️ 1.35/5 · 7631 отзыв · Мошенники, Реклама\n" +
                "ПАО \"МТС\" · г. Москва\n" +
                "💬 18.10.2026: <i>Звонят &lt;снова&gt;</i> (Мошенники)\n" +
                "25.11.2026 14:12 · <a href=\"https://zvonili.com/phone/9123456789\">zvonili.com</a>",
            TelegramFormat.event(e, tz),
        )
        val line = TelegramFormat.digest(listOf(e), tz).lines().last()
        assertEquals("14:12 📞 SIM2 <code>+7 912 345-67-89</code> ⚠️1.35/5 Мошенники", line)
    }

    @Test fun liveOutcomeHeads() {
        val base = ev(Action.ALLOW, name = "Ростелеком: связь").copy(source = Decision.Source.DEFAULT, ruleName = null)
        fun head(e: CallEvent) = TelegramFormat.event(e, tz).lineSequence().first()
        assertEquals("📞 <b>ЗВОНИТ</b> · SIM2", head(base.copy(outcome = CallEvent.Outcome.RINGING)))
        assertEquals("📵 <b>НЕ ОТВЕЧЕН</b> · SIM2", head(base.copy(outcome = CallEvent.Outcome.MISSED)))
        assertEquals("✅ <b>ПРИНЯТ</b> · SIM2", head(base.copy(outcome = CallEvent.Outcome.ANSWERED)))
        assertEquals("⛔ <b>СБРОШЕН</b> из Telegram · SIM2", head(base.copy(action = Action.REJECT, outcome = CallEvent.Outcome.HUNG_UP_BOT)))
        assertEquals("🚫 <b>БАН</b>, сброшен из Telegram · SIM2", head(base.copy(action = Action.BAN, outcome = CallEvent.Outcome.HUNG_UP_BOT)))
        assertEquals("🚫 <b>БАН</b> по подписи · SIM2", head(base.copy(action = Action.BAN, outcome = CallEvent.Outcome.HUNG_UP_RULE)))
        assertTrue(TelegramFormat.event(base.copy(outcome = CallEvent.Outcome.RINGING), tz).contains("«Ростелеком: связь»"))
    }

    @Test fun whoReply() {
        val none = TelegramFormat.who("+79123456789", NumberReport("+79123456789", found = true, url = "https://zvonili.com/phone/9123456789"))
        assertEquals(
            "🔎 <code>+7 912 345-67-89</code>\nℹ️ отзывов нет\n<a href=\"https://zvonili.com/phone/9123456789\">zvonili.com</a>",
            none,
        )
        assertTrue(TelegramFormat.who("+79123456789", null).contains("сайт не ответил"))
    }

    @Test fun digest() {
        val list = listOf(ev(Action.BAN, rule = "r1"), ev(Action.REJECT, rule = "r2"), ev(Action.ALLOW, dry = "d"))
        val m = TelegramFormat.digest(list, tz)
        assertTrue(m.startsWith("📋 <b>Autoban</b>: 3 соб. · 🚫 1 · ⛔ 1\n"))
        assertEquals(3, m.lines().count { it.startsWith("14:12 ") })
    }
}
