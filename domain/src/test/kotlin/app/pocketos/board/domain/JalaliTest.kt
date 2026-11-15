package app.pocketos.board.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class JalaliTest {
    @Test
    fun knownDates() {
        assertEquals(JalaliDate(1405, 8, 24), Jalali.toJalali(LocalDate.of(2026, 11, 15)))
        assertEquals(JalaliDate(1405, 1, 1), Jalali.toJalali(LocalDate.of(2026, 3, 21)))
        assertEquals(JalaliDate(1404, 1, 1), Jalali.toJalali(LocalDate.of(2025, 3, 21)))
        assertEquals(JalaliDate(1403, 1, 1), Jalali.toJalali(LocalDate.of(2024, 3, 20)))
        assertEquals(JalaliDate(1403, 12, 30), Jalali.toJalali(LocalDate.of(2025, 3, 20)))
    }

    @Test
    fun roundTripOverManyYears() {
        var d = LocalDate.of(1990, 1, 1)
        val end = LocalDate.of(2060, 12, 31)
        while (d <= end) {
            val j = Jalali.toJalali(d)
            assertEquals(d, Jalali.fromJalali(j.year, j.month, j.day), "رفت‌وبرگشت $d")
            d = d.plusDays(1)
        }
    }

    @Test
    fun monthLengths() {
        assertEquals(31, Jalali.monthLength(1405, 1))
        assertEquals(31, Jalali.monthLength(1405, 6))
        assertEquals(30, Jalali.monthLength(1405, 7))
        assertEquals(30, Jalali.monthLength(1405, 11))
        assertEquals(30, Jalali.monthLength(1403, 12)) // کبیسه
        assertEquals(29, Jalali.monthLength(1404, 12))
        assertEquals(29, Jalali.monthLength(1405, 12))
    }

    @Test
    fun invalidDates() {
        assertFailsWith<IllegalArgumentException> { Jalali.fromJalali(1404, 12, 30) }
        assertFailsWith<IllegalArgumentException> { Jalali.fromJalali(1404, 13, 1) }
        assertNull(Jalali.fromJalaliOrNull(1405, 7, 31))
    }

    @Test
    fun formatting() {
        val d = LocalDate.of(2026, 11, 15) // یکشنبه
        assertEquals("۱۴۰۵/۰۸/۲۴", Jalali.format(d))
        assertEquals("یکشنبه ۲۴ آبان ۱۴۰۵", Jalali.formatLong(d))
    }

    @Test
    fun weekStartsOnSaturday() {
        assertEquals(0, Jalali.weekColumn(LocalDate.of(2026, 11, 14))) // شنبه
        assertEquals(1, Jalali.weekColumn(LocalDate.of(2026, 11, 15))) // یکشنبه
        assertEquals(6, Jalali.weekColumn(LocalDate.of(2026, 11, 20))) // جمعه
    }

    @Test
    fun digits() {
        assertEquals("۱۲۳۴", Digits.fa(1234))
        assertEquals("1234", Digits.en("۱۲۳۴"))
        assertEquals("1234", Digits.en("١٢٣٤"))
        assertEquals("تلفن 09121234567", Digits.en("تلفن ۰۹۱۲۱۲۳۴۵۶۷"))
    }
}
