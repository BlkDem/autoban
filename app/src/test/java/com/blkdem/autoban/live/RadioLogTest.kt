package com.blkdem.autoban.live

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RadioLogTest {

    private fun line(name: String) =
        "         1795600214.038  1366  1366 V Telephony: GsmConnection: updateAddress, caller display name : $name, present : 1"

    @Test fun samsungLine() {
        val log = sequenceOf(
            "--------- beginning of main",
            line("ООО ВЕКТОР: прочие услуги"),
            "         1795600214.038  1366  1366 V Telephony: GsmConnection: updateAddress, caller display name changed - name=ООО ВЕКТОР: прочие услуги, present=1",
        )
        assertEquals("ООО ВЕКТОР: прочие услуги", RadioLog.parse(log))
    }

    @Test fun lastLabelWins() {
        assertEquals("Ростелеком: связь", RadioLog.parse(sequenceOf(line("PAO MTS: svyaz i internet"), line("Ростелеком: связь"))))
    }

    @Test fun unmarked() {
        assertEquals("Звонок без маркировки", RadioLog.parse(sequenceOf(line("Звонок без маркировки"))))
    }

    @Test fun notALabel() {
        assertNull(RadioLog.parse(sequenceOf(line("null"), line(""), line("+79123456789"), "random line")))
    }
}
