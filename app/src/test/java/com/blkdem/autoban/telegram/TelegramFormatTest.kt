package com.blkdem.autoban.telegram

import com.blkdem.autoban.data.CallEvent
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

    @Test fun digest() {
        val list = listOf(ev(Action.BAN, rule = "r1"), ev(Action.REJECT, rule = "r2"), ev(Action.ALLOW, dry = "d"))
        val m = TelegramFormat.digest(list, tz)
        assertTrue(m.startsWith("📋 <b>Autoban</b>: 3 соб. · 🚫 1 · ⛔ 1\n"))
        assertEquals(3, m.lines().count { it.startsWith("14:12 ") })
    }
}
