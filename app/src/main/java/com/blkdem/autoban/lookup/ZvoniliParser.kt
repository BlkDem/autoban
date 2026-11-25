package com.blkdem.autoban.lookup

/**
 * Разбор страницы номера на zvonili.com. Чистый Kotlin — покрыт тестами на сохранённых страницах.
 * Нужное лежит в начале страницы, поэтому разбор терпит обрезанный HTML.
 */
object ZvoniliParser {

    const val SOURCE = "zvonili.com"

    /** Сайт знает только российские номера: `+79123456789` → `https://zvonili.com/phone/9123456789`. */
    fun url(number: String): String? =
        if (isRussian(number)) "https://zvonili.com/phone/" + number.substring(2) else null

    fun isRussian(number: String?): Boolean =
        number != null && number.length == 12 && number.startsWith("+7") && number.substring(2).all { it.isDigit() }

    fun parse(number: String, html: String, now: Long = System.currentTimeMillis()): NumberReport {
        val url = url(number)
        if (!html.contains("Рейтинг номера:")) return NumberReport(number, found = false, url = url, fetchedAt = now)

        val rating = badgeAfter(html, "Рейтинг номера:")?.substringBefore('/')?.replace(',', '.')?.toDoubleOrNull()
        val views = badgeAfter(html, "Просмотров:")?.toIntOrNull()
        val reviews = badgeAfter(html, "Отзывов:")?.toIntOrNull() ?: 0

        val categories = html.indexOf("Тип номера:").takeIf { it >= 0 }?.let { start ->
            val end = html.indexOf("</tr>", start).let { if (it < 0) minOf(html.length, start + 3000) else it }
            BADGE.findAll(html.substring(start, end)).map { text(it.groupValues[1]) }.filter { it.isNotEmpty() }.toList()
        }.orEmpty()

        val owner = OWNER.find(html)
        val operator = owner?.groupValues?.get(1)?.let(::text)?.takeIf { it.isNotEmpty() }
        val region = owner?.groupValues?.get(2)?.let(::text)?.takeIf { it.isNotEmpty() }

        val comments = html.split("itemprop=\"review\"").drop(1).mapNotNull { block ->
            val body = QUOTE.find(block)?.groupValues?.get(1)?.let(::text)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            NumberReport.Comment(
                date = DATE.find(block.substringBefore("<blockquote"))?.groupValues?.get(1),
                type = TYPE.find(block)?.groupValues?.get(1)?.let(::text)?.takeIf { it.isNotEmpty() },
                text = if (body.length > MAX_COMMENT) body.take(MAX_COMMENT - 1).trimEnd() + "…" else body,
            )
        }.take(MAX_COMMENTS)

        return NumberReport(
            number = number,
            found = true,
            rating = rating?.takeIf { it > 0 && reviews > 0 },
            reviews = reviews,
            views = views,
            categories = categories,
            operator = operator,
            region = region,
            comments = comments,
            url = url,
            fetchedAt = now,
        )
    }

    /** Текст первого `badge` после подписи в таблице: `<span class="badge …">1.35/5</span>`. */
    private fun badgeAfter(html: String, label: String): String? {
        val i = html.indexOf(label)
        if (i < 0) return null
        val window = html.substring(i, minOf(html.length, i + 400))
        // Подпись следующей строки таблицы не должна попасть в окно: строки «Отзывов» может не быть.
        val cut = window.indexOf("</tr>").let { if (it < 0) window else window.substring(0, it) }
        return BADGE.find(cut)?.groupValues?.get(1)?.let(::text)
    }

    /** Убрать теги, раскрыть сущности, схлопнуть пробелы. */
    fun text(s: String): String = s.replace(TAG, " ")
        .replace("&quot;", "\"").replace("&#039;", "'").replace("&#39;", "'").replace("&laquo;", "«").replace("&raquo;", "»")
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&")
        .replace(SPACES, " ").trim()

    private val TAG = Regex("<[^>]*>")
    private val SPACES = Regex("""\s+""")
    private val BADGE = Regex("""<span class="badge[^"]*">([^<]*)</span>""")
    private val OWNER = Regex("""принадлежит\s+оператору\s+([\s\S]{1,200}?)\s+в\s+регионе\s*<span[^>]*>([^<]*)</span>""")
    private val QUOTE = Regex("""<blockquote[^>]*>([\s\S]*?)</blockquote>""")
    private val TYPE = Regex("""<span class="type">([^<]*)</span>""")
    private val DATE = Regex("""(\d{2}\.\d{2}\.\d{4} \d{2}:\d{2})""")

    private const val MAX_COMMENTS = 3
    private const val MAX_COMMENT = 300
}
