package com.blkdem.autoban.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ParseHmTest {
    @Test fun parse() {
        assertEquals(9 * 60 + 30, parseHm("9:30"))
        assertEquals(9 * 60 + 30, parseHm("09:30"))
        assertEquals(0, parseHm("00:00"))
        assertEquals(24 * 60, parseHm("24:00"))
        assertNull(parseHm("24:01"))
        assertNull(parseHm("12:60"))
        assertNull(parseHm("1230"))
        assertNull(parseHm(""))
    }
}
