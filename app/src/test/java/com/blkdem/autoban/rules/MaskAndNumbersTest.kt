package com.blkdem.autoban.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MaskAndNumbersTest {

    @Test fun normalize() {
        assertEquals("+79123456789", PhoneNumbers.normalize("8 (912) 345-67-89"))
        assertEquals("+79123456789", PhoneNumbers.normalize("+7 912 345 67 89"))
        assertEquals("+79123456789", PhoneNumbers.normalize("79123456789"))
        assertEquals("+79123456789", PhoneNumbers.normalize("9123456789"))
        assertEquals("+78001234567", PhoneNumbers.normalize("88001234567"))
        assertEquals("+380441234567", PhoneNumbers.normalize("+380441234567"))
        assertEquals("900", PhoneNumbers.normalize("900"))
        assertNull(PhoneNumbers.normalize(""))
        assertNull(PhoneNumbers.normalize("Unknown"))
    }

    @Test fun pretty() {
        assertEquals("+7 912 345-67-89", PhoneNumbers.pretty("+79123456789"))
        assertEquals("скрыт", PhoneNumbers.pretty(null))
        assertEquals("900", PhoneNumbers.pretty("900"))
    }

    @Test fun globNames() {
        val m = Mask("*ростелеком*")
        assertTrue(m.matches("Ростелеком"))
        assertTrue(m.matches("ПАО РОСТЕЛЕКОМ — Москва"))
        assertFalse(m.matches("МТС"))
        assertFalse(m.matches(null))
        assertTrue(Mask("*").matches("что угодно"))
        assertTrue(Mask("Сбер?анк").matches("сбербанк"))
        assertFalse(Mask("Сбер").matches("Сбербанк")) // маска совпадает целиком
    }

    @Test fun yoEqualsE() {
        assertTrue(Mask("*ёлка*").matches("ЕЛКА"))
        assertTrue(Mask("*елка*").matches("Ёлка"))
    }

    @Test fun regexMasks() {
        assertTrue(Mask("re:ростел[её]к").matches("ООО Ростелеком"))
        assertFalse(Mask("re:^мтс$").matches("мтс банк"))
        assertFalse(Mask("re:(").isValid)
        assertFalse(Mask("re:(").matches("("))
    }

    @Test fun numberMasks() {
        fun m(mask: String, raw: String) = Mask.numberMatches(Mask.forNumber(mask), PhoneNumbers.normalize(raw))
        assertTrue(m("+7495*", "84951234567"))
        assertTrue(m("8495*", "+74951234567"))
        assertTrue(m("+7 (495) *", "84951234567"))
        assertTrue(m("8800*", "88005553535"))
        assertTrue(m("+7912???????", "89121234567"))
        assertFalse(m("+7912???????", "891212345678"))
        assertFalse(m("+7495*", "84991234567"))
        assertTrue(m("+79123456789", "8 912 345 67 89"))
        assertFalse(Mask.numberMatches(Mask.forNumber("*"), null))
    }
}
