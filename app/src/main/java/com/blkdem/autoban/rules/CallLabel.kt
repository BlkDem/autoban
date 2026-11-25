package com.blkdem.autoban.rules

/**
 * Подпись звонка от оператора — маркировка по 41-ФЗ: «Организация: категория».
 * Билайн пишет её кириллицей («Ростелеком: связь»), МТС — латиницей («PAO MTS: svyaz i internet»).
 * Чистый Kotlin — покрыт тестами.
 */
object CallLabel {

    data class Marking(val organization: String, val category: String)

    /** «Ростелеком: связь» → (Ростелеком, связь). Без двоеточия или с пустой частью — null. */
    fun parse(label: String?): Marking? {
        val t = label?.trim() ?: return null
        if ("//" in t) return null // content://…, https://…
        val i = t.indexOf(':')
        if (i <= 0 || i == t.lastIndex) return null
        val org = t.substring(0, i).trim()
        val cat = t.substring(i + 1).trim()
        if (letters(org) < 2 || letters(cat) < 2) return null // отсекает время «0:05» и т.п.
        if (org.lowercase().startsWith("sim")) return null
        return Marking(org, cat)
    }

    private val UNMARKED = listOf("без маркировки", "bez markirovki")

    /** Оператор подписал звонок компании без договора маркировки. */
    fun isUnmarked(label: String?): Boolean =
        label != null && Mask.fold(label).let { l -> UNMARKED.any { it in l } }

    /** Подпись оператора: маркировка или «звонок без маркировки». */
    fun isOperatorLabel(text: String?): Boolean = parse(text) != null || isUnmarked(text)

    /**
     * Найти подпись оператора среди текстов уведомления звонилки о входящем.
     * Берём только то, что похоже на маркировку: прочие строки (номер, «Входящий вызов», регион) пропускаем.
     */
    fun fromTexts(texts: List<String?>): String? {
        val list = texts.mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }.distinct()
        return list.firstOrNull { parse(it) != null && !looksLikeNumber(it) } ?: list.firstOrNull { isUnmarked(it) }
    }

    fun looksLikeNumber(s: String): Boolean = s.count { it.isDigit() } >= 5 && letters(s) == 0

    private fun letters(s: String) = s.count { it.isLetter() }
}
