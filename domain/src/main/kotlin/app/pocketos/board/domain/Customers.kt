package app.pocketos.board.domain

import java.text.Collator
import java.time.LocalDate
import java.util.Locale

/** وضعیت پرونده (همان وب‌اپ). */
enum class CaseStatus(val code: String, val label: String, val open: Boolean) {
    NEW("new", "جدید", true),
    DOING("doing", "در حال انجام", true),
    WAITING("waiting", "منتظر جواب", true),
    FINAL("final", "نهایی", false),
    ARCHIVE("archive", "آرشیو", false);

    companion object {
        fun of(code: String?): CaseStatus = entries.firstOrNull { it.code == code } ?: NEW
    }
}

object Names {
    val HONORIFICS = setOf("آقای", "اقای", "خانم", "دکتر", "مهندس", "استاد", "حاج", "حاجی", "سرکار", "جناب")

    /** واژه‌های نام، بدون آقای/خانم/دکتر… */
    fun tokens(name: String?): Set<String> = Normalize.words(name).filter { it !in HONORIFICS }.toSet()

    /** حرف آواتار: نخستین حرف نام بدون عنوان. */
    fun avatar(name: String?): String {
        val w = Normalize.words(name).firstOrNull { it !in HONORIFICS } ?: Normalize.words(name).firstOrNull()
        return w?.firstOrNull()?.toString() ?: "؟"
    }

    /** نام برای مرتب‌سازی الفبایی (بدون عنوان). */
    fun sortName(name: String?): String =
        Normalize.words(name).filter { it !in HONORIFICS }.joinToString(" ")

    /** متقاضی‌هایی که واژهٔ نامشان (حداقل ۳ حرف) در متن آمده؛ بیشترین واژهٔ مشترک اول. */
    fun matching(text: String, names: List<Pair<Long, String>>): List<Long> {
        val words = Normalize.words(text).toSet()
        return names.mapNotNull { (id, n) ->
            val hit = tokens(n).count { it.length >= 3 && it in words }
            if (hit > 0) id to hit else null
        }.sortedByDescending { it.second }.map { it.first }
    }

    private val collator: Collator by lazy { Collator.getInstance(Locale("fa")) }

    fun compare(a: String, b: String): Int = collator.compare(sortName(a), sortName(b))
}

object RelativeTime {
    private const val DAY = 86_400_000L

    /** «امروز»، «دیروز»، «۳ روز پیش»، «۲ هفته پیش»، «۳ ماه پیش». */
    fun ago(thenMillis: Long, nowMillis: Long): String {
        val days = ((nowMillis - thenMillis) / DAY).coerceAtLeast(0)
        return when {
            days == 0L -> "امروز"
            days == 1L -> "دیروز"
            days < 14 -> Digits.fa("$days روز پیش")
            days < 60 -> Digits.fa("${days / 7} هفته پیش")
            else -> Digits.fa("${days / 30} ماه پیش")
        }
    }
}

object DayLabel {
    /** «امروز»، «فردا»، «پس‌فردا» یا «سه‌شنبه ۲۸ مهر» (با سال اگر سال دیگری است). */
    fun of(d: LocalDate, today: LocalDate): String {
        val delta = java.time.temporal.ChronoUnit.DAYS.between(today, d)
        if (delta == 0L) return "امروز"
        if (delta == 1L) return "فردا"
        if (delta == 2L) return "پس‌فردا"
        val j = Jalali.toJalali(d)
        val ty = Jalali.toJalali(today).year
        val base = "${Jalali.WEEKDAYS[d.dayOfWeek.value - 1]} ${j.day} ${Jalali.MONTHS[j.month - 1]}"
        return Digits.fa(if (j.year != ty) "$base ${j.year}" else base)
    }
}

data class CaseChip(val id: Long, val title: String)

/** خلاصهٔ یک متقاضی برای فهرست. */
data class CustomerSummary(
    val id: Long,
    val name: String,
    val phone: String,
    val pinned: Boolean,
    val archived: Boolean,
    val openCases: Int,
    val openTasks: Int,
    val nextDue: LocalDate?,
    val lastActivity: Long,
    /** بدون پرونده، یادداشت و کار. */
    val isEmpty: Boolean,
    val createdAt: Long,
    val searchText: String,
    val cases: List<CaseChip> = emptyList(),
)

data class CustomerSections(
    val starred: List<CustomerSummary> = emptyList(),
    val withTasks: List<CustomerSummary> = emptyList(),
    val others: List<CustomerSummary> = emptyList(),
    val old: List<CustomerSummary> = emptyList(),
    val archived: List<CustomerSummary> = emptyList(),
    /** وقتی جست‌وجو هست: همهٔ نتیجه‌ها (با بایگانی‌شده‌ها) و بخش‌های دیگر خالی‌اند. */
    val results: List<CustomerSummary>? = null,
)

object CustomerList {
    const val OLD_DAYS = 180
    private const val DAY = 86_400_000L

    fun matches(s: CustomerSummary, query: String): Boolean =
        Normalize.words(query).all { it in s.searchText }

    fun build(all: List<CustomerSummary>, query: String, alpha: Boolean, nowMillis: Long): CustomerSections {
        val byName = Comparator<CustomerSummary> { a, b -> Names.compare(a.name, b.name) }
        val byActivity = compareByDescending<CustomerSummary> { it.lastActivity }
        val order = if (alpha) byName else byActivity

        if (Normalize.words(query).isNotEmpty()) {
            return CustomerSections(results = all.filter { matches(it, query) }.sortedWith(order))
        }
        val active = all.filter { !it.archived }
        val oldCut = nowMillis - OLD_DAYS * DAY
        val starred = active.filter { it.pinned }.sortedWith(byName)
        val rest = active.filter { !it.pinned }
        val withTasks = rest.filter { it.openTasks > 0 }
            .sortedWith(if (alpha) byName else compareBy<CustomerSummary> { it.nextDue ?: LocalDate.MAX }.then(byActivity))
        val noTasks = rest.filter { it.openTasks == 0 }
        val old = noTasks.filter { it.openCases == 0 && it.lastActivity < oldCut }.sortedWith(order)
        val oldIds = old.map { it.id }.toSet()
        val others = noTasks.filter { it.id !in oldIds }.sortedWith(order)
        return CustomerSections(
            starred = starred, withTasks = withTasks, others = others, old = old,
            archived = all.filter { it.archived }.sortedWith(order),
        )
    }

    /** متقاضی خالی: بدون پرونده/یادداشت/کار، قدیمی‌تر از یک روز، نه ستاره‌دار، نه بایگانی. */
    fun empties(all: List<CustomerSummary>, nowMillis: Long): List<CustomerSummary> =
        all.filter { it.isEmpty && !it.pinned && !it.archived && nowMillis - it.createdAt > DAY }
}

object Similar {
    /**
     * نخستین جفت متقاضی با واژه‌های نام یکسان (بی‌توجه به آقای/خانم/دکتر…)؛
     * جفت‌هایی که کاربر گفته «جدا هستند» رد می‌شوند. جفت: (a, b) با a < b.
     */
    fun firstPair(customers: List<Pair<Long, String>>, notSimilar: Set<Pair<Long, Long>>): Pair<Long, Long>? {
        val groups = customers.sortedBy { it.first }
            .map { it.first to Names.tokens(it.second) }
            .filter { it.second.isNotEmpty() }
            .groupBy({ it.second }, { it.first })
        for (ids in groups.values) {
            for (i in ids.indices) for (j in i + 1 until ids.size) {
                val pair = ids[i] to ids[j]
                if (pair !in notSimilar) return pair
            }
        }
        return null
    }
}
