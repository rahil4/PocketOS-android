package app.pocketos.board.domain

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/** قواعد کار تکراری و جابه‌جایی نوبت. */
object Recurrence {
    /** نوبت بعدی پس از «انجام شد»: از برنامهٔ ثابت (anchor اگر نوبت جابه‌جا شده بود). */
    fun nextAfterDone(due: LocalDate, anchor: LocalDate?, repeat: String, today: LocalDate): LocalDate? =
        When.nextDue(anchor ?: due, repeat, today)

    /**
     * anchor تازه وقتی فقط همین نوبت به [newDay] می‌رود. برگرداندن به روز برنامه anchor را پاک می‌کند (null).
     * کار غیرتکراری anchor ندارد.
     */
    fun anchorAfterMove(oldDue: LocalDate?, oldAnchor: LocalDate?, newDay: LocalDate, repeat: String?): LocalDate? {
        if (repeat == null) return null
        val base = oldAnchor ?: oldDue ?: return null
        return if (newDay == base) null else base
    }
}

/** برچسب زمانِ یک مورد در سابقه: «دیروز · ۱۶:۲۰». */
object Stamp {
    fun of(millis: Long, nowMillis: Long, zone: ZoneId): String {
        val t = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), zone)
        val today = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(nowMillis), zone).toLocalDate()
        val d = t.toLocalDate()
        val delta = java.time.temporal.ChronoUnit.DAYS.between(d, today)
        val day = when {
            delta == 0L -> "امروز"
            delta == 1L -> "دیروز"
            else -> {
                val j = Jalali.toJalali(d)
                val base = "${j.day} ${Jalali.MONTHS[j.month - 1]}"
                if (j.year != Jalali.toJalali(today).year) "$base ${j.year}" else base
            }
        }
        return Digits.fa("$day · %02d:%02d".format(t.hour, t.minute))
    }
}
