package com.blkdem.autoban.data

import com.blkdem.autoban.rules.ListEntry
import com.blkdem.autoban.rules.ListType
import com.blkdem.autoban.rules.Rule
import com.blkdem.autoban.rules.Rules
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Резервная копия правил и списков (JSON). */
@Serializable
data class Backup(
    val version: Int = 1,
    val rules: List<Rule> = emptyList(),
    val blocklist: List<Item> = emptyList(),
    val whitelist: List<Item> = emptyList(),
) {
    @Serializable
    data class Item(val pattern: String, val note: String? = null, val ruleName: String? = null, val createdAt: Long = 0)

    companion object {
        private val json = Json(Rules.json) { prettyPrint = true }

        fun of(rules: List<Rule>, block: List<ListEntry>, white: List<ListEntry>) = Backup(
            rules = rules.map { it.copy(id = 0) },
            blocklist = block.map { Item(it.pattern, it.note, it.ruleName, it.createdAt) },
            whitelist = white.map { Item(it.pattern, it.note, it.ruleName, it.createdAt) },
        )

        fun encode(b: Backup): String = json.encodeToString(serializer(), b)
        fun decode(s: String): Backup = json.decodeFromString(serializer(), s)
            .let { b -> b.copy(rules = b.rules.map(com.blkdem.autoban.rules.Rules::normalize)) }
    }

    fun entries(type: ListType) = if (type == ListType.BLOCK) blocklist else whitelist
}

/** Экспорт журнала в CSV (UTF-8 с BOM — чтобы Excel правильно открыл кириллицу). */
object CsvExport {
    private val header = listOf("time", "sim", "number", "raw_number", "presentation", "caller_name", "action", "source", "rule", "dry_run", "would_action", "telegram", "rating", "reviews", "number_type", "operator", "region")

    fun journal(events: List<CallEvent>, tz: TimeZone = TimeZone.getDefault()): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = tz }
        return buildString {
            append('﻿')
            append(header.joinToString(";")).append("\r\n")
            for (e in events) {
                listOf(
                    fmt.format(Date(e.timestamp)), e.simSlot?.toString(), e.number, e.rawNumber, e.presentation.name,
                    e.callerName, e.action.name, e.source.name, e.ruleName, e.dryRun, e.wouldAction.name, e.tgStatus.name,
                    e.report?.rating?.toString(), e.report?.reviews?.toString(), e.report?.categories?.joinToString(", "),
                    e.report?.operator, e.report?.region,
                ).joinTo(this, ";") { cell(it) }
                append("\r\n")
            }
        }
    }

    private fun cell(v: String?): String {
        if (v == null) return ""
        return if (v.any { it == ';' || it == '"' || it == '\n' || it == '\r' }) "\"" + v.replace("\"", "\"\"") + "\"" else v
    }
}
