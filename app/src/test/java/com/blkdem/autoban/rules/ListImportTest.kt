package com.blkdem.autoban.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListImportTest {

    @Test fun plainNumbersInAnyFormat() {
        val r = ListImport.parse("89123456789\n+7 (912) 345-67-88\n9123456787\n8.912.345.67.86\n")
        assertEquals(listOf("+79123456789", "+79123456788", "+79123456787", "+79123456786"), r.items.map { it.pattern })
        assertEquals(0, r.skipped)
    }

    @Test fun csvWithHeaderNotesQuotesAndBom() {
        val r = ListImport.parse("﻿Номер;Описание\n\"84951234567\";\"Ростелеком, опрос\"\n89990001122,кредит\n")
        assertEquals(2, r.items.size)
        assertEquals(ListImport.Item("+74951234567", "Ростелеком, опрос"), r.items[0]) // запятая в кавычках — часть текста
        assertEquals(ListImport.Item("+79990001122", "кредит"), r.items[1])
        assertEquals(1, r.skipped) // заголовок
    }

    @Test fun masksAndRegex() {
        val r = ListImport.parse("+7 495 *\n8800*\n+7912???????\nre:^\\+7958\n")
        assertEquals(listOf("+7495*", "8800*", "+7912???????", "re:^\\+7958"), r.items.map { it.pattern })
    }

    @Test fun commentsBlankLinesAndDuplicates() {
        val r = ListImport.parse("# мой список\n\n// ещё комментарий\n89123456789\n+79123456789\r\n8 912 345 67 89\n")
        assertEquals(1, r.items.size)
        assertEquals(0, r.skipped)
    }

    @Test fun garbageIsSkipped() {
        assertNull(ListImport.parseLine("позвонить маме"))
        assertNull(ListImport.parseLine("12"))
        assertNull(ListImport.parseLine("re:[unclosed"))
        assertTrue(ListImport.parse("abc\n89123456789").let { it.items.size == 1 && it.skipped == 1 })
    }

    @Test fun decodesUtf8AndCp1251() {
        val s = "89123456789;Ростелеком"
        assertEquals(s, ListImport.decode(s.toByteArray(Charsets.UTF_8)))
        assertEquals(s, ListImport.decode(s.toByteArray(charset("windows-1251"))))
    }

    @Test fun lookupUrl() {
        assertEquals("https://yandex.ru/search/?text=89123456789+%D0%BA%D1%82%D0%BE+%D0%B7%D0%B2%D0%BE%D0%BD%D0%B8%D0%BB", PhoneNumbers.lookupUrl("+79123456789"))
        assertTrue(PhoneNumbers.lookupUrl("+380441234567").contains("text=380441234567+"))
    }
}
