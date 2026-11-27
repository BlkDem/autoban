package com.blkdem.autoban.telegram

import com.blkdem.autoban.data.CallEvent
import com.blkdem.autoban.lookup.NumberReport
import com.blkdem.autoban.rules.Action
import com.blkdem.autoban.rules.CallInfo
import com.blkdem.autoban.rules.Decision
import com.blkdem.autoban.rules.ListEntry
import com.blkdem.autoban.rules.ListType
import com.blkdem.autoban.rules.PhoneNumbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class TelegramFormatTest {

    private val tz = TimeZone.getTimeZone("Asia/Novosibirsk")
    private val ts = java.time.ZonedDateTime.of(2026, 11, 25, 14, 12, 0, 0, tz.toZoneId()).toInstant().toEpochMilli()
    private val day = 86_400_000L

    private fun ev(action: Action, number: String? = "+79123456789", name: String? = null, rule: String? = null, dry: String? = null, would: Action = action) =
        CallEvent(
            id = 1, timestamp = ts, rawNumber = number, number = number,
            presentation = if (number == null) CallInfo.Presentation.HIDDEN else CallInfo.Presentation.PRESENTED,
            callerName = name, simSlot = 2, simLabel = null, action = action, source = Decision.Source.RULE,
            ruleId = 1, ruleName = rule, dryRun = dry, wouldAction = would,
        )

    @Test fun banByMarkingRule() {
        val e = ev(Action.BAN, name = "ООО ВЕКТОР: прочие услуги", rule = "*вектор*")
            .copy(simSlot = 1, simLabel = "Beeline", labelSource = CallInfo.LabelSource.RADIO_LOG)
        val added = ListEntry(type = ListType.BLOCK, pattern = "+79123456789", note = "ООО ВЕКТОР: прочие услуги", createdAt = ts + 50, ruleName = "*вектор*")
        assertEquals(
            "🚫 <b>БАН</b> · SIM1 (Beeline)\n" +
                "<code>+7 912 345-67-89</code> · мобильный\n" +
                "🏷 Маркировка: «ООО ВЕКТОР: прочие услуги» · <i>системный лог</i>\n" +
                "📒 Раньше не звонил\n" +
                "🚫 Добавлен в бан-лист — ООО ВЕКТОР: прочие услуги (*вектор*)\n" +
                "⚖️ Правило «*вектор*» → сброшен и забанен\n" +
                "25.11.2026 14:12",
            TelegramFormat.event(e, TelegramFormat.Extras(blocked = added), tz),
        )
    }

    @Test fun rejectedByBlocklistWithHistory() {
        val r = NumberReport("+79123456789", found = false, url = "https://zvonili.com/phone/9123456789")
        val e = ev(Action.REJECT, rule = "Бан-лист: +79123456789").copy(source = Decision.Source.BLOCKLIST, report = r)
        val old = ListEntry(type = ListType.BLOCK, pattern = "+79123456789", note = "ООО ВЕКТОР: прочие услуги", createdAt = ts - day, ruleName = "*вектор*")
        val m = TelegramFormat.event(e, TelegramFormat.Extras(previousCalls = 2, lastCall = ts - day, blocked = old), tz)
        assertEquals(
            "⛔ <b>СБРОС</b> · SIM2\n" +
                "<code>+7 912 345-67-89</code> · мобильный\n" +
                "🏷 Маркировки нет\n" +
                "ℹ️ Отзывы: номер не найден\n" +
                "📒 Звонил раньше: 2 раза, последний 24.11.2026 14:12\n" +
                "🚫 В бан-листе с 24.11.2026 — ООО ВЕКТОР: прочие услуги (*вектор*)\n" +
                "⚖️ Номер в бан-листе → сброшен\n" +
                "25.11.2026 14:12 · <a href=\"https://zvonili.com/phone/9123456789\">zvonili.com</a>",
            m,
        )
    }

    @Test fun maskEntryIsShown() {
        val e = ev(Action.REJECT).copy(source = Decision.Source.BLOCKLIST)
        val mask = ListEntry(type = ListType.BLOCK, pattern = "+7912*", createdAt = ts - day)
        assertTrue(TelegramFormat.event(e, TelegramFormat.Extras(blocked = mask), tz)
            .contains("🚫 В бан-листе с 24.11.2026 по маске <code>+7912*</code>\n"))
    }

    @Test fun hiddenRejected() {
        val m = TelegramFormat.event(ev(Action.REJECT, number = null, rule = "Скрытые на SIM1"), tz = tz)
        assertTrue(m.startsWith("⛔ <b>СБРОС</b>"))
        assertTrue(m.contains("<code>скрыт</code>\n"))
        assertFalse("у скрытого номера нет истории", m.contains("📒"))
    }

    @Test fun dryRunMessage() {
        val m = TelegramFormat.event(ev(Action.ALLOW, dry = "Скрытые на SIM1", would = Action.REJECT).copy(source = Decision.Source.DEFAULT), tz = tz)
        assertTrue(m.startsWith("🧪 <b>DRY-RUN</b>"))
        assertTrue(m.contains("⚖️ Ни одно правило не сработало → пропущен\n"))
        assertTrue(m.contains("Сработало бы: Скрытые на SIM1 → сброс"))
    }

    @Test fun escapesHtml() {
        val m = TelegramFormat.event(ev(Action.BAN, name = "<ООО & Ко>", rule = "a<b"), tz = tz)
        assertTrue(m.contains("Имя от сети: «&lt;ООО &amp; Ко&gt;»"))
        assertTrue(m.contains("Правило «a&lt;b»"))
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
            "📞 <b>ВХОДЯЩИЙ</b> · SIM2\n<code>+7 912 345-67-89</code> · мобильный\n" +
                "🏷 Маркировки нет\n" +
                "⚠️ Отзывы: 1.35/5 · 7631 отзыв · Мошенники, Реклама\n" +
                "📍 ПАО \"МТС\" · г. Москва\n" +
                "💬 18.10.2026: <i>Звонят &lt;снова&gt;</i> (Мошенники)\n" +
                "📒 Раньше не звонил\n" +
                "⚖️ Ни одно правило не сработало → пропущен\n" +
                "25.11.2026 14:12 · <a href=\"https://zvonili.com/phone/9123456789\">zvonili.com</a>",
            TelegramFormat.event(e, tz = tz),
        )
        val line = TelegramFormat.digest(listOf(e), tz).lines().last()
        assertEquals("14:12 📞 SIM2 <code>+7 912 345-67-89</code> ⚠️1.35/5 Мошенники", line)
    }

    @Test fun contactCall() {
        val e = ev(Action.ALLOW, number = "+74951234567").copy(
            source = Decision.Source.CONTACT, ruleName = null, contactName = "Мама", outcome = CallEvent.Outcome.RINGING,
        )
        assertEquals(
            "📞 <b>ЗВОНИТ</b> · SIM2\n<code>+7 495 123-45-67</code> · городской\n" +
                "👤 Контакт: <b>Мама</b>\n" +
                "📒 Звонил раньше: 5 раз, последний 24.11.2026 14:12\n" +
                "⚖️ Номер из контактов → пропущен (фильтр Android их не проверяет)\n" +
                "25.11.2026 14:12",
            TelegramFormat.event(e, TelegramFormat.Extras(previousCalls = 5, lastCall = ts - day), tz),
        )
        val unknown = e.copy(contactName = null)
        assertTrue(TelegramFormat.event(unknown, tz = tz).contains("🏷 Маркировки пока нет"))
        assertTrue(TelegramFormat.event(unknown, tz = tz).contains("Фильтр Android звонок не проверял"))
    }

    @Test fun liveOutcomeHeads() {
        val base = ev(Action.ALLOW, name = "Ростелеком: связь").copy(source = Decision.Source.DEFAULT, ruleName = null)
        fun head(e: CallEvent) = TelegramFormat.event(e, tz = tz).lineSequence().first()
        assertEquals("📞 <b>ЗВОНИТ</b> · SIM2", head(base.copy(outcome = CallEvent.Outcome.RINGING)))
        assertEquals("📵 <b>НЕ ОТВЕЧЕН</b> · SIM2", head(base.copy(outcome = CallEvent.Outcome.MISSED)))
        assertEquals("✅ <b>ПРИНЯТ</b> · SIM2", head(base.copy(outcome = CallEvent.Outcome.ANSWERED)))
        assertEquals("⛔ <b>СБРОШЕН</b> из Telegram · SIM2", head(base.copy(action = Action.REJECT, outcome = CallEvent.Outcome.HUNG_UP_BOT)))
        assertEquals("🚫 <b>БАН</b>, сброшен из Telegram · SIM2", head(base.copy(action = Action.BAN, outcome = CallEvent.Outcome.HUNG_UP_BOT)))
        assertEquals("🚫 <b>БАН</b> по подписи · SIM2", head(base.copy(action = Action.BAN, outcome = CallEvent.Outcome.HUNG_UP_RULE)))
        assertTrue(TelegramFormat.event(base.copy(outcome = CallEvent.Outcome.RINGING), tz = tz).contains("🏷 Маркировка: «Ростелеком: связь»"))
    }

    @Test fun whoReply() {
        val none = TelegramFormat.who("+79123456789", NumberReport("+79123456789", found = true, url = "https://zvonili.com/phone/9123456789"))
        assertEquals(
            "🔎 <code>+7 912 345-67-89</code> · мобильный\nℹ️ отзывов нет\n<a href=\"https://zvonili.com/phone/9123456789\">zvonili.com</a>",
            none,
        )
        assertTrue(TelegramFormat.who("+79123456789", null).contains("сайт не ответил"))
    }

    @Test fun smsFromNumberAndAlpha() {
        assertEquals(
            "✉️ <b>SMS</b> · SIM1 (Beeline)\n<code>+7 912 345-67-89</code> · 👤 <b>Мама</b>\n\nПривет &lt;3\n\n25.11.2026 14:12",
            TelegramFormat.sms("89123456789", "Мама", 1, "Beeline", "Привет <3", ts, tz),
        )
        assertEquals(
            "✉️ <b>SMS</b> · SIM?\n<b>Sberbank</b>\n\nКод 1234\n\n25.11.2026 14:12",
            TelegramFormat.sms("Sberbank", null, null, null, "Код 1234", ts, tz),
        )
        val long = TelegramFormat.sms("900", null, 2, null, "x".repeat(5000), ts, tz)
        assertTrue(long.length < 4096)
        assertTrue(long.contains("x…"))
    }

    @Test fun numberKinds() {
        assertEquals("мобильный", PhoneNumbers.kind("+79123456789"))
        assertEquals("городской", PhoneNumbers.kind("+74951234567"))
        assertEquals("городской", PhoneNumbers.kind("+78123456789"))
        assertEquals("бесплатный 8-800", PhoneNumbers.kind("+78001234567"))
        assertEquals("платный сервисный 8-80x", PhoneNumbers.kind("+78091234567"))
        assertEquals("Казахстан", PhoneNumbers.kind("+77011234567"))
        assertEquals("международный", PhoneNumbers.kind("+905321234567"))
        assertEquals("короткий", PhoneNumbers.kind("900"))
        assertEquals(null, PhoneNumbers.kind(null))
    }

    @Test fun digest() {
        val list = listOf(ev(Action.BAN, rule = "r1"), ev(Action.REJECT, rule = "r2"), ev(Action.ALLOW, dry = "d"), ev(Action.ALLOW).copy(contactName = "Мама"))
        val m = TelegramFormat.digest(list, tz)
        assertTrue(m.startsWith("📋 <b>Autoban</b>: 4 соб. · 🚫 1 · ⛔ 1\n"))
        assertEquals(4, m.lines().count { it.startsWith("14:12 ") })
        assertTrue(m.contains("👤Мама"))
    }
}
