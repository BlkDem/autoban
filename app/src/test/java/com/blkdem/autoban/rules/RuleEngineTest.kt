package com.blkdem.autoban.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class RuleEngineTest {

    private val engine = RuleEngine()
    private val zone = ZoneId.of("Asia/Novosibirsk")
    private val active = Rules.defaults.mapIndexed { i, r -> r.copy(id = i + 1L, mode = RuleMode.ACTIVE) }

    private fun call(
        number: String? = "+79123456789",
        sim: Int? = 1,
        name: String? = null,
        presentation: CallInfo.Presentation = if (number == null) CallInfo.Presentation.HIDDEN else CallInfo.Presentation.PRESENTED,
        at: LocalDateTime = LocalDateTime.of(2026, 11, 25, 12, 0), // среда
    ) = CallInfo(
        timestamp = at.atZone(zone).toInstant().toEpochMilli(),
        rawNumber = number, presentation = presentation, callerName = name, simSlot = sim,
    )

    private fun ctx(
        rules: List<Rule> = active,
        white: List<String> = emptyList(),
        block: List<String> = emptyList(),
        recent: Int = 0,
        protection: Boolean = true,
    ) = EvalContext(
        rules = rules,
        whitelist = white.map { ListEntry(type = ListType.WHITE, pattern = it) },
        blocklist = block.map { ListEntry(type = ListType.BLOCK, pattern = it) },
        recentCalls = { _, _ -> recent },
        zone = zone,
        protectionEnabled = protection,
    )

    // --- требования владельца ---

    @Test fun rule1_hiddenOnSim1_rejected() {
        val d = engine.evaluate(call(number = null, sim = 1), ctx())
        assertEquals(Action.REJECT, d.action)
        assertEquals("Скрытые на SIM1", d.ruleName)
    }

    @Test fun rule1_unknownPresentationCountsAsHidden() {
        val d = engine.evaluate(call(number = null, sim = 1, presentation = CallInfo.Presentation.UNKNOWN), ctx())
        assertEquals(Action.REJECT, d.action)
    }

    @Test fun rule1_presentedOnSim1_allowed() {
        assertEquals(Action.ALLOW, engine.evaluate(call(sim = 1), ctx()).action)
    }

    @Test fun rule1_hiddenOnSim2_allowed() {
        assertEquals(Action.ALLOW, engine.evaluate(call(number = null, sim = 2), ctx()).action)
    }

    @Test fun rule2_rostelecomOnSim2_banned() {
        val d = engine.evaluate(call(sim = 2, name = "Ростелеком"), ctx())
        assertEquals(Action.BAN, d.action)
        assertEquals("Маркированные на SIM2", d.ruleName)
    }

    @Test fun rule2_otherNameOrNoName_allowed() {
        assertEquals(Action.ALLOW, engine.evaluate(call(sim = 2, name = "Сбербанк"), ctx()).action)
        assertEquals(Action.ALLOW, engine.evaluate(call(sim = 2, name = null), ctx()).action)
    }

    @Test fun rule2_rostelecomOnSim1_allowed() {
        assertEquals(Action.ALLOW, engine.evaluate(call(sim = 1, name = "Ростелеком"), ctx()).action)
    }

    // --- списки и порядок ---

    @Test fun blocklistRejectsOnAnySim() {
        val d = engine.evaluate(call(sim = 1), ctx(block = listOf("+79123456789")))
        assertEquals(Action.REJECT, d.action)
        assertEquals(Decision.Source.BLOCKLIST, d.source)
    }

    @Test fun blocklistMask() {
        assertEquals(Action.REJECT, engine.evaluate(call(number = "84951112233"), ctx(block = listOf("+7495*"))).action)
    }

    @Test fun whitelistBeatsEverything() {
        val d = engine.evaluate(call(sim = 2, name = "Ростелеком"), ctx(white = listOf("8912*"), block = listOf("+79123456789")))
        assertEquals(Action.ALLOW, d.action)
        assertEquals(Decision.Source.WHITELIST, d.source)
    }

    @Test fun banOnHiddenNumberDegradesToReject() {
        val rules = listOf(Rule(id = 1, name = "все в бан", mode = RuleMode.ACTIVE, action = Action.BAN))
        assertEquals(Action.REJECT, engine.evaluate(call(number = null), ctx(rules)).action)
    }

    @Test fun priorityOrder() {
        val rules = listOf(
            Rule(id = 1, name = "поздний", priority = 50, mode = RuleMode.ACTIVE, action = Action.REJECT),
            Rule(id = 2, name = "ранний", priority = 5, mode = RuleMode.ACTIVE, action = Action.ALLOW),
        )
        assertEquals("ранний", engine.evaluate(call(), ctx(rules)).ruleName)
    }

    @Test fun disabledRuleSkipped() {
        val rules = active.map { it.copy(enabled = false) }
        assertEquals(Action.ALLOW, engine.evaluate(call(number = null, sim = 1), ctx(rules)).action)
    }

    // --- dry-run ---

    @Test fun dryRunDoesNotRejectButIsReported() {
        val d = engine.evaluate(call(number = null, sim = 1), ctx(Rules.defaults.mapIndexed { i, r -> r.copy(id = i + 1L) }))
        assertEquals(Action.ALLOW, d.action)
        assertEquals(listOf("Скрытые на SIM1"), d.dryRunHits.map { it.name })
        assertEquals(Action.REJECT, d.wouldAction)
    }

    @Test fun dryRunThenActiveRuleBelow() {
        val rules = listOf(
            Rule(id = 1, name = "проба", priority = 1, mode = RuleMode.DRY_RUN, action = Action.BAN),
            Rule(id = 2, name = "боевое", priority = 2, mode = RuleMode.ACTIVE, action = Action.REJECT),
        )
        val d = engine.evaluate(call(), ctx(rules))
        assertEquals(Action.REJECT, d.action)
        assertEquals("боевое", d.ruleName)
        assertEquals(listOf("проба"), d.dryRunHits.map { it.name })
    }

    // --- выключатель ---

    @Test fun protectionOffAllowsButRemembersRule() {
        val d = engine.evaluate(call(number = null, sim = 1), ctx(protection = false))
        assertEquals(Action.ALLOW, d.action)
        assertEquals(Decision.Source.DISABLED, d.source)
        assertEquals("Скрытые на SIM1", d.ruleName)
    }

    // --- остальные условия ---

    @Test fun repeatedCallException() {
        val rules = listOf(
            Rule(id = 1, name = "срочно", priority = 1, mode = RuleMode.ACTIVE,
                conditions = listOf(Condition.Repeated(count = 2, minutes = 3)), action = Action.ALLOW),
            Rule(id = 2, name = "всё сбросить", priority = 2, mode = RuleMode.ACTIVE, action = Action.REJECT),
        )
        assertEquals(Action.REJECT, engine.evaluate(call(), ctx(rules, recent = 0)).action)
        assertEquals(Action.ALLOW, engine.evaluate(call(), ctx(rules, recent = 1)).action)
    }

    @Test fun timeWindowSimple() {
        val c = Condition.TimeWindow(days = setOf(1, 2, 3, 4, 5), fromMinute = 9 * 60, toMinute = 18 * 60)
        val e = EvalContext(rules = emptyList(), zone = zone)
        assertTrue(engine.matches(c, call(at = LocalDateTime.of(2026, 11, 25, 9, 0)), e))   // ср 09:00
        assertFalse(engine.matches(c, call(at = LocalDateTime.of(2026, 11, 25, 18, 0)), e)) // ср 18:00
        assertFalse(engine.matches(c, call(at = LocalDateTime.of(2026, 11, 28, 12, 0)), e)) // сб
    }

    @Test fun timeWindowOverMidnight() {
        val c = Condition.TimeWindow(days = setOf(5), fromMinute = 22 * 60, toMinute = 7 * 60) // ночь с пятницы
        val e = EvalContext(rules = emptyList(), zone = zone)
        assertTrue(engine.matches(c, call(at = LocalDateTime.of(2026, 11, 27, 23, 0)), e))  // пт 23:00
        assertTrue(engine.matches(c, call(at = LocalDateTime.of(2026, 11, 28, 6, 59)), e))  // сб 06:59
        assertFalse(engine.matches(c, call(at = LocalDateTime.of(2026, 11, 28, 23, 0)), e)) // сб 23:00
        assertFalse(engine.matches(c, call(at = LocalDateTime.of(2026, 11, 27, 6, 0)), e))  // пт 06:00
    }

    @Test fun numberMaskCondition() {
        val c = Condition.NumberMask("8800*")
        val e = EvalContext(rules = emptyList())
        assertTrue(engine.matches(c, call(number = "+78001234567"), e))
        assertFalse(engine.matches(c, call(number = null), e))
    }

    // --- сериализация ---

    @Test fun conditionsJsonRoundTrip() {
        val all = listOf(
            Condition.SimSlot(2), Condition.NumberHidden, Condition.NumberPresented,
            Condition.CallerName("*ростелеком*"), Condition.NumberMask("+7495*"),
            Condition.TimeWindow(setOf(6, 7), 60, 120), Condition.Repeated(3, 5),
        )
        assertEquals(all, Rules.conditionsFromJson(Rules.conditionsToJson(all)))
    }

    @Test fun rulesJsonRoundTrip() {
        assertEquals(active, Rules.rulesFromJson(Rules.rulesToJson(active)))
    }
}
