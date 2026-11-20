package app.pocketos.board.domain

import java.time.LocalDate

/** کدام برچسب‌ها کنار کار در ویجت نوشته شوند. */
enum class TagMode(val code: String) {
    NONE("none"), SELECTED("selected"), ALL("all");

    companion object {
        fun of(code: String?): TagMode = entries.firstOrNull { it.code == code } ?: NONE
    }
}

/** تنظیمات ویجت «امروز» (روی خود گوشی؛ برای همهٔ ویجت‌ها یکی). */
data class WidgetPrefs(
    val showCustomer: Boolean = true,
    val count: Int = 6,
    /** ۰ تا ۱۰۰ درصدِ مات‌بودن پس‌زمینه؛ ۱۰۰ همان ظاهر پیش‌فرض است. */
    val opacity: Int = 100,
    val tagMode: TagMode = TagMode.NONE,
    val tags: Set<String> = emptySet(),
) {
    companion object {
        val COUNTS = listOf(3, 6, 10)
        fun clampCount(n: Int): Int = if (n in COUNTS) n else 6
        fun clampOpacity(n: Int): Int = n.coerceIn(0, 100)
    }
}

object WidgetLogic {
    /** «جمعه ۲۹ آبان» */
    fun dateLine(d: LocalDate): String {
        val j = Jalali.toJalali(d)
        return Digits.fa("${Jalali.WEEKDAYS[d.dayOfWeek.value - 1]} ${j.day} ${Jalali.MONTHS[j.month - 1]}")
    }

    /** «۳ کار دارید · ۱ مانده» یا «امروز کاری ندارید». */
    fun countLine(total: Int, late: Int): String =
        if (total == 0) "امروز کاری ندارید"
        else Digits.fa("$total کار دارید" + if (late > 0) " · $late مانده" else "")

    /** برچسب‌هایی که طبق تنظیمات کنار این کار نوشته می‌شوند (به ترتیب متن). */
    fun shownTags(text: String?, p: WidgetPrefs): List<String> = when (p.tagMode) {
        TagMode.NONE -> emptyList()
        TagMode.ALL -> Tags.tagsIn(text)
        TagMode.SELECTED -> Tags.tagsIn(text).filter { it in p.tags }
    }

    /** عنوان نمایشی: بدون «#…»، و برچسب‌های مجاز در پایان. */
    fun title(text: String?, p: WidgetPrefs): String {
        val base = Tags.strip(text)
        val tags = shownTags(text, p).joinToString(" ") { "#$it" }
        return if (tags.isEmpty()) base else "$base $tags"
    }

    fun encodeTags(tags: Set<String>): String = tags.sorted().joinToString(",")

    fun decodeTags(s: String?): Set<String> = s.orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}
