package app.pocketos.board.domain

import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhenTest {
    private val today = LocalDate.of(2026, 11, 15) // یکشنبه ۲۴ آبان ۱۴۰۵

    @Test
    fun relativeDays() {
        assertEquals(today.plusDays(1) to "تحویل نقشه", When.parse("فردا تحویل نقشه", today))
        assertEquals(today.plusDays(2) to "کار", When.parse("کار پس‌فردا", today))
        assertEquals(today to "تماس", When.parse("امروز تماس", today))
        assertEquals(today.plusDays(3) to "تماس", When.parse("+۳ تماس", today))
        assertEquals(today.plusDays(7) to "گزارش", When.parse("هفته بعد گزارش", today))
    }

    @Test
    fun weekdaysAreAlwaysInTheFuture() {
        assertEquals(LocalDate.of(2026, 11, 17) to "تحویل", When.parse("سه‌شنبه تحویل", today))
        assertEquals(LocalDate.of(2026, 11, 22) to "تماس", When.parse("یکشنبه تماس", today)) // خودِ امروز نه
        assertEquals(LocalDate.of(2026, 11, 20) to "پیگیری", When.parse("پیگیری جمعه", today))
        assertEquals(LocalDate.of(2026, 11, 21) to "کار", When.parse("روز شنبه کار", today))
    }

    @Test
    fun jalaliDates() {
        assertEquals(LocalDate.of(2026, 11, 19) to "تماس", When.parse("۲۸ آبان تماس", today))
        // گذشته ← سال بعد
        assertEquals(Jalali.fromJalali(1406, 8, 1) to "نقشه", When.parse("۱ آبان نقشه", today))
        assertEquals(Jalali.fromJalali(1405, 12, 5) to "الف", When.parse("۵ اسفند ۱۴۰۵ الف", today).let { it?.first to "الف" })
        assertEquals(LocalDate.of(2026, 11, 19) to "الف", When.parse("۱۴۰۵/۰۸/۲۸ الف", today))
        assertEquals(LocalDate.of(2026, 12, 25) to "ب", When.parse("2026-12-25 ب", today))
    }

    @Test
    fun titleKeepsHalfSpaceAndDigits() {
        assertEquals(today.plusDays(1) to "می‌رود ۱۲۳", When.parse("فردا می‌رود ۱۲۳", today))
        assertEquals(today.plusDays(1) to "می‌رود", When.parse("می‌رود فردا", today))
        assertEquals("می‌خواهد", When.parseRepeat("هر هفته می‌خواهد", today)!!.third)
        assertEquals("علی‌اکبر", Normalize.clean("  علی‌اکبر  "))
    }

    @Test
    fun noDay() {
        assertNull(When.parse("تحویل نقشه", today))
        assertNull(When.parse("", today))
        assertNull(When.parse("۳۱ اسفند ۱۴۰۴ الف", today)) // روز وجود ندارد
    }

    @Test
    fun repeats() {
        assertEquals(Triple("weekly", today.plusDays(7), "تماس"), When.parseRepeat("هر هفته تماس", today))
        assertEquals(Triple("every:3", today.plusDays(3), "آب"), When.parseRepeat("هر ۳ روز آب", today))
        assertEquals(Triple("weekly", LocalDate.of(2026, 11, 16), "گزارش"), When.parseRepeat("هر دوشنبه گزارش", today))
        assertEquals(Triple("daily", today, "دارو"), When.parseRepeat("دارو هر روز", today))
        assertEquals(Triple("monthly", LocalDate.of(2026, 12, 15), "قسط"), When.parseRepeat("هر ماه قسط", today))
        assertEquals("weekly", When.parseRepeat("هر هفته سه‌شنبه تماس", today)!!.first)
        assertEquals(LocalDate.of(2026, 11, 17), When.parseRepeat("هر هفته سه‌شنبه تماس", today)!!.second)
        assertNull(When.parseRepeat("هر هفته", today))
        assertNull(When.parseRepeat("هر ۴۰۰ روز الف", today))
    }

    @Test
    fun nextDueStaysOnFixedSchedule() {
        assertEquals(LocalDate.of(2026, 11, 17), When.nextDue(LocalDate.of(2026, 11, 10), "weekly", today))
        assertEquals(LocalDate.of(2026, 11, 16), When.nextDue(LocalDate.of(2026, 11, 10), "every:3", today))
        assertEquals(today.plusDays(1), When.nextDue(LocalDate.of(2026, 11, 10), "daily", today))
        assertEquals(LocalDate.of(2026, 12, 1), When.nextDue(Jalali.fromJalali(1405, 8, 10), "monthly", today))
        assertNull(When.nextDue(today, "bogus", today))
    }

    @Test
    fun addMonthsClampsToMonthEnd() {
        // ۳۱ شهریور + ۱ ماه = ۳۰ مهر
        assertEquals(Jalali.fromJalali(1405, 7, 30), When.addMonths(Jalali.fromJalali(1405, 6, 31), 1))
        assertEquals(Jalali.fromJalali(1406, 1, 15), When.addMonths(Jalali.fromJalali(1405, 12, 15), 1))
    }

    @Test
    fun repeatLabels() {
        assertEquals("هر هفته", When.repeatLabel("weekly"))
        assertEquals("هر ۳ روز", When.repeatLabel("every:3"))
        assertEquals("", When.repeatLabel(null))
        assertTrue(When.validRepeat("every:2"))
        assertFalse(When.validRepeat("every:1"))
        assertFalse(When.validRepeat("x"))
    }

    @Test
    fun analyze() {
        assertEquals(When.Capture("تحویل نقشه #اداره", today.plusDays(1), null), When.analyze("فردا تحویل نقشه #اداره", today))
        assertEquals("weekly", When.analyze("هر هفته تماس", today)!!.repeat)
        assertNull(When.analyze("فقط یک یادداشت", today))
        assertNull(When.analyze("فردا", today)) // بدون عنوان
    }

    @Test
    fun tags() {
        assertEquals(listOf("اداره", "نقشه"), Tags.tagsIn("تحویل #اداره و #نقشه و #اداره"))
        assertEquals(emptyList(), Tags.tagsIn("شماره #۱۲"))
        assertEquals(emptyList(), Tags.tagsIn("abc#x"))
        assertEquals("تحویل نقشه", Tags.strip("تحویل #اداره نقشه"))
        assertTrue(Tags.hasTag("کار #اداره", "اداره"))
        assertFalse(Tags.hasTag("کار #ادارهکل", "اداره"))
        assertEquals(listOf("اداره"), Tags.tagsIn("#اداره‌کل".replace("‌کل", ""))) // نیم‌فاصله برچسب را می‌شکند
        assertEquals(listOf("اداره"), Tags.tagsIn("#ادارهٔ".replace("ٔ", "")))
    }

    @Test
    fun recurrenceAnchor() {
        val due = LocalDate.of(2026, 11, 17)
        val moved = LocalDate.of(2026, 11, 19)
        assertEquals(due, Recurrence.anchorAfterMove(due, null, moved, "weekly"))
        assertEquals(due, Recurrence.anchorAfterMove(moved, due, LocalDate.of(2026, 11, 20), "weekly"))
        assertNull(Recurrence.anchorAfterMove(moved, due, due, "weekly")) // برگشت به برنامه
        assertNull(Recurrence.anchorAfterMove(due, null, moved, null))
        assertEquals(LocalDate.of(2026, 11, 24), Recurrence.nextAfterDone(moved, due, "weekly", moved))
        // نوبتِ جابه‌جاشده پیش از رسیدن روز برنامه انجام شد ← نوبت بعد، بعد از روز برنامه
        assertEquals(LocalDate.of(2026, 11, 24), Recurrence.nextAfterDone(moved, due, "weekly", LocalDate.of(2026, 11, 15)))
        assertEquals(LocalDate.of(2026, 11, 24), Recurrence.nextAfterDone(due, null, "weekly", LocalDate.of(2026, 11, 15))) // انجام زودهنگام
        assertEquals(LocalDate.of(2026, 11, 18), Recurrence.nextAfterDone(LocalDate.of(2026, 11, 17), null, "daily", LocalDate.of(2026, 11, 15)))
    }

    @Test
    fun stamp() {
        val now = LocalDate.of(2026, 11, 15).atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val h = 3_600_000L
        assertEquals("امروز · ۰۹:۰۵", Stamp.of(now - 2 * h - 55 * 60_000, now, ZoneOffset.UTC))
        assertEquals("دیروز · ۱۲:۰۰", Stamp.of(now - 24 * h, now, ZoneOffset.UTC))
        assertEquals("۲۱ آبان · ۱۲:۰۰", Stamp.of(now - 72 * h, now, ZoneOffset.UTC))
    }
}
