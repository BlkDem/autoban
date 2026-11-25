package com.blkdem.autoban.rules

import com.blkdem.autoban.lookup.NumberReport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReputationTest {

    private val engine = RuleEngine()

    private fun report(rating: Double? = 1.4, reviews: Int = 10, vararg types: String) =
        NumberReport("+79123456789", found = true, rating = rating, reviews = reviews, categories = types.toList())

    private fun call(r: NumberReport?) = CallInfo(
        timestamp = 0, rawNumber = "+79123456789", presentation = CallInfo.Presentation.PRESENTED, simSlot = 1, report = r,
    )

    private fun matches(c: Condition.Reputation, r: NumberReport?) = engine.matches(c, call(r), EvalContext(emptyList()))

    @Test fun ratingAndReviews() {
        val c = Condition.Reputation(minReviews = 3, maxRating = 2.0)
        assertTrue(matches(c, report(1.4, 10)))
        assertTrue(matches(c, report(2.0, 3)))
        assertFalse(matches(c, report(2.1, 10)))
        assertFalse(matches(c, report(1.0, 2)))
        assertFalse(matches(c, report(null, 0)))
    }

    @Test fun noReportNoMatch() {
        val c = Condition.Reputation(minReviews = 1, maxRating = 0.0)
        assertFalse(matches(c, null))
        assertFalse(matches(c, NumberReport("+79123456789", found = false)))
    }

    @Test fun categoryMask() {
        val c = Condition.Reputation(minReviews = 1, maxRating = 0.0, category = "*мошен*")
        assertTrue(matches(c, report(3.5, 5, "Реклама", "Мошенники")))
        assertFalse(matches(c, report(1.0, 5, "Реклама", "Опросы")))
        // Оценка не учитывается при maxRating = 0.
        assertTrue(matches(c, report(null, 5, "МОШЕННИКИ")))
    }

    @Test fun ruleUsesReport() {
        val rule = Rules.reputationDefault.copy(id = 7, mode = RuleMode.ACTIVE)
        val ctx = EvalContext(listOf(rule))
        assertEquals(Action.REJECT, engine.evaluate(call(report(1.2, 50, "Мошенники")), ctx).action)
        assertEquals(Action.ALLOW, engine.evaluate(call(report(4.5, 50)), ctx).action)
        assertEquals(Action.ALLOW, engine.evaluate(call(null), ctx).action)
    }

    @Test fun needsReport() {
        val dry = Rules.reputationDefault
        assertTrue(engine.needsReport(listOf(dry), activeOnly = false))
        assertFalse(engine.needsReport(listOf(dry), activeOnly = true))
        assertTrue(engine.needsReport(listOf(dry.copy(mode = RuleMode.ACTIVE)), activeOnly = true))
        assertFalse(engine.needsReport(listOf(dry.copy(mode = RuleMode.ACTIVE, enabled = false)), activeOnly = true))
        assertFalse(engine.needsReport(Rules.defaults.filter { it !== dry && it.name != dry.name }, activeOnly = false))
    }

    @Test fun serializesAndDescribes() {
        val c = Condition.Reputation(minReviews = 5, maxRating = 2.5, category = "*коллект*")
        val back = Rules.conditionsFromJson(Rules.conditionsToJson(listOf(c)))
        assertEquals(listOf(c), back)
        assertEquals("репутация: отзывов ≥ 5, оценка ≤ 2.5, тип ~ «*коллект*»", Rules.describe(c))
    }
}
