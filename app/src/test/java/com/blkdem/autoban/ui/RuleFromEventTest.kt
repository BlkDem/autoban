package com.blkdem.autoban.ui

import com.blkdem.autoban.data.CallEvent
import com.blkdem.autoban.rules.Action
import com.blkdem.autoban.rules.CallInfo
import com.blkdem.autoban.rules.Condition
import com.blkdem.autoban.rules.Decision
import com.blkdem.autoban.rules.RuleMode
import org.junit.Assert.assertEquals
import org.junit.Test

class RuleFromEventTest {
    private fun ev(number: String?, name: String?, sim: Int?) = CallEvent(
        timestamp = 0, rawNumber = number, number = number,
        presentation = if (number == null) CallInfo.Presentation.HIDDEN else CallInfo.Presentation.PRESENTED,
        callerName = name, simSlot = sim, simLabel = null, action = Action.ALLOW, source = Decision.Source.DEFAULT,
        ruleId = null, ruleName = null, dryRun = null, wouldAction = Action.ALLOW,
    )

    @Test fun hidden() {
        val r = ruleFromEvent(ev(null, null, 1))
        assertEquals(listOf(Condition.SimSlot(1), Condition.NumberHidden), r.conditions)
        assertEquals(RuleMode.DRY_RUN, r.mode)
    }

    @Test fun byName() {
        assertEquals(listOf(Condition.CallerName("*Ростелеком*")), ruleFromEvent(ev("+79123456789", "Ростелеком", 2)).conditions)
        // Маркировка: правило по организации, на любой SIM.
        assertEquals(
            listOf(Condition.Marking(organization = "*Ростелеком*")),
            ruleFromEvent(ev("+79309131503", "Ростелеком: связь", 1)).conditions,
        )
    }

    @Test fun byNumberWithoutSim() {
        assertEquals(listOf(Condition.NumberMask("+79123456789")), ruleFromEvent(ev("+79123456789", null, null)).conditions)
        assertEquals(listOf(Condition.NumberMask("+79123456789")), ruleFromEvent(ev("+79123456789", null, 2)).conditions)
    }
}
