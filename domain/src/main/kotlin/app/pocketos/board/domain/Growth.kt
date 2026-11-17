package app.pocketos.board.domain

import java.time.LocalDate

/** بازهٔ بسته‌ی روزها: [from]..[to] هر دو شامل. */
data class Span(val from: LocalDate, val to: LocalDate) {
    operator fun contains(d: LocalDate) = d >= from && d <= to
    val days: Int get() = (to.toEpochDay() - from.toEpochDay()).toInt() + 1
}

/** دورهٔ جاری تا امروز و همان مقدارِ دورهٔ قبل (تا «همان روز»)، برای مقایسهٔ منصفانه. */
data class Period(val whole: Span, val toDate: Span, val prevToDate: Span, val prevWhole: Span)

/** هفته (شنبه تا جمعه)، ماه و فصل جلالی؛ پایهٔ شمارنده‌های «رشد» در خانه. */
object Growth {
    private val SEASONS = listOf("بهار", "تابستان", "پاییز", "زمستان")

    fun weekStart(today: LocalDate): LocalDate = today.minusDays(Jalali.weekColumn(today).toLong())

    fun week(today: LocalDate): Period {
        val start = weekStart(today)
        val whole = Span(start, start.plusDays(6))
        val prevWhole = Span(start.minusDays(7), start.minusDays(1))
        val off = today.toEpochDay() - start.toEpochDay()
        return Period(whole, Span(start, today), Span(prevWhole.from, prevWhole.from.plusDays(off)), prevWhole)
    }

    private fun monthSpan(jy: Int, jm: Int): Span =
        Span(Jalali.fromJalali(jy, jm, 1), Jalali.fromJalali(jy, jm, Jalali.monthLength(jy, jm)))

    fun month(today: LocalDate): Period {
        val j = Jalali.toJalali(today)
        val whole = monthSpan(j.year, j.month)
        val (py, pm) = if (j.month == 1) (j.year - 1) to 12 else j.year to (j.month - 1)
        val prevWhole = monthSpan(py, pm)
        val pDay = minOf(j.day, Jalali.monthLength(py, pm))
        return Period(whole, Span(whole.from, today), Span(prevWhole.from, Jalali.fromJalali(py, pm, pDay)), prevWhole)
    }

    private fun seasonSpan(jy: Int, index: Int): Span {
        val m0 = index * 3 + 1
        return Span(Jalali.fromJalali(jy, m0, 1), Jalali.fromJalali(jy, m0 + 2, Jalali.monthLength(jy, m0 + 2)))
    }

    fun seasonIndex(today: LocalDate): Int = (Jalali.toJalali(today).month - 1) / 3

    fun seasonName(today: LocalDate): String = SEASONS[seasonIndex(today)]

    fun season(today: LocalDate): Period {
        val j = Jalali.toJalali(today)
        val idx = seasonIndex(today)
        val whole = seasonSpan(j.year, idx)
        val prevWhole = if (idx == 0) seasonSpan(j.year - 1, 3) else seasonSpan(j.year, idx - 1)
        val off = today.toEpochDay() - whole.from.toEpochDay()
        val prevTo = minOf(prevWhole.from.plusDays(off), prevWhole.to)
        return Period(whole, Span(whole.from, today), Span(prevWhole.from, prevTo), prevWhole)
    }

    /** جملهٔ مقایسه؛ آرام و خنثی (هرگز «کمتر شده»). [label] مثل «ماه قبل». */
    fun compare(cur: Int, prev: Int, label: String): String = when {
        cur > prev -> Digits.fa("${cur - prev} بیشتر از $label")
        cur == prev -> "برابر $label"
        else -> Digits.fa("$label تا همین روز $prev بود")
    }
}
