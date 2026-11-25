package com.blkdem.autoban.rules

import java.util.Locale

/**
 * Маска для имён и номеров.
 *
 * - `*` — любые символы (в т.ч. ни одного), `?` — ровно один символ; маска должна совпасть целиком.
 * - Регистр не важен, `ё` = `е`.
 * - Префикс `re:` — регулярное выражение (ищется вхождение), например `re:ростел[её]к`.
 * - Обычная маска сравнивается ещё и с учётом транслитерации: `*ростелеком*` совпадает
 *   с `PAO Rostelekom: …` и `Rostelecom` (маркировка МТС пишется латиницей). Для `re:` — нет.
 */
class Mask(val pattern: String) {

    private val regex: Regex? = build(pattern)
    private val translitRegex: Regex? = if (pattern.startsWith(REGEX_PREFIX)) null else glob(skeleton(pattern))

    val isValid: Boolean get() = regex != null

    fun matches(text: String?): Boolean {
        if (text == null || regex == null) return false
        val folded = fold(text)
        if (isRegex) return regex.containsMatchIn(folded)
        return regex.matches(folded) || translitRegex?.matches(skeleton(text)) == true
    }

    private val isRegex get() = pattern.startsWith(REGEX_PREFIX)

    companion object {
        const val REGEX_PREFIX = "re:"

        fun fold(s: String): String = s.trim().lowercase(Locale.ROOT).replace('ё', 'е')

        private fun build(pattern: String): Regex? = try {
            if (pattern.startsWith(REGEX_PREFIX)) Regex(fold(pattern.removePrefix(REGEX_PREFIX))) else glob(fold(pattern))
        } catch (_: Exception) {
            null
        }

        private fun glob(folded: String): Regex? = try {
            val sb = StringBuilder()
            for (ch in folded) when (ch) {
                '*' -> sb.append(".*")
                '?' -> sb.append('.')
                else -> sb.append(Regex.escape(ch.toString()))
            }
            Regex(sb.toString(), RegexOption.DOT_MATCHES_ALL)
        } catch (_: Exception) {
            null
        }

        private val CYR = mapOf(
            'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ж' to "zh", 'з' to "z",
            'и' to "i", 'й' to "y", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p",
            'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "kh", 'ц' to "ts", 'ч' to "ch",
            'ш' to "sh", 'щ' to "sh", 'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
        )

        /**
         * Грубый «скелет» строки для сравнения кириллицы с латиницей: транслитерация и сведение
         * похожих написаний (c/k, kh/h, j/y, w/v, ph/f, x/ks). Маски `*` и `?` сохраняются.
         */
        fun skeleton(s: String): String {
            val sb = StringBuilder()
            for (ch in fold(s)) sb.append(CYR[ch] ?: ch.toString())
            return sb.toString()
                .replace("shch", "sh").replace("sch", "sh")
                .replace("ch", "\u0001").replace("ck", "k").replace('c', 'k').replace("\u0001", "ch")
                .replace("kh", "h").replace("ph", "f").replace('w', 'v').replace('q', 'k').replace("x", "ks")
                .replace('j', 'y').replace("iy", "i").replace("yi", "i")
                .replace("'", "").replace("`", "")
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
