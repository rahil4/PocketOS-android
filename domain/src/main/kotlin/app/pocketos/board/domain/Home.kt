package app.pocketos.board.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

enum class Reason { OVER, SOON, STALE, FOLLOW, NEW }

/** آستانه‌های «نیاز به توجه» (پیش‌فرض‌های وب‌اپ: BOARD_STALE_DAYS، BOARD_WAIT_DAYS، BOARD_NEW_DAYS). */
data class AttentionConfig(val staleDays: Int = 5, val waitDays: Int = 14, val newDays: Int = 2, val soonDays: Int = 3)

data class CaseInfo(
    val id: Long,
    val title: String,
    val customerId: Long?,
    val customerName: String?,
    val status: CaseStatus,
    val due: LocalDate?,
    /** روزهای گذشته از آخرین فعالیت. */
    val idleDays: Int,
)

data class AttentionItem(val case: CaseInfo, val reasons: List<Pair<Reason, String>>, val rank: Int) {
    /** «دیدم، بماند» فقط وقتی دلیل مهلت نیست. */
    val keepOk: Boolean get() = reasons.none { it.first == Reason.OVER || it.first == Reason.SOON }
}

/** پرونده‌هایی که توجه می‌خواهند؛ با زبان آرام (بدون «دیرکرد» و سرزنش). */
object Attention {
    const val MAX = 60

    fun list(cases: List<CaseInfo>, today: LocalDate, cfg: AttentionConfig = AttentionConfig()): List<AttentionItem> {
        val out = ArrayList<AttentionItem>()
        for (c in cases) {
            if (!c.status.open) continue
            val reasons = ArrayList<Pair<Reason, String>>()
            var rank = 9
            c.due?.let { due ->
                val delta = ChronoUnit.DAYS.between(today, due).toInt()
                if (delta < 0) {
                    reasons += Reason.OVER to Digits.fa("مهلت ${-delta} روز پیش بود")
                    rank = minOf(rank, 0)
                } else if (delta <= cfg.soonDays) {
                    reasons += Reason.SOON to when (delta) {
                        0 -> "مهلت: امروز"
                        1 -> "مهلت: فردا"
                        else -> Digits.fa("مهلت: $delta روز دیگر")
                    }
                    rank = minOf(rank, 1)
                }
            }
            if (c.status == CaseStatus.DOING && c.idleDays >= cfg.staleDays) {
                reasons += Reason.STALE to Digits.fa("${c.idleDays} روز است سر نزده‌ای")
                rank = minOf(rank, 2)
            }
            if (c.status == CaseStatus.WAITING && c.idleDays >= cfg.waitDays) {
                reasons += Reason.FOLLOW to Digits.fa("${c.idleDays} روز است منتظر جواب است — یک پیگیری")
                rank = minOf(rank, 3)
            }
            if (c.status == CaseStatus.NEW && c.idleDays >= cfg.newDays) {
                reasons += Reason.NEW to "هنوز نگاه نشده"
                rank = minOf(rank, 4)
            }
            if (reasons.isNotEmpty()) out += AttentionItem(c, reasons, rank)
        }
        return out.sortedWith(compareBy<AttentionItem>({ it.rank }, { it.case.due ?: LocalDate.MAX }, { -it.case.idleDays })).take(MAX)
    }
}

/** یک ردیف «امروز»: کار یا پروندهٔ نیازمند توجه. */
data class HomeEntry(
    val key: String,
    val taskId: Long?,
    val caseId: Long?,
    val title: String,
    val customerId: Long?,
    val customerName: String?,
    val sub: String,
    val amber: Boolean,
    val lateDays: Int,
    val repeat: String? = null,
    val keepOk: Boolean = false,
) {
    val isTask: Boolean get() = taskId != null
}

data class HomeGroup(val customerId: Long?, val name: String?, val items: List<HomeEntry>)

object HomeLogic {
    /** از این روز به بعد، کار عقب‌افتاده در خانه جمع می‌شود (فقط شمرده می‌شود). */
    const val OLD_LATE = 2
    const val MAX_GROUPS = 4
    const val MAX_ITEMS = 3

    /** «از دیروز مانده»، «از ۳ روز پیش مانده»، یا نام روز. */
    fun taskNote(due: LocalDate, today: LocalDate): String {
        val delta = ChronoUnit.DAYS.between(today, due)
        if (delta < 0) return if (delta == -1L) "از دیروز مانده" else Digits.fa("از ${-delta} روز پیش مانده")
        return DayLabel.of(due, today)
    }

    fun greeting(hour: Int): String = when {
        hour < 12 -> "صبح بخیر"
        hour < 17 -> "روز بخیر"
        hour < 21 -> "عصر بخیر"
        else -> "شب بخیر"
    }

    /** گروه‌بندی بر اساس مشتری به ترتیب اولین دیده‌شدن. */
    fun group(entries: List<HomeEntry>): List<HomeGroup> {
        val order = LinkedHashMap<Long, MutableList<HomeEntry>>()
        entries.forEach { order.getOrPut(it.customerId ?: 0L) { ArrayList() } += it }
        return order.values.map { items -> HomeGroup(items.first().customerId, items.first().customerName, items) }
    }

    /** خانه: حداکثر ۴ مشتری و برای هر کدام ۳ مورد؛ تعداد مواردِ نمایش‌داده‌نشده هم برمی‌گردد. */
    fun limit(groups: List<HomeGroup>, maxGroups: Int = MAX_GROUPS, maxItems: Int = MAX_ITEMS): Pair<List<HomeGroup>, Int> {
        val total = groups.sumOf { it.items.size }
        val shown = groups.take(maxGroups).map { it.copy(items = it.items.take(maxItems)) }
        return shown to (total - shown.sumOf { it.items.size })
    }

    /** پیشنهاد «روز را ببند»: بعد از ساعت تنظیم‌شده و اگر امروز بسته نشده. */
    fun eveningVisible(hourNow: Int, eveningHour: Int, closedOn: String?, today: LocalDate): Boolean =
        eveningHour in 1..23 && hourNow >= eveningHour && closedOn != today.toString()

    /** انتخاب‌های «بستن روز»؛ null یعنی رها کن. */
    fun shifted(choice: String, today: LocalDate): LocalDate? = when (choice) {
        "tomorrow" -> today.plusDays(1)
        "after" -> today.plusDays(2)
        "week" -> today.plusDays(7)
        else -> null
    }
}
