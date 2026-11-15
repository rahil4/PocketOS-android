package app.pocketos.board.domain

import java.time.DateTimeException
import java.time.LocalDate
import java.util.regex.Matcher
import java.util.regex.Pattern

/** تشخیص «روز» و «تکرار» در متن فارسی؛ بازنویسی `when.py` وب‌اپ. */
object When {
    // به ترتیب java.time.DayOfWeek-1: دوشنبه=۰ ... یکشنبه=۶
    private val WD = mapOf(
        "شنبه" to 5, "یکشنبه" to 6, "یک شنبه" to 6, "دوشنبه" to 0, "دو شنبه" to 0, "سه شنبه" to 1,
        "چهارشنبه" to 2, "چهار شنبه" to 2, "پنجشنبه" to 3, "پنج شنبه" to 3, "جمعه" to 4,
    )
    private val MONTHS: Map<String, Int> = Jalali.MONTHS.mapIndexed { i, m -> m.replace('‌', ' ') to i + 1 }.toMap()

    private fun alt(words: Collection<String>) =
        words.sortedByDescending { it.length }.joinToString("|") { Pattern.quote(it) }

    private const val REL = "امروز|فردا|پس ?فردا|هفته(?: ?ی)? بعد|\\+\\d{1,3}"
    private val PHRASE = "(?:روز )?(?<wd>" + alt(WD.keys) + ")" +
        "|(?<rel>" + REL + ")" +
        "|(?<num>\\d{4}[/\\-.]\\d{1,2}[/\\-.]\\d{1,2})" +
        "|(?<dm>\\d{1,2}) (?<mon>" + alt(MONTHS.keys) + ")(?: (?<yr>\\d{4}))?"
    private val START = Pattern.compile("^(?:$PHRASE)(?: |\$)")
    private val END = Pattern.compile("(?:^| )(?:$PHRASE)\$")

    private fun weekdayIndex(d: LocalDate) = d.dayOfWeek.value - 1

    private fun resolve(m: Matcher, today: LocalDate): LocalDate? {
        try {
            m.group("wd")?.let { w ->
                var delta = Math.floorMod(WD.getValue(w) - weekdayIndex(today), 7)
                if (delta == 0) delta = 7
                return today.plusDays(delta.toLong())
            }
            m.group("rel")?.let { raw ->
                val r = raw.replace(" ", "")
                return when {
                    r == "امروز" -> today
                    r == "فردا" -> today.plusDays(1)
                    r == "پسفردا" -> today.plusDays(2)
                    r.startsWith("هفته") -> today.plusDays(7)
                    else -> today.plusDays(r.substring(1).toLong())
                }
            }
            m.group("num")?.let { raw ->
                val (y, mo, d) = raw.split('/', '-', '.').map { it.toInt() }
                return if (y >= 1700) LocalDate.of(y, mo, d) else Jalali.fromJalali(y, mo, d)
            }
            m.group("dm")?.let { dd ->
                val jy = Jalali.toJalali(today).year
                val mo = MONTHS.getValue(m.group("mon"))
                val d = dd.toInt()
                m.group("yr")?.let { return Jalali.fromJalali(it.toInt(), mo, d) }
                val res = Jalali.fromJalaliOrNull(jy, mo, d) ?: return null
                return if (res < today) Jalali.fromJalali(jy + 1, mo, d) else res
            }
        } catch (_: IllegalArgumentException) {
            return null
        } catch (_: DateTimeException) {
            return null
        }
        return null
    }

    /** (تاریخ، متن بدون عبارت روز) یا null. عبارت روز اول یا آخر متن می‌آید. */
    fun parse(text: String?, today: LocalDate): Pair<LocalDate, String>? {
        val t = text.orEmpty().trim()
        if (t.isEmpty()) return null
        val s = Normalize.aligned(t) // هم‌طولِ t؛ عنوان از متن اصلی بریده می‌شود تا نیم‌فاصله و ارقام نماند
        for (atStart in listOf(true, false)) {
            val m = (if (atStart) START else END).matcher(s)
            if (m.find()) {
                val d = resolve(m, today) ?: continue
                return d to (if (atStart) t.substring(m.end()) else t.substring(0, m.start())).trim()
            }
        }
        return null
    }

    // ── تکرار: «هر روز»، «هر هفته»، «هر ماه»، «هر ۳ روز»، «هر دوشنبه» ──
    private val REP = "هر (?<kind>روز|هفته|ماه)|هر (?<n>\\d{1,3}) روز|هر (?:روز )?(?<rwd>" + alt(WD.keys) + ")"
    private val REP_START = Pattern.compile("^(?:$REP)(?: |\$)")
    private val REP_END = Pattern.compile("(?:^| )(?:$REP)\$")
    private val REPEAT_LABELS = mapOf("daily" to "هر روز", "weekly" to "هر هفته", "monthly" to "هر ماه")

    fun repeatLabel(code: String?): String = when {
        code == null -> ""
        code.startsWith("every:") -> Digits.fa("هر ${code.substring(6)} روز")
        else -> REPEAT_LABELS[code].orEmpty()
    }

    fun validRepeat(code: String?): Boolean =
        code != null && (code in REPEAT_LABELS || (code.startsWith("every:") && code.substring(6).toIntOrNull() in 2..365))

    fun addMonths(d: LocalDate, n: Int): LocalDate {
        val j = Jalali.toJalali(d)
        val k = j.month - 1 + n
        val jy = j.year + Math.floorDiv(k, 12)
        val jm = Math.floorMod(k, 12) + 1
        return Jalali.fromJalali(jy, jm, minOf(j.day, Jalali.monthLength(jy, jm)))
    }

    /** نوبت بعدیِ کار تکراری: نخستین روزِ برنامهٔ ثابت که بعد از امروز است. */
    fun nextDue(due: LocalDate, code: String, today: LocalDate): LocalDate? = when {
        code == "daily" -> today.plusDays(1)
        code == "weekly" || code.startsWith("every:") -> {
            val step = if (code == "weekly") 7L else (code.substring(6).toLongOrNull() ?: 0L)
            if (step <= 0) null else {
                var d = due
                while (d <= today) d = d.plusDays(step)
                d
            }
        }
        code == "monthly" -> {
            var k = 1
            while (addMonths(due, k) <= today) k++
            addMonths(due, k)
        }
        else -> null
    }

    /** (کد تکرار، اولین روز، عنوان) یا null؛ روز شروع هم می‌تواند بیاید: «هر هفته سه‌شنبه …». */
    fun parseRepeat(text: String?, today: LocalDate): Triple<String, LocalDate, String>? {
        val t = text.orEmpty().trim()
        if (t.isEmpty()) return null
        val s = Normalize.aligned(t)
        for (atStart in listOf(true, false)) {
            val m = (if (atStart) REP_START else REP_END).matcher(s)
            if (!m.find()) continue
            var rest = (if (atStart) t.substring(m.end()) else t.substring(0, m.start())).trim()
            if (rest.isEmpty()) return null
            val code: String
            var first: LocalDate
            val rwd = m.group("rwd")
            val n = m.group("n")
            if (rwd != null) {
                code = "weekly"
                var delta = Math.floorMod(WD.getValue(rwd) - weekdayIndex(today), 7)
                if (delta == 0) delta = 7
                first = today.plusDays(delta.toLong())
            } else if (n != null) {
                val k = n.toInt()
                if (k !in 2..365) return null
                code = "every:$k"
                first = today.plusDays(k.toLong())
            } else {
                code = when (m.group("kind")) { "روز" -> "daily"; "هفته" -> "weekly"; else -> "monthly" }
                first = when (code) {
                    "daily" -> today
                    "weekly" -> today.plusDays(7)
                    else -> addMonths(today, 1)
                }
            }
            parse(rest, today)?.let { (d, r) ->
                if (r.isEmpty()) return null
                first = d
                rest = r
            }
            return Triple(code, first, rest)
        }
        return null
    }

    /** تحلیل متنِ ثبت: روز، تکرار و عنوان؛ null اگر روزی در متن نیست. */
    data class Capture(val title: String, val due: LocalDate, val repeat: String?)

    fun analyze(text: String?, today: LocalDate): Capture? {
        parseRepeat(text, today)?.let { (code, d, rest) -> return Capture(rest, d, code) }
        parse(text, today)?.let { (d, rest) -> if (rest.isNotEmpty()) return Capture(rest, d, null) }
        return null
    }
}
