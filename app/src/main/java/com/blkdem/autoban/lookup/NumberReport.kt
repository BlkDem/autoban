package com.blkdem.autoban.lookup

import kotlinx.serialization.Serializable
import java.util.Locale

/** Что известно о номере из открытых отзывов. Чистый Kotlin — покрыт тестами. */
@Serializable
data class NumberReport(
    val number: String,
    val source: String = ZvoniliParser.SOURCE,
    /** false — сайт ничего не знает о номере. */
    val found: Boolean,
    /** Средняя оценка 1…5; null — оценок нет. */
    val rating: Double? = null,
    val reviews: Int = 0,
    val views: Int? = null,
    /** Типы номера по отзывам: «Мошенники», «Реклама»… */
    val categories: List<String> = emptyList(),
    val operator: String? = null,
    val region: String? = null,
    /** Последние отзывы, новые первыми. */
    val comments: List<Comment> = emptyList(),
    val url: String? = null,
    val fetchedAt: Long = 0,
) {
    @Serializable
    data class Comment(val date: String? = null, val type: String? = null, val text: String)

    enum class Verdict { BAD, NEUTRAL, GOOD, UNKNOWN }

    val verdict: Verdict get() = when {
        !found || reviews == 0 || rating == null -> Verdict.UNKNOWN
        rating <= 2.5 -> Verdict.BAD
        rating >= 4.0 -> Verdict.GOOD
        else -> Verdict.NEUTRAL
    }

    val icon: String get() = when (verdict) {
        Verdict.BAD -> "⚠️"
        Verdict.NEUTRAL -> "❔"
        Verdict.GOOD -> "👍"
        Verdict.UNKNOWN -> "ℹ️"
    }

    /** «1,35/5 · 7631 отзыв · Мошенники, Реклама» / «отзывов нет». */
    fun summary(maxCategories: Int = 3): String = when {
        !found -> "номер не найден"
        reviews == 0 -> "отзывов нет"
        else -> buildList {
            rating?.let { add(fmtRating(it) + "/5") }
            add("$reviews " + plural(reviews, "отзыв", "отзыва", "отзывов"))
            if (categories.isNotEmpty()) add(categories.take(maxCategories).joinToString(", "))
        }.joinToString(" · ")
    }

    /** «ПАО "МТС" · г. Москва». */
    fun where(): String? = listOfNotNull(operator, region).joinToString(" · ").ifBlank { null }

    companion object {
        fun fmtRating(r: Double): String =
            if (r == Math.floor(r)) r.toInt().toString() else String.format(Locale.US, "%.2f", r).trimEnd('0').trimEnd('.')

        fun plural(n: Int, one: String, few: String, many: String): String {
            val m100 = n % 100
            val m10 = n % 10
            return when {
                m100 in 11..14 -> many
                m10 == 1 -> one
                m10 in 2..4 -> few
                else -> many
            }
        }
    }
}
