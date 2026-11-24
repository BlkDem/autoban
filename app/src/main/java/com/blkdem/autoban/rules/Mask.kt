package com.blkdem.autoban.rules

import java.util.Locale

/**
 * Маска для имён и номеров.
 *
 * - `*` — любые символы (в т.ч. ни одного), `?` — ровно один символ; маска должна совпасть целиком.
 * - Регистр не важен, `ё` = `е`.
 * - Префикс `re:` — регулярное выражение (ищется вхождение), например `re:ростел[её]к`.
 */
class Mask(val pattern: String) {

    private val regex: Regex? = build(pattern)

    val isValid: Boolean get() = regex != null

    fun matches(text: String?): Boolean {
        if (text == null || regex == null) return false
        val folded = fold(text)
        return if (isRegex) regex.containsMatchIn(folded) else regex.matches(folded)
    }

    private val isRegex get() = pattern.startsWith(REGEX_PREFIX)

    companion object {
        const val REGEX_PREFIX = "re:"

        fun fold(s: String): String = s.trim().lowercase(Locale.ROOT).replace('ё', 'е')

        private fun build(pattern: String): Regex? = try {
            if (pattern.startsWith(REGEX_PREFIX)) {
                Regex(fold(pattern.removePrefix(REGEX_PREFIX)))
            } else {
                val sb = StringBuilder()
                for (ch in fold(pattern)) when (ch) {
                    '*' -> sb.append(".*")
                    '?' -> sb.append('.')
                    else -> sb.append(Regex.escape(ch.toString()))
                }
                Regex(sb.toString(), RegexOption.DOT_MATCHES_ALL)
            }
        } catch (_: Exception) {
            null
        }

        /** Маска для номера: убираем пробелы, скобки и дефисы, чтобы `+7 (495) *` = `+7495*`. */
        fun forNumber(pattern: String) = Mask(
            if (pattern.startsWith(REGEX_PREFIX)) pattern
            else pattern.filterNot { it == ' ' || it == '-' || it == '(' || it == ')' }
        )

        fun numberMatches(mask: Mask, normalizedNumber: String?): Boolean =
            normalizedNumber != null && PhoneNumbers.forms(normalizedNumber).any(mask::matches)
    }
}
