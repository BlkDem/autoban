package com.blkdem.autoban.rules

/**
 * Разбор списка номеров из текстового файла / CSV.
 *
 * Одна запись на строку: номер или маска, дальше через `;` `,` или табуляцию — необязательная заметка.
 * Пустые строки, комментарии (`#`, `//`) и заголовки таблиц пропускаются.
 * Строка, начинающаяся с `re:`, целиком считается регуляркой.
 */
object ListImport {

    data class Item(val pattern: String, val note: String?)

    data class Result(val items: List<Item>, val skipped: Int)

    private const val SEPARATORS = ";,\t"

    /** Поля CSV-строки: разделители `;` `,` табуляция, внутри "кавычек" разделители не действуют, "" — кавычка. */
    private fun splitFields(line: String): List<String> {
        val out = mutableListOf<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' && quoted && line.getOrNull(i + 1) == '"' -> { cur.append('"'); i++ }
                c == '"' -> quoted = !quoted
                c in SEPARATORS && !quoted -> { out += cur.toString().trim(); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString().trim()
        return out
    }
    private val phoneChars = Regex("[+0-9 ()\\-.]+")
    private val numberMask = Regex("\\+?[0-9*?]*[*?][0-9*?]*")

    /** UTF-8, а если файл в нём не читается — Windows-1251 (так сохраняет CSV русский Excel). */
    fun decode(bytes: ByteArray): String {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
        return runCatching { decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString() }
            .getOrElse { String(bytes, charset("windows-1251")) }
    }

    fun parse(text: String): Result {
        val items = LinkedHashMap<String, Item>()
        var skipped = 0
        for (rawLine in text.removePrefix("﻿").lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("//")) continue
            val item = parseLine(line)
            if (item == null) skipped++ else items.putIfAbsent(item.pattern, item)
        }
        return Result(items.values.toList(), skipped)
    }

    /** Одна строка → запись или null, если номер не распознан. */
    fun parseLine(line: String): Item? {
        if (line.startsWith(Mask.REGEX_PREFIX)) return canonical(line)?.let { Item(it, null) }
        val fields = splitFields(line)
        val first = fields.first()
        val note = fields.drop(1).firstOrNull { it.isNotEmpty() }?.take(200)
        val pattern = canonical(first) ?: return null
        return Item(pattern, note)
    }

    /**
     * Единый вид записи списка — чтобы одно и то же не хранилось дважды:
     * номер → `+79123456789`, маска → без пробелов/скобок/дефисов/точек (`+7 (495) *` → `+7495*`),
     * `re:` — как есть. null — не номер и не маска.
     */
    fun canonical(raw: String): String? {
        val s = raw.trim()
        return when {
            s.startsWith(Mask.REGEX_PREFIX) -> s.takeIf { Mask.forNumber(it).isValid }
            s.contains('*') || s.contains('?') ->
                s.filterNot { it == ' ' || it == '-' || it == '(' || it == ')' || it == '.' }.takeIf { it.matches(numberMask) }
            s.matches(phoneChars) && s.count { it.isDigit() } >= 3 -> PhoneNumbers.normalize(s)
            else -> null
        }
    }
}
