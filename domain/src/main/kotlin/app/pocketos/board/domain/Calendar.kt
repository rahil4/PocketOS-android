package app.pocketos.board.domain

import java.time.LocalDate

enum class CalView(val code: String, val label: String) {
    MONTH("month", "ماه"), WEEK("week", "هفته"), THREE("3", "۳ روز"), DAY("day", "روز");

    companion object {
        fun of(code: String?): CalView = entries.firstOrNull { it.code == code } ?: MONTH
    }
}

/** بازه‌ها و ناوبری نماهای تقویم شمسی؛ هفته از شنبه تا جمعه. */
object CalRange {
    fun weekStart(d: LocalDate): LocalDate = d.minusDays(Jalali.weekColumn(d).toLong())

    fun monthStart(d: LocalDate): LocalDate {
        val j = Jalali.toJalali(d)
        return Jalali.fromJalali(j.year, j.month, 1)
    }

    fun monthEnd(d: LocalDate): LocalDate {
        val j = Jalali.toJalali(d)
        return monthStart(d).plusDays(Jalali.monthLength(j.year, j.month) - 1L)
    }

    /** بازهٔ دادهٔ هر نما؛ ماه = تمام هفته‌های شبکه (با روزهای ماه‌های کناری). */
    fun of(view: CalView, anchor: LocalDate): Pair<LocalDate, LocalDate> = when (view) {
        CalView.MONTH -> weekStart(monthStart(anchor)) to weekStart(monthEnd(anchor)).plusDays(6)
        CalView.WEEK -> weekStart(anchor).let { it to it.plusDays(6) }
        CalView.THREE -> anchor to anchor.plusDays(2)
        CalView.DAY -> anchor to anchor
    }

    fun days(from: LocalDate, to: LocalDate): List<LocalDate> =
        generateSequence(from) { it.plusDays(1) }.takeWhile { it <= to }.toList()

    /** ردیف‌های هفتگیِ شبکهٔ ماه. */
    fun monthWeeks(anchor: LocalDate): List<List<LocalDate>> {
        val (from, to) = of(CalView.MONTH, anchor)
        return days(from, to).chunked(7)
    }

    fun shift(view: CalView, anchor: LocalDate, dir: Int): LocalDate = when (view) {
        CalView.MONTH -> When.addMonths(anchor, dir)
        CalView.WEEK -> anchor.plusDays(7L * dir)
        CalView.THREE -> anchor.plusDays(3L * dir)
        CalView.DAY -> anchor.plusDays(dir.toLong())
    }

    fun sameJalaliMonth(a: LocalDate, b: LocalDate): Boolean {
        val x = Jalali.toJalali(a)
        val y = Jalali.toJalali(b)
        return x.year == y.year && x.month == y.month
    }

    fun title(view: CalView, anchor: LocalDate): String {
        val j = Jalali.toJalali(anchor)
        if (view == CalView.MONTH) return Digits.fa("${Jalali.MONTHS[j.month - 1]} ${j.year}")
        if (view == CalView.DAY) return Jalali.formatLong(anchor)
        val (from, to) = of(view, anchor)
        val a = Jalali.toJalali(from)
        val b = Jalali.toJalali(to)
        return Digits.fa(
            when {
                a.year != b.year -> "${a.day} ${Jalali.MONTHS[a.month - 1]} ${a.year} تا ${b.day} ${Jalali.MONTHS[b.month - 1]} ${b.year}"
                a.month != b.month -> "${a.day} ${Jalali.MONTHS[a.month - 1]} تا ${b.day} ${Jalali.MONTHS[b.month - 1]}"
                else -> "${a.day} تا ${b.day} ${Jalali.MONTHS[a.month - 1]}"
            },
        )
    }
}

/** یک ردیف تقویم: کار، یا مهلتِ پرونده (taskId = null). */
data class CalItem(
    val key: String,
    val day: LocalDate,
    val title: String,
    val customerId: Long?,
    val customerName: String?,
    val caseId: Long?,
    val caseTitle: String?,
    val taskId: Long?,
    val done: Boolean,
    val repeat: String?,
) {
    val isCaseDue: Boolean get() = taskId == null
}

enum class Dot { NONE, OPEN, ALERT, DONE }

data class CalGroup(val customerId: Long?, val name: String, val items: List<CalItem>) {
    val openCount: Int get() = items.count { !it.done }
}

object CalendarLogic {
    /** باز اول، بعد انجام‌شده؛ در هر دسته الفبایی. */
    private val order = compareBy<CalItem>({ it.done }, { it.title })

    fun byDay(items: List<CalItem>): Map<LocalDate, List<CalItem>> =
        items.groupBy { it.day }.mapValues { (_, v) -> v.sortedWith(order) }

    /** نقطهٔ هر روز: کهربایی اگر کارِ بازِ امروز یا گذشته دارد، عادی اگر کار بازِ آینده، کم‌رنگ اگر فقط انجام‌شده. */
    fun dots(items: List<CalItem>, today: LocalDate): Map<LocalDate, Dot> =
        items.groupBy { it.day }.mapValues { (day, v) ->
            val open = v.filter { !it.done }
            when {
                open.isEmpty() -> Dot.DONE
                day <= today -> Dot.ALERT
                else -> Dot.OPEN
            }
        }

    /** گروه‌بندی بر اساس مشتری؛ گروه‌های دارای کار باز اول، «بدون مشتری» آخر. */
    fun groupByCustomer(items: List<CalItem>): List<CalGroup> {
        val groups = items.groupBy { it.customerId }.map { (id, v) ->
            CalGroup(id, v.firstNotNullOfOrNull { it.customerName } ?: "بدون مشتری", v.sortedWith(order))
        }
        return groups.sortedWith(
            compareBy<CalGroup>({ it.customerId == null }, { it.openCount == 0 }).then { a, b -> Names.compare(a.name, b.name) },
        )
    }

    /** «۳ کار · ۱ انجام شد» */
    fun summary(items: List<CalItem>): String {
        val done = items.count { it.done }
        val base = "${items.size} کار"
        return Digits.fa(if (done > 0) "$base · $done انجام شد" else base)
    }
}
