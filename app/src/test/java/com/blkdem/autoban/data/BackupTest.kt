package com.blkdem.autoban.data

import com.blkdem.autoban.rules.Action
import com.blkdem.autoban.rules.CallInfo
import com.blkdem.autoban.rules.Decision
import com.blkdem.autoban.rules.ListEntry
import com.blkdem.autoban.rules.ListType
import com.blkdem.autoban.rules.Rules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.TimeZone

class BackupTest {

    @Test fun backupRoundTrip() {
        val b = Backup.of(
            Rules.defaults,
            listOf(ListEntry(id = 5, type = ListType.BLOCK, pattern = "+79123456789", note = "Ростелеком", ruleName = "r", createdAt = 1)),
            listOf(ListEntry(type = ListType.WHITE, pattern = "+7495*")),
        )
        val back = Backup.decode(Backup.encode(b))
        assertEquals(b, back)
        assertEquals("+79123456789", back.entries(ListType.BLOCK).single().pattern)
    }

    @Test fun ignoresUnknownFields() {
        val b = Backup.decode("""{"version":1,"future":"x","rules":[],"blocklist":[{"pattern":"8800*","extra":1}]}""")
        assertEquals("8800*", b.blocklist.single().pattern)
    }

    @Test fun csv() {
        val e = CallEvent(
            id = 1, timestamp = 0, rawNumber = "89123456789", number = "+79123456789",
            presentation = CallInfo.Presentation.PRESENTED, callerName = "ООО \"Рога; и копыта\"", simSlot = 2, simLabel = null,
            action = Action.BAN, source = Decision.Source.RULE, ruleId = 1, ruleName = "r", dryRun = null, wouldAction = Action.BAN,
        )
        val csv = CsvExport.journal(listOf(e), TimeZone.getTimeZone("UTC"))
        val lines = csv.removePrefix("﻿").split("\r\n")
        assertTrue(lines[0].startsWith("time;sim;number"))
        assertEquals("1970-01-01 00:00:00;2;+79123456789;89123456789;PRESENTED;\"ООО \"\"Рога; и копыта\"\"\";BAN;RULE;r;;BAN;NONE", lines[1])
    }
}
