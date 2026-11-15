package app.pocketos.board.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val DAY = 86_400_000L
private const val NOW = 1_800_000_000_000L

private fun cust(
    id: Long, name: String, pinned: Boolean = false, archived: Boolean = false, cases: Int = 0, tasks: Int = 0,
    due: LocalDate? = null, ago: Long = 0, empty: Boolean = false, phone: String = "",
) = CustomerSummary(
    id, name, phone, pinned, archived, cases, tasks, due, NOW - ago * DAY, empty, NOW - ago * DAY,
    Normalize.key("$name $phone"),
)

class CustomersTest {
    @Test
    fun normalize() {
        assertEquals("علی کریم 12", Normalize.norm("  علي‌ كريم ۱۲ "))
        assertEquals("می رود", Normalize.norm("می‌رود"))
        assertEquals(listOf("آقای", "رنجبر"), Normalize.words("آقای «رنجبر»،"))
    }

    @Test
    fun phone() {
        assertEquals("09121234567", Phone.normalize("+98 912 123 4567"))
        assertEquals("09121234567", Phone.normalize("۹۱۲۱۲۳۴۵۶۷"))
        assertEquals("09121234567", Phone.normalize("0098-912-1234567"))
        assertEquals("02112345678", Phone.normalize("021-12345678"))
        assertEquals("۰۹۱۲ ۱۲۳ ۴۵۶۷", Phone.display("09121234567"))
    }

    @Test
    fun names() {
        assertEquals(setOf("رنجبر"), Names.tokens("آقای رنجبر"))
        assertEquals("ر", Names.avatar("آقای رنجبر"))
        assertEquals("م", Names.avatar("خانم دکتر محمدی"))
        assertEquals("؟", Names.avatar(""))
        assertEquals("علی رنجبر", Names.sortName("دکتر علی رنجبر"))
    }

    @Test
    fun statuses() {
        assertEquals(CaseStatus.DOING, CaseStatus.of("doing"))
        assertEquals(CaseStatus.NEW, CaseStatus.of("???"))
        assertTrue(CaseStatus.WAITING.open)
        assertTrue(!CaseStatus.FINAL.open)
    }

    @Test
    fun relativeTime() {
        assertEquals("امروز", RelativeTime.ago(NOW - 3_600_000, NOW))
        assertEquals("دیروز", RelativeTime.ago(NOW - DAY, NOW))
        assertEquals("۳ روز پیش", RelativeTime.ago(NOW - 3 * DAY, NOW))
        assertEquals("۲ هفته پیش", RelativeTime.ago(NOW - 15 * DAY, NOW))
        assertEquals("۳ ماه پیش", RelativeTime.ago(NOW - 95 * DAY, NOW))
    }

    @Test
    fun dayLabel() {
        val today = LocalDate.of(2026, 11, 15)
        assertEquals("امروز", DayLabel.of(today, today))
        assertEquals("فردا", DayLabel.of(today.plusDays(1), today))
        assertEquals("پس‌فردا", DayLabel.of(today.plusDays(2), today))
        assertEquals("پنجشنبه ۲۸ آبان", DayLabel.of(LocalDate.of(2026, 11, 19), today))
        assertTrue(DayLabel.of(LocalDate.of(2027, 11, 19), today).endsWith("۱۴۰۶"))
    }

    @Test
    fun sections() {
        val d1 = LocalDate.of(2026, 11, 20)
        val d2 = LocalDate.of(2026, 11, 16)
        val all = listOf(
            cust(1, "آقای رنجبر", pinned = true, cases = 2, tasks = 2, due = d1),
            cust(2, "خانم محمدی", tasks = 1, due = d1, ago = 3),
            cust(3, "دکتر کریمی", cases = 1, ago = 14),
            cust(4, "حسینی", ago = 400),
            cust(5, "جعفری", archived = true, ago = 10),
            cust(6, "نوری", tasks = 1, due = d2, ago = 1),
        )
        val s = CustomerList.build(all, "", alpha = false, nowMillis = NOW)
        assertEquals(listOf(1L), s.starred.map { it.id })
        assertEquals(listOf(6L, 2L), s.withTasks.map { it.id }) // نزدیک‌ترین مهلت اول
        assertEquals(listOf(3L), s.others.map { it.id })
        assertEquals(listOf(4L), s.old.map { it.id })
        assertEquals(listOf(5L), s.archived.map { it.id })
        assertNull(s.results)
    }

    @Test
    fun searchIncludesArchived() {
        val all = listOf(cust(1, "آقای رنجبر", phone = "09121234567"), cust(5, "جعفری", archived = true))
        assertEquals(listOf(1L), CustomerList.build(all, "رنجبر", false, NOW).results!!.map { it.id })
        assertEquals(listOf(1L), CustomerList.build(all, "۰۹۱۲", false, NOW).results!!.map { it.id })
        assertEquals(listOf(5L), CustomerList.build(all, "جعفر", false, NOW).results!!.map { it.id })
        assertEquals(emptyList(), CustomerList.build(all, "نامرتبط", false, NOW).results!!)
    }

    @Test
    fun empties() {
        val all = listOf(
            cust(1, "الف", empty = true, ago = 2), cust(2, "ب", empty = true, ago = 0),
            cust(3, "ج", empty = true, ago = 5, pinned = true), cust(4, "د", cases = 1, ago = 5),
        )
        assertEquals(listOf(1L), CustomerList.empties(all, NOW).map { it.id })
    }

    @Test
    fun similar() {
        val cs = listOf(1L to "آقای رنجبر", 2L to "رنجبر", 3L to "خانم محمدی", 4L to "علی رنجبر")
        assertEquals(1L to 2L, Similar.firstPair(cs, emptySet()))
        assertNull(Similar.firstPair(cs, setOf(1L to 2L)))
        assertNull(Similar.firstPair(listOf(1L to "الف", 2L to "ب"), emptySet()))
    }
}
