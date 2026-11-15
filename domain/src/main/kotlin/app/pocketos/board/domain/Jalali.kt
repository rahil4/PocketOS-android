package app.pocketos.board.domain

import java.time.LocalDate

/** تاریخ جلالی؛ ماه ۱ تا ۱۲. */
data class JalaliDate(val year: Int, val month: Int, val day: Int)

/**
 * تبدیل میلادی ⇄ جلالی (الگوریتم jdf، همان `jalali.py` وب‌اپ) و ابزارهای نمایش فارسی.
 * بدون وابستگی به اندروید.
 */
object Jalali {
    val MONTHS = listOf(
        "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
        "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
    )

    /** به ترتیب java.time.DayOfWeek: دوشنبه ... یکشنبه. */
    val WEEKDAYS = listOf("دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه", "شنبه", "یکشنبه")

    private val G_DM = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)

    fun gregorianToJalali(gy: Int, gm: Int, gd: Int): JalaliDate {
        val gy2 = if (gm > 2) gy + 1 else gy
        var days = 355666 + 365 * gy + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400 + gd + G_DM[gm - 1]
        var jy = -1595 + 33 * (days / 12053)
        days %= 12053
        jy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            jy += (days - 1) / 365
            days = (days - 1) % 365
        }
        val jm: Int
        val jd: Int
        if (days < 186) {
            jm = 1 + days / 31
            jd = 1 + days % 31
        } else {
            jm = 7 + (days - 186) / 30
            jd = 1 + (days - 186) % 30
        }
        return JalaliDate(jy, jm, jd)
    }

    private fun jalaliToGregorianParts(jyIn: Int, jm: Int, jd: Int): Triple<Int, Int, Int> {
        val jy = jyIn + 1595
        var days = -355668 + 365 * jy + (jy / 33) * 8 + ((jy % 33) + 3) / 4 + jd +
            (if (jm < 7) (jm - 1) * 31 else (jm - 7) * 30 + 186)
        var gy = 400 * (days / 146097)
        days %= 146097
        if (days > 36524) {
            days -= 1
            gy += 100 * (days / 36524)
            days %= 36524
            if (days >= 365) days += 1
        }
        gy += 4 * (days / 1461)
        days %= 1461
        if (days > 365) {
            gy += (days - 1) / 365
            days = (days - 1) % 365
        }
        var gd = days + 1
        val leap = (gy % 4 == 0 && gy % 100 != 0) || gy % 400 == 0
        val sal = intArrayOf(0, 31, if (leap) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var gm = 0
        while (gm < 13 && gd > sal[gm]) {
            gd -= sal[gm]
            gm++
        }
        return Triple(gy, gm, gd)
    }

    fun toJalali(d: LocalDate): JalaliDate = gregorianToJalali(d.year, d.monthValue, d.dayOfMonth)

    /** تاریخ نامعتبر `IllegalArgumentException` می‌دهد (مثلاً ۳۰ اسفندِ سال کبیسه‌نشده). */
    fun fromJalali(jy: Int, jm: Int, jd: Int): LocalDate {
        require(jy in 1200..1600 && jm in 1..12 && jd in 1..31) { "تاریخ نامعتبر است" }
        val (gy, gm, gd) = jalaliToGregorianParts(jy, jm, jd)
        val d = LocalDate.of(gy, gm, gd)
        require(toJalali(d) == JalaliDate(jy, jm, jd)) { "این روز در تقویم وجود ندارد" }
        return d
    }

    fun fromJalaliOrNull(jy: Int, jm: Int, jd: Int): LocalDate? =
        try { fromJalali(jy, jm, jd) } catch (_: IllegalArgumentException) { null } catch (_: java.time.DateTimeException) { null }

    /** تعداد روزهای ماهِ جلالی. */
    fun monthLength(jy: Int, jm: Int): Int = when {
        jm <= 6 -> 31
        jm <= 11 -> 30
        else -> if (fromJalaliOrNull(jy, 12, 30) != null) 30 else 29
    }

    /** نمایش عددی: ۱۴۰۵/۰۸/۲۴ */
    fun format(d: LocalDate): String {
        val j = toJalali(d)
        return Digits.fa("%04d/%02d/%02d".format(j.year, j.month, j.day))
    }

    /** نمایش بلند: یکشنبه ۲۴ آبان ۱۴۰۵ */
    fun formatLong(d: LocalDate): String {
        val j = toJalali(d)
        return Digits.fa("${WEEKDAYS[d.dayOfWeek.value - 1]} ${j.day} ${MONTHS[j.month - 1]} ${j.year}")
    }

    /**
     * ستون هفته در تقویم: شنبه = ۰ ... جمعه = ۶.
     */
    fun weekColumn(d: LocalDate): Int = (d.dayOfWeek.value + 1) % 7
}
