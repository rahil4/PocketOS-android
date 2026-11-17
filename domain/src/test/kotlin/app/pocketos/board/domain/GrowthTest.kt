package app.pocketos.board.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GrowthTest {
    private val today = LocalDate.of(2026, 11, 16) // یکشنبه ۲۵ آبان ۱۴۰۵

    @Test
    fun weekRunsSaturdayToFriday() {
        val p = Growth.week(today)
        assertEquals(LocalDate.of(2026, 11, 14), p.whole.from) // شنبه
        assertEquals(LocalDate.of(2026, 11, 20), p.whole.to) // جمعه
        assertEquals(Span(LocalDate.of(2026, 11, 14), today), p.toDate)
        assertEquals(LocalDate.of(2026, 11, 7), p.prevWhole.from)
        assertEquals(LocalDate.of(2026, 11, 8), p.prevToDate.to) // دو روز اول هفتهٔ قبل
        assertEquals(2, p.prevToDate.days)
    }

    @Test
    fun saturdayIsItsOwnWeekStart() {
        val sat = LocalDate.of(2026, 11, 14)
        assertEquals(sat, Growth.weekStart(sat))
        assertEquals(1, Growth.week(sat).toDate.days)
    }

    @Test
    fun jalaliMonthAndPreviousUpToTheSameDay() {
        val p = Growth.month(today) // آبان ۱۴۰۵: ۱ تا ۳۰
        assertEquals(Jalali.fromJalali(1405, 8, 1), p.whole.from)
        assertEquals(Jalali.fromJalali(1405, 8, 30), p.whole.to)
        assertEquals(Jalali.fromJalali(1405, 7, 1), p.prevWhole.from)
        assertEquals(Jalali.fromJalali(1405, 7, 25), p.prevToDate.to)
    }

    @Test
    fun previousMonthShorterThanDayIsClamped() {
        val first = Jalali.fromJalali(1406, 1, 31) // آخر فروردین؛ ماه قبل (اسفند) کوتاه‌تر است
        val p = Growth.month(first)
        assertTrue(p.prevToDate.to <= p.prevWhole.to)
        assertEquals(Jalali.fromJalali(1405, 12, 1), p.prevWhole.from)
    }

    @Test
    fun seasonsAndPreviousYear() {
        assertEquals("پاییز", Growth.seasonName(today))
        val p = Growth.season(today)
        assertEquals(Jalali.fromJalali(1405, 7, 1), p.whole.from)
        assertEquals(Jalali.fromJalali(1405, 9, 30), p.whole.to)
        assertEquals(Jalali.fromJalali(1405, 4, 1), p.prevWhole.from) // تابستان
        val spring = Growth.season(Jalali.fromJalali(1405, 2, 10))
        assertEquals(Jalali.fromJalali(1404, 10, 1), spring.prevWhole.from) // زمستان سال قبل
        assertEquals("زمستان", Growth.seasonName(Jalali.fromJalali(1405, 11, 5)))
    }

    @Test
    fun compareIsNeverNegative() {
        assertEquals("۳ بیشتر از ماه قبل", Growth.compare(18, 15, "ماه قبل"))
        assertEquals("برابر ماه قبل", Growth.compare(5, 5, "ماه قبل"))
        assertEquals("ماه قبل تا همین روز ۹ بود", Growth.compare(5, 9, "ماه قبل"))
    }
}
