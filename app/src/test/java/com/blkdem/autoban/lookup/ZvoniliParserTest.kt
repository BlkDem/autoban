package com.blkdem.autoban.lookup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Страницы сохранены с zvonili.com 25.11.2026 (обрезаны, имена авторов заменены). */
class ZvoniliParserTest {

    private fun page(name: String) =
        javaClass.getResource("/zvonili/$name.html")!!.readText(Charsets.UTF_8)

    @Test fun url() {
        assertEquals("https://zvonili.com/phone/9618719370", ZvoniliParser.url("+79618719370"))
        assertEquals("https://zvonili.com/phone/4951234567", ZvoniliParser.url("+74951234567"))
        assertNull(ZvoniliParser.url("+380441234567"))
        assertNull(ZvoniliParser.url("900"))
        assertFalse(ZvoniliParser.isRussian(null))
    }

    @Test fun manyReviews() {
        val r = ZvoniliParser.parse("+79618719370", page("many"), now = 42)
        assertTrue(r.found)
        assertEquals(1.35, r.rating!!, 1e-9)
        assertEquals(7631, r.reviews)
        assertEquals(580059, r.views)
        assertEquals(listOf("Неадекваты", "Мошенники", "Реклама", "Хулиганы", "Колл-центры", "Опросы", "Коллекторы"), r.categories)
        assertEquals("ПАО \"Вымпел-Коммуникации\"", r.operator)
        assertEquals("Новосибирская обл.", r.region)
        assertEquals(2, r.comments.size)
        assertEquals("18.10.2026 19:53", r.comments[0].date)
        assertEquals("Мошенники", r.comments[0].type)
        assertTrue(r.comments[0].text.startsWith("Не успел продать свою квартиру, как стали поступать"))
        assertEquals("Звонит губошлёп", r.comments[1].text)
        assertEquals(42, r.fetchedAt)
        assertEquals(NumberReport.Verdict.BAD, r.verdict)
        assertEquals("1.35/5 · 7631 отзыв · Неадекваты, Мошенники, Реклама", r.summary())
    }

    @Test fun oneReview() {
        val r = ZvoniliParser.parse("+74712361584", page("one"))
        assertEquals(1.0, r.rating!!, 1e-9)
        assertEquals(1, r.reviews)
        assertEquals(listOf("Мошенники"), r.categories)
        assertEquals("ПАО \"Ростелеком\"", r.operator)
        assertEquals("г. Курск Курская обл.", r.region)
        assertEquals(1, r.comments.size)
        assertTrue(r.comments[0].text.length <= 300)
        assertTrue(r.comments[0].text.endsWith("…"))
        assertEquals("1/5 · 1 отзыв · Мошенники", r.summary())
    }

    @Test fun noReviews() {
        val r = ZvoniliParser.parse("+79999270779", page("none"))
        assertTrue(r.found)
        assertNull(r.rating)
        assertEquals(0, r.reviews)
        assertEquals(3, r.views)
        assertTrue(r.categories.isEmpty())
        assertEquals("ООО \"Т2 Мобайл\"", r.operator)
        assertEquals("г. Москва и Московская область", r.region)
        assertEquals(NumberReport.Verdict.UNKNOWN, r.verdict)
        assertEquals("отзывов нет", r.summary())
        assertEquals("ООО \"Т2 Мобайл\" · г. Москва и Московская область", r.where())
    }

    @Test fun notFound() {
        val r = ZvoniliParser.parse("+79154562110", page("notfound"))
        assertFalse(r.found)
        assertEquals("номер не найден", r.summary())
    }

    @Test fun truncatedPageStillParses() {
        val html = page("many")
        val cut = html.substring(0, html.indexOf("itemprop=\"review\"") + 200)
        val r = ZvoniliParser.parse("+79618719370", cut)
        assertEquals(7631, r.reviews)
        assertEquals("Новосибирская обл.", r.region)
        assertTrue(r.comments.isEmpty())
    }

    @Test fun plurals() {
        assertEquals("отзыв", NumberReport.plural(1, "отзыв", "отзыва", "отзывов"))
        assertEquals("отзыва", NumberReport.plural(23, "отзыв", "отзыва", "отзывов"))
        assertEquals("отзывов", NumberReport.plural(12, "отзыв", "отзыва", "отзывов"))
        assertEquals("отзывов", NumberReport.plural(646, "отзыв", "отзыва", "отзывов"))
        assertEquals("1.5", NumberReport.fmtRating(1.5))
        assertEquals("2", NumberReport.fmtRating(2.0))
    }
}
