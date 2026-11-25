package com.blkdem.autoban.rules

/** Нормализация номеров (ориентирована на РФ, остальные номера оставляет в международном виде). */
object PhoneNumbers {

    /** `8 (912) 345-67-89` → `+79123456789`; `900` → `900`; пусто → null. */
    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val digits = raw.filter { it.isDigit() }
        if (digits.isEmpty()) return null
        if (raw.trim().startsWith("+")) return "+$digits"
        return when {
            digits.length == 11 && (digits[0] == '8' || digits[0] == '7') -> "+7" + digits.substring(1)
            digits.length == 10 && digits[0] == '9' -> "+7$digits"
            else -> digits // короткие и служебные номера — как есть
        }
    }

    /** Варианты записи номера, по которым сопоставляются маски: `+79123456789`, `79123456789`, `89123456789`. */
    fun forms(normalized: String): List<String> {
        val out = linkedSetOf(normalized, normalized.removePrefix("+"))
        if (normalized.startsWith("+7") && normalized.length == 12) out += "8" + normalized.substring(2)
        return out.toList()
    }

    /** Ссылка на поиск отзывов о номере. Российские номера ищем в виде `89123456789` — так их пишут сайты «кто звонил». */
    fun lookupUrl(normalized: String): String {
        val n = if (normalized.startsWith("+7") && normalized.length == 12) "8" + normalized.substring(2) else normalized.removePrefix("+")
        return "https://yandex.ru/search/?text=" + java.net.URLEncoder.encode("$n кто звонил", "UTF-8")
    }

    /** `+79123456789` → `+7 912 345-67-89`. */
    fun pretty(normalized: String?): String {
        if (normalized == null) return "скрыт"
        if (normalized.startsWith("+7") && normalized.length == 12) {
            val d = normalized.substring(2)
            return "+7 ${d.substring(0, 3)} ${d.substring(3, 6)}-${d.substring(6, 8)}-${d.substring(8)}"
        }
        return normalized
    }
}
