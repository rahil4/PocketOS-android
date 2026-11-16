package app.pocketos.board.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalendarTest {
    private val today = LocalDate.of(2026, 11, 15) // یکشنبه ۲۴ آبان ۱۴۰۵

    private fun item(key: String, day: LocalDate, title: String = key, cust: Long? = 1, name: String? = "الف", done: Boolean = false) =
        CalItem(key, day, title, cust, name, null, null, key.removePrefix("t").toLongOrNull(), done, null)

    @Test
    fun monthGridStartsOnSaturdayAndCoversWholeWeeks() {
        // آبان ۱۴۰۵: ۱ آبان جمعه (۱ آبان = ۲۳ اکتبر ۲۰۲۶)، ۳۰ آبان شنبه
        val (from, to) = CalRange.of(CalView.MONTH, today)
        assertEquals(LocalDate.of(2026, 10, 17), from) // شنبهٔ هفتهٔ اول
        assertEquals(LocalDate.of(2026, 11, 27), to) // جمعهٔ هفتهٔ آخر
        val weeks = CalRange.monthWeeks(today)
        assertEquals(6, weeks.size)
        assertTrue(weeks.all { it.size == 7 })
        assertEquals(0, Jalali.weekColumn(weeks[0][0]))
        assertEquals(6, Jalali.weekColumn(weeks[5][6]))
    }

    @Test
    fun februaryLikeShortMonthsAndLeapEsfand() {
        // اسفند ۱۴۰۳ کبیسه (۳۰ روز)
        val esfand = Jalali.fromJalali(1403, 12, 10)
        assertEquals(Jalali.fromJalali(1403, 12, 30), CalRange.monthEnd(esfand))
        val esfand1404 = Jalali.fromJalali(1404, 12, 10)
        assertEquals(Jalali.fromJalali(1404, 12, 29), CalRange.monthEnd(esfand1404))
        val (from, to) = CalRange.of(CalView.MONTH, esfand)
        assertEquals(0, Jalali.weekColumn(from))
        assertEquals(6, Jalali.weekColumn(to))
        assertTrue(from <= CalRange.monthStart(esfand) && to >= CalRange.monthEnd(esfand))
    }

    @Test
    fun weekThreeDayAndDayRanges() {
        assertEquals(LocalDate.of(2026, 11, 14) to LocalDate.of(2026, 11, 20), CalRange.of(CalView.WEEK, today))
        assertEquals(today to today.plusDays(2), CalRange.of(CalView.THREE, today))
        assertEquals(today to today, CalRange.of(CalView.DAY, today))
        // خودِ شنبه و جمعه در هفتهٔ درست می‌افتند
        assertEquals(LocalDate.of(2026, 11, 14), CalRange.weekStart(LocalDate.of(2026, 11, 14)))
        assertEquals(LocalDate.of(2026, 11, 14), CalRange.weekStart(LocalDate.of(2026, 11, 20)))
    }

    @Test
    fun navigation() {
        assertEquals(LocalDate.of(2026, 12, 15), CalRange.shift(CalView.MONTH, today, 1)) // ۲۴ آذر
        assertEquals(LocalDate.of(2026, 10, 16), CalRange.shift(CalView.MONTH, today, -1)) // ۲۴ مهر
        assertEquals(today.plusDays(7), CalRange.shift(CalView.WEEK, today, 1))
        assertEquals(today.minusDays(3), CalRange.shift(CalView.THREE, today, -1))
        assertEquals(today.plusDays(1), CalRange.shift(CalView.DAY, today, 1))
    }

    @Test
    fun titles() {
        assertEquals("آبان ۱۴۰۵", CalRange.title(CalView.MONTH, today))
        assertEquals("۲۳ تا ۲۹ آبان", CalRange.title(CalView.WEEK, today))
        assertEquals("۲۴ تا ۲۶ آبان", CalRange.title(CalView.THREE, today))
        assertEquals("یکشنبه ۲۴ آبان ۱۴۰۵", CalRange.title(CalView.DAY, today))
        // هفتهٔ شنبه ۳۰ آبان تا جمعه ۵ آذر
        assertEquals("۳۰ آبان تا ۶ آذر", CalRange.title(CalView.WEEK, LocalDate.of(2026, 11, 21)))
        assertEquals("۲۲ تا ۲۸ اسفند", CalRange.title(CalView.WEEK, Jalali.fromJalali(1405, 12, 28)))
        // ۲۹ اسفند ۱۴۰۵ شنبه است؛ هفته‌اش وارد سال بعد می‌شود
        assertEquals("۲۹ اسفند ۱۴۰۵ تا ۶ فروردین ۱۴۰۶", CalRange.title(CalView.WEEK, Jalali.fromJalali(1405, 12, 29)))
    }

    @Test
    fun dotsAndSummary() {
        val items = listOf(
            item("t1", today, done = true),
            item("t2", today.plusDays(1)),
            item("t3", today),
            item("t4", today.minusDays(1), done = true),
        )
        val dots = CalendarLogic.dots(items, today)
        assertEquals(Dot.ALERT, dots[today]) // امروز کار باز دارد
        assertEquals(Dot.OPEN, dots[today.plusDays(1)])
        assertEquals(Dot.DONE, dots[today.minusDays(1)])
        assertEquals("۲ کار · ۱ انجام شد", CalendarLogic.summary(items.filter { it.day == today }))
        assertEquals("۱ کار", CalendarLogic.summary(listOf(item("t2", today))))
    }

    @Test
    fun marksForMonthGrid() {
        val due = CalItem("k1", today.plusDays(3), "مهلت: پرونده", 1, "الف", 9, "پرونده", null, false, null)
        val items = listOf(
            item("t1", today), item("t2", today.minusDays(2)), item("t3", today.minusDays(5), done = true), due,
        )
        val m = CalendarLogic.marks(items, today)
        assertEquals(DayMarks(task = true, deadline = false, late = false, doneOnly = false), m[today])
        assertEquals(DayMarks(task = true, deadline = false, late = true, doneOnly = false), m[today.minusDays(2)])
        assertEquals(DayMarks(task = false, deadline = false, late = false, doneOnly = true), m[today.minusDays(5)])
        assertEquals(DayMarks(task = false, deadline = true, late = false, doneOnly = false), m[today.plusDays(3)])
    }

    @Test
    fun dayListOpenFirstThenDone() {
        val d = CalendarLogic.byDay(listOf(item("t1", today, "ب", done = true), item("t2", today, "الف"), item("t3", today, "ت")))
        assertEquals(listOf("t2", "t3", "t1"), d[today]!!.map { it.key })
    }

    @Test
    fun groupsByCustomer() {
        val items = listOf(
            item("t1", today, cust = 2, name = "محمدی", done = true),
            item("t2", today, cust = 1, name = "رنجبر"),
            item("t3", today, cust = null, name = null),
            item("t4", today, cust = 3, name = "کریمی"),
        )
        val g = CalendarLogic.groupByCustomer(items)
        assertEquals(listOf("رنجبر", "کریمی", "محمدی", "بدون مشتری"), g.map { it.name })
        assertEquals(1, g[0].openCount)
    }
}
