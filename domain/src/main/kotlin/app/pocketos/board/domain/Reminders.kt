package app.pocketos.board.domain

import java.time.LocalDate

/** تنظیمات اپ (کلیدها و پیش‌فرض‌ها مثل وب‌اپ). */
data class AppSettings(
    /** auto | dark | light */
    val theme: String = "auto",
    /** sm | md | lg | xl */
    val fsize: String = "md",
    /** ساعت «بستن روز»؛ ۰ = خاموش. */
    val eveningHour: Int = 19,
    val morningOn: Boolean = true,
    val morningHour: Int = 8,
    val eveningPush: Boolean = false,
    val pushNames: Boolean = true,
) {
    /** ضریب «زوم» کل رابط. */
    val scale: Float
        get() = when (fsize) {
            "sm" -> 0.94f
            "lg" -> 1.12f
            "xl" -> 1.25f
            else -> 1f
        }

    companion object {
        val THEMES = listOf("auto" to "خودکار (مثل گوشی)", "dark" to "تیره", "light" to "روشن")
        val SIZES = listOf("sm" to "کوچک", "md" to "متوسط", "lg" to "بزرگ", "xl" to "خیلی بزرگ")
        val EVENING_HOURS = listOf(0, 17, 18, 19, 20, 21, 22, 23)
        val MORNING_HOURS = (5..12).toList()
    }
}

data class ReminderTask(val title: String, val due: LocalDate, val customer: String?)

/** متن اعلان؛ [route] مقصد لمس (home | evening). */
data class Payload(val title: String, val body: String, val route: String, val tag: String)

/** قاعده و متن اعلان‌های صبحگاهی و عصرانه (همان `run_reminders` و `morning_payload` وب‌اپ). */
object Reminders {
    const val MORNING_WINDOW = 4
    const val EVENING_WINDOW = 3
    const val GENERIC_BODY = "برای دیدن، برنامه را باز کنید"

    fun morningDue(on: Boolean, hour: Int, hourNow: Int, lastMorning: String?, today: LocalDate): Boolean =
        on && lastMorning != today.toString() && hourNow in hour..(hour + MORNING_WINDOW)

    fun eveningDue(push: Boolean, eveningHour: Int, hourNow: Int, lastEvening: String?, closedOn: String?, today: LocalDate): Boolean =
        push && eveningHour in 1..23 && hourNow in eveningHour..(eveningHour + EVENING_WINDOW) &&
            lastEvening != today.toString() && closedOn != today.toString()

    /** null اگر کاری نیست (بدون کار، اعلانی نمی‌رود). [rows]: کارهای باز با روز امروز یا گذشته. */
    fun morning(rows: List<ReminderTask>, today: LocalDate, names: Boolean): Payload? {
        if (rows.isEmpty()) return null
        val nToday = rows.count { it.due == today }
        val nLate = rows.size - nToday
        var title = if (nToday > 0) "امروز $nToday کار داری" else "$nLate کار مانده داری"
        if (nToday > 0 && nLate > 0) title += " · $nLate مانده"
        var body = GENERIC_BODY
        if (names) {
            val groups = LinkedHashMap<String, MutableList<String>>()
            rows.filter { it.due == today }.forEach { groups.getOrPut(it.customer ?: "بدون مشتری") { ArrayList() } += Tags.strip(it.title).ifEmpty { it.title } }
            val lines = groups.entries.take(3).map { (k, v) -> "$k: " + v.take(2).joinToString(" · ") }
            if (lines.isNotEmpty()) body = lines.joinToString("\n")
        }
        return Payload(Digits.fa(title), Digits.fa(body), "home", "morning")
    }

    fun evening(openCount: Int): Payload =
        Payload("روز را ببند و سبک شو", Digits.fa("$openCount کار هنوز باز است؛ یک لمس برای تصمیم."), "evening", "evening")
}
