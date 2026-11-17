package app.pocketos.board.domain

import java.time.LocalDate

/** متقاضی برای تطبیق نام در دستیار. */
data class AssistCustomer(val id: Long, val name: String, val phone: String, val archived: Boolean, val toks: Set<String>) {
    companion object {
        fun of(id: Long, name: String, phone: String = "", archived: Boolean = false) =
            AssistCustomer(id, name, phone, archived, Names.tokens(name))
    }
}

/** قصدی که دستیار از جمله می‌فهمد؛ اجرا با لایهٔ داده است. */
sealed interface AssistIntent {
    data object Help : AssistIntent
    data class TasksDay(val day: LocalDate, val label: String) : AssistIntent
    data class TasksRange(val start: LocalDate, val end: LocalDate, val label: String) : AssistIntent
    data object TasksLate : AssistIntent
    data object WhoHasTasks : AssistIntent
    data object OpenCases : AssistIntent
    data object RecentNotes : AssistIntent
    data class CustomerInfo(val customerId: Long) : AssistIntent
    data class Phone(val customerId: Long) : AssistIntent

    /** مبهم: کاربر یکی را انتخاب می‌کند و [say] با نام کامل دوباره فرستاده می‌شود (`{name}` جایگزین می‌شود). */
    data class Choices(val ids: List<Long>, val say: String, val ask: String? = null) : AssistIntent
    data class AddTask(
        val customerId: Long?, val customer: String?, val title: String, val due: LocalDate, val label: String, val repeat: String?,
    ) : AssistIntent
    data class AddNote(val customerId: Long, val customer: String, val text: String) : AssistIntent
    data class Search(val q: String) : AssistIntent
    data class Tag(val tag: String) : AssistIntent
    data class NoCustomer(val q: String) : AssistIntent
    data object NeedTitle : AssistIntent
    data object Unknown : AssistIntent
}

/**
 * دستیار متنی بدون هوش مصنوعی: جملهٔ فارسی ← «قصد» با قاعده‌های ثابت (بازنویسی `assistant.interpret` وب‌اپ).
 * هیچ‌چیز را حدس نمی‌زند: اگر نفهمد `Search`/`Unknown` برمی‌گرداند و مبهم را با `Choices` می‌پرسد.
 */
object Assistant {
    private val FILLER = setOf(
        "کار", "کارها", "کارهای", "کاری", "چه", "چی", "دارم", "داریم", "هست", "هستند", "را", "رو", "نشان", "بده",
        "بگو", "ببین", "لطفا", "من", "برای", "در", "این", "پیش", "های", "ها",
    )
    private val PREPOSITIONS = setOf("برای", "به", "از", "با")
    private val HONORIFICS = Names.HONORIFICS

    private val HELP = Regex("(سلام|درود|راهنما|کمک|help|مثال|چه کارهایی می ?تونی.*|چه کار.* بلدی)")
    private val NOTE = Regex("^(?:بنویس|یادداشت(?: کن)?|ثبت(?: کن)?)\\s*(?:برای|در|به)?\\s*(.+?)\\s*[:：]\\s*(.+)$", RegexOption.DOT_MATCHES_ALL)
    private val PHONE = Regex("^(?:تلفن|شماره|موبایل|شماره ?ی تلفن)(?: تماس)?\\s+(.+)$")
    private val WHO = Regex("(^| )(کی|کدام|کدوم|چه کسانی|چه کسایی)( |$)")
    private val OPEN_CASES = Regex("پرونده( ?های| ?ی)? باز|پرونده ?ها")
    private val FOR_WHO = Regex("(برای|مال)\\s")
    private val LATE = Regex("عقب ?افتاده|از قبل مانده|مانده ?ها|کارهای مانده")
    private val RECENT = Regex("یادداشت.*(جدید|آخر)|جدیدترین")
    private val THIS_WEEK = Regex("هفته(?: بعد)?")
    private val WS = Regex("\\s+")

    private fun words(s: String?) = Normalize.words(s)

    /** (فهرست، نوع) ← «full»: همهٔ واژه‌های نام در جمله‌اند (بلندترین‌ها)، «part»: دست‌کم یک واژهٔ مشترک. */
    fun matchCustomers(ws: List<String>, customers: List<AssistCustomer>, loose: Boolean = true): Pair<List<AssistCustomer>, String> {
        val wset = ws.toSet()
        val full = customers.filter { it.toks.isNotEmpty() && wset.containsAll(it.toks) }
        if (full.isNotEmpty()) {
            val best = full.maxOf { it.toks.size }
            return full.filter { it.toks.size == best } to "full"
        }
        if (loose) {
            val long = wset.filter { it.length >= 2 }.toSet()
            val part = customers.filter { c -> c.toks.any { it in long } }
            if (part.isNotEmpty()) return part to "part"
        }
        return emptyList<AssistCustomer>() to "none"
    }

    /** واژه‌های نام متقاضی و «برای/آقای/خانم…» کنارشان حذف می‌شود؛ شاخص‌های حذف‌شده برمی‌گردد. */
    private fun dropIndices(ws: List<String>, cust: AssistCustomer): Set<Int> {
        val drop = HashSet<Int>()
        ws.forEachIndexed { i, w ->
            if (w in cust.toks) {
                drop += i
                var j = i - 1
                while (j >= 0 && (ws[j] in HONORIFICS || ws[j] in PREPOSITIONS)) {
                    drop += j
                    j--
                }
            }
        }
        return drop
    }

    /** عنوان از متن اصلی (نیم‌فاصله و ارقام کاربر حفظ می‌شود)؛ نشانه‌ای می‌ماند که دست‌کم یک واژه‌اش حذف نشده. */
    private fun title(rest: String, cust: AssistCustomer?): String {
        val tokens = rest.trim().split(WS).filter { it.isNotEmpty() }.map { it to words(it) }
        val all = tokens.flatMap { it.second }
        val drop = cust?.let { dropIndices(all, it) }.orEmpty()
        var idx = 0
        val kept = ArrayList<String>()
        for ((orig, ws) in tokens) {
            val range = idx until idx + ws.size
            idx += ws.size
            if (ws.isNotEmpty() && range.any { it !in drop }) kept += orig
        }
        return kept.joinToString(" ").trim()
    }

    fun interpret(text: String?, today: LocalDate, customers: List<AssistCustomer>): AssistIntent {
        val raw = text.orEmpty().trim()
        if (raw.isEmpty()) return AssistIntent.Help
        val s = words(raw).joinToString(" ")
        val ws = s.split(' ').filter { it.isNotEmpty() }

        if (HELP.matches(s)) return AssistIntent.Help

        // یادداشت: «بنویس برای رنجبر: شمارهٔ سند ۱۲۳۴۵» (متن از نوشتهٔ اصلی، با ارقام و نیم‌فاصلهٔ کاربر)
        val aligned = Normalize.aligned(raw)
        NOTE.find(aligned)?.let { m ->
            val who = m.groupValues[1]
            val body = raw.substring(m.groups[2]!!.range.first, m.groups[2]!!.range.last + 1).trim()
            val (found, _) = matchCustomers(words(who), customers)
            if (found.size == 1 && body.isNotEmpty()) return AssistIntent.AddNote(found[0].id, found[0].name, body)
            if (found.size > 1) return AssistIntent.Choices(found.take(6).map { it.id }, "بنویس برای {name}: $body")
            return AssistIntent.NoCustomer(who)
        }

        // شمارهٔ تلفن
        PHONE.find(s)?.let { m ->
            val (found, _) = matchCustomers(m.groupValues[1].split(' ').filter { it.isNotEmpty() }, customers)
            if (found.size == 1) return AssistIntent.Phone(found[0].id)
            if (found.size > 1) return AssistIntent.Choices(found.take(6).map { it.id }, "تلفن {name}")
            return AssistIntent.NoCustomer(m.groupValues[1])
        }

        // پرسش‌های کلی
        if (WHO.containsMatchIn(s) && "کار" in s) return AssistIntent.WhoHasTasks
        if (OPEN_CASES.containsMatchIn(s) && !FOR_WHO.containsMatchIn(s)) return AssistIntent.OpenCases
        if (LATE.containsMatchIn(s)) return AssistIntent.TasksLate
        if (RECENT.containsMatchIn(s)) return AssistIntent.RecentNotes

        // کارهای یک روز: «کارهای امروز»، «فردا»، «این هفته»
        val restWs = ws.filter { it !in FILLER }
        if (restWs.isNotEmpty()) {
            val rest = restWs.joinToString(" ")
            if (THIS_WEEK.matches(rest) && "بعد" !in rest) {
                return AssistIntent.TasksRange(today, today.plusDays(6), "این هفته")
            }
            When.parse(rest, today)?.let { (day, left) -> if (left.isEmpty()) return AssistIntent.TasksDay(day, DayLabel.of(day, today)) }
        }

        // ثبت کار با جمله: «فردا برای رنجبر تحویل نقشه»، «هر هفته … »
        var repeat: String? = null
        var due: LocalDate? = null
        var rest = ""
        val rr = When.parseRepeat(raw, today)
        if (rr != null) {
            repeat = rr.first; due = rr.second; rest = rr.third
        } else {
            When.parse(raw, today)?.let { due = it.first; rest = it.second }
        }
        val dueDay = due
        if (dueDay != null && rest.isNotEmpty()) {
            val rw = words(rest)
            var found = matchCustomers(rw, customers, loose = false).first
            if (found.isEmpty()) { // همهٔ واژه‌های باقی‌مانده بخشی از نامِ یک متقاضی‌اند
                val rset = rw.filter { it !in FILLER && it !in HONORIFICS }.toSet()
                if (rset.isNotEmpty()) {
                    val f = customers.filter { it.toks.containsAll(rset) }
                    found = if (f.size == 1) f else emptyList()
                }
            }
            if (found.isEmpty()) { // نام ناقص فقط بعد از «برای/به/آقای/خانم…»
                val cand = ArrayList<AssistCustomer>()
                rw.forEachIndexed { i, w ->
                    if (i > 0 && (rw[i - 1] in HONORIFICS || rw[i - 1] in setOf("برای", "به", "با", "از"))) cand += customers.filter { w in it.toks }
                }
                found = cand.distinctBy { it.id }
            }
            if (found.size > 1) { // مبهم: همان جمله با نام کاملِ هر گزینه دوباره فرستاده می‌شود
                val dateText = raw.replaceFirst(rest, "").trim()
                val shared = found.flatMap { it.toks }.toSet()
                val clean = rw.filter { it !in shared && it !in HONORIFICS && it != "برای" }.joinToString(" ")
                return AssistIntent.Choices(found.take(6).map { it.id }, "$dateText برای {name} $clean".trim(), "کدام متقاضی؟")
            }
            val cust = found.firstOrNull()
            val t = title(rest, cust)
            if (t.isEmpty()) return AssistIntent.NeedTitle
            return AssistIntent.AddTask(cust?.id, cust?.name, t.take(200), dueDay, DayLabel.of(dueDay, today), repeat)
        }

        // برچسب: «#اداره»
        Tags.tagsIn(raw).firstOrNull()?.let { return AssistIntent.Tag(it) }

        // فقط نام متقاضی (حداکثر ۴ واژه)
        val nameWs = ws.filter { it !in HONORIFICS }
        if (nameWs.isNotEmpty() && nameWs.size <= 4) {
            val wset = nameWs.toSet()
            val exact = customers.filter { it.toks == wset }
            val found = if (exact.isNotEmpty()) exact.take(1) else matchCustomers(ws, customers).first
            if (found.size == 1) return AssistIntent.CustomerInfo(found[0].id)
            if (found.size > 1) return AssistIntent.Choices(found.take(6).map { it.id }, "{name}")
        }
        if (s.length >= 2) return AssistIntent.Search(raw.take(100))
        return AssistIntent.Unknown
    }
}
