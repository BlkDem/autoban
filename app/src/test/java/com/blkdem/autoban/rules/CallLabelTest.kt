package com.blkdem.autoban.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallLabelTest {

    @Test fun parseMarking() {
        assertEquals(CallLabel.Marking("Ростелеком", "связь"), CallLabel.parse("Ростелеком: связь"))
        assertEquals(CallLabel.Marking("PAO MTS", "svyaz i internet"), CallLabel.parse(" PAO MTS: svyaz i internet "))
        assertEquals(CallLabel.Marking("IP Ivanov I I", "torgovlya"), CallLabel.parse("IP Ivanov I I: torgovlya"))
        assertNull(CallLabel.parse("Ростелеком"))
        assertNull(CallLabel.parse("0:05"))
        assertNull(CallLabel.parse("Ростелеком:"))
        assertNull(CallLabel.parse("SIM 1: Beeline"))
        assertNull(CallLabel.parse(null))
    }

    @Test fun unmarked() {
        assertTrue(CallLabel.isUnmarked("Звонок без маркировки"))
        assertTrue(CallLabel.isUnmarked("Zvonok bez markirovki"))
        assertFalse(CallLabel.isUnmarked("Ростелеком: связь"))
    }

    @Test fun fromNotificationTexts() {
        assertEquals("Ростелеком: связь", CallLabel.fromTexts(listOf("+7 930 913-15-03", "Ростелеком: связь", "Входящий вызов")))
        assertEquals("Звонок без маркировки", CallLabel.fromTexts(listOf("Звонок без маркировки", "+7 900 123-45-67")))
        assertNull(CallLabel.fromTexts(listOf("+7 930 913-15-03", "Входящий вызов", "Россия", null, "")))
        assertNull(CallLabel.fromTexts(listOf("Входящий вызов 0:05")))
    }

    @Test fun translitMasks() {
        assertTrue(Mask("*ростелеком*").matches("PAO Rostelekom: svyaz"))
        assertTrue(Mask("*ростелеком*").matches("Rostelecom"))
        assertTrue(Mask("*rostelecom*").matches("Ростелеком: связь"))
        assertTrue(Mask("*мтс*").matches("PAO MTS: svyaz i internet"))
        assertTrue(Mask("*сбер*").matches("PAO Sberbank: bank"))
        assertTrue(Mask("*яндекс*").matches("OOO Yandex: IT"))
        assertTrue(Mask("*связ*").matches("svyaz"))
        assertFalse(Mask("*ростелеком*").matches("PAO MTS: svyaz"))
        // Регулярки транслитерацию не используют.
        assertFalse(Mask("re:ростелеком").matches("Rostelekom"))
        // Номера не ломаются.
        assertTrue(Mask.numberMatches(Mask.forNumber("+7930*"), "+79309131503"))
    }

    private val engine = RuleEngine()
    private fun call(name: String?) = CallInfo(0, "+79309131503", CallInfo.Presentation.PRESENTED, callerName = name, simSlot = 1)
    private fun m(c: Condition, name: String?) = engine.matches(c, call(name), EvalContext(emptyList()))

    @Test fun markingCondition() {
        assertTrue(m(Condition.Marking(), "Ростелеком: связь"))
        assertFalse(m(Condition.Marking(), "Ростелеком"))
        assertFalse(m(Condition.Marking(), null))
        assertTrue(m(Condition.Marking(organization = "*ростелеком*"), "PAO Rostelekom: svyaz"))
        assertTrue(m(Condition.Marking(category = "*связ*"), "Ростелеком: связь"))
        assertTrue(m(Condition.Marking(category = "*реклам*"), "OOO Romashka: reklama"))
        assertFalse(m(Condition.Marking(category = "*банк*"), "Ростелеком: связь"))
        assertTrue(m(Condition.Unmarked, "Звонок без маркировки"))
        assertFalse(m(Condition.Unmarked, "Ростелеком: связь"))
    }

    @Test fun conditionsSerializeAndDescribe() {
        val list = listOf(Condition.Marking("*ростелеком*", "*связ*"), Condition.Unmarked, Condition.Marking())
        assertEquals(list, Rules.conditionsFromJson(Rules.conditionsToJson(list)))
        assertEquals("маркировка: организация ~ «*ростелеком*», категория ~ «*связ*»", Rules.describe(list[0]))
        assertEquals("звонок без маркировки", Rules.describe(Condition.Unmarked))
        assertEquals("звонок маркирован", Rules.describe(Condition.Marking()))
    }

    @Test fun migrateRostelecomRule() {
        val old = Rule(
            id = 2, name = "Маркированные на SIM2", priority = 20, mode = RuleMode.ACTIVE, action = Action.BAN,
            conditions = listOf(Condition.SimSlot(2), Condition.NumberPresented, Condition.CallerName("*ростелеком*")),
        )
        val new = Rules.migrateRostelecom(old)!!
        assertEquals(Rules.ROSTELECOM_NAME, new.name)
        assertEquals(listOf(Condition.NumberPresented, Condition.CallerName("*ростелеком*")), new.conditions)
        assertEquals(RuleMode.ACTIVE, new.mode)
        // Переименованное пользователем правило сохраняет имя; чужие правила не трогаем.
        assertEquals("Моё", Rules.migrateRostelecom(old.copy(name = "Моё"))!!.name)
        assertNull(Rules.migrateRostelecom(new))
        assertNull(Rules.migrateRostelecom(old.copy(conditions = listOf(Condition.SimSlot(2), Condition.NumberHidden))))
    }
}
