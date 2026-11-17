package app.pocketos.board.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HomeTest {
    private val today = LocalDate.of(2026, 11, 15)

    private fun case(
        id: Long, status: CaseStatus, due: LocalDate? = null, idle: Int = 0, title: String = "پرونده $id",
    ) = CaseInfo(id, title, 1, "الف", status, due, idle)

    private fun reasons(c: CaseInfo) = Attention.list(listOf(c), today).singleOrNull()?.reasons?.map { it.first }

    @Test
    fun deadlineReasons() {
        assertEquals(listOf(Reason.OVER), reasons(case(1, CaseStatus.DOING, today.minusDays(2))))
        assertEquals("مهلت ۲ روز پیش بود", Attention.list(listOf(case(1, CaseStatus.DOING, today.minusDays(2))), today)[0].reasons[0].second)
        assertEquals("مهلت: امروز", Attention.list(listOf(case(1, CaseStatus.DOING, today)), today)[0].reasons[0].second)
        assertEquals("مهلت: فردا", Attention.list(listOf(case(1, CaseStatus.DOING, today.plusDays(1))), today)[0].reasons[0].second)
        assertEquals("مهلت: ۳ روز دیگر", Attention.list(listOf(case(1, CaseStatus.DOING, today.plusDays(3))), today)[0].reasons[0].second)
        assertNull(reasons(case(1, CaseStatus.DOING, today.plusDays(4)))) // دورتر از ۳ روز
    }

    @Test
    fun idleThresholdsAtTheBoundary() {
        // در حال انجام: ۵
        assertNull(reasons(case(1, CaseStatus.DOING, idle = 4)))
        assertEquals(listOf(Reason.STALE), reasons(case(1, CaseStatus.DOING, idle = 5)))
        assertEquals("۶ روز است سر نزده‌ای", Attention.list(listOf(case(1, CaseStatus.DOING, idle = 6)), today)[0].reasons[0].second)
        // منتظر جواب: ۱۴
        assertNull(reasons(case(1, CaseStatus.WAITING, idle = 13)))
        assertEquals(listOf(Reason.FOLLOW), reasons(case(1, CaseStatus.WAITING, idle = 14)))
        // جدید: ۲
        assertNull(reasons(case(1, CaseStatus.NEW, idle = 1)))
        assertEquals(listOf(Reason.NEW), reasons(case(1, CaseStatus.NEW, idle = 2)))
        assertEquals("هنوز نگاه نشده", Attention.list(listOf(case(1, CaseStatus.NEW, idle = 3)), today)[0].reasons[0].second)
    }

    @Test
    fun closedCasesNeverNeedAttention() {
        assertNull(reasons(case(1, CaseStatus.FINAL, today.minusDays(9), 99)))
        assertNull(reasons(case(1, CaseStatus.ARCHIVE, today.minusDays(9), 99)))
    }

    @Test
    fun keepOnlyWhenNotAboutDeadline() {
        assertFalse(Attention.list(listOf(case(1, CaseStatus.DOING, today.minusDays(1), 9)), today)[0].keepOk)
        assertFalse(Attention.list(listOf(case(1, CaseStatus.NEW, today.plusDays(1), 9)), today)[0].keepOk)
        assertTrue(Attention.list(listOf(case(1, CaseStatus.DOING, idle = 9)), today)[0].keepOk)
        assertTrue(Attention.list(listOf(case(1, CaseStatus.NEW, idle = 9)), today)[0].keepOk)
    }

    @Test
    fun orderedByRankThenDue() {
        val list = Attention.list(
            listOf(
                case(1, CaseStatus.NEW, idle = 5), // rank 4
                case(2, CaseStatus.DOING, idle = 9), // rank 2
                case(3, CaseStatus.DOING, today.plusDays(2)), // rank 1
                case(4, CaseStatus.WAITING, today.minusDays(1)), // rank 0
            ),
            today,
        )
        assertEquals(listOf(4L, 3L, 2L, 1L), list.map { it.case.id })
    }

    @Test
    fun capsAtSixty() {
        val many = (1L..80L).map { case(it, CaseStatus.NEW, idle = 9) }
        assertEquals(60, Attention.list(many, today).size)
    }

    @Test
    fun taskNotes() {
        assertEquals("از دیروز مانده", HomeLogic.taskNote(today.minusDays(1), today))
        assertEquals("از ۴ روز پیش مانده", HomeLogic.taskNote(today.minusDays(4), today))
        assertEquals("امروز", HomeLogic.taskNote(today, today))
        assertEquals("فردا", HomeLogic.taskNote(today.plusDays(1), today))
    }

    private fun entry(key: String, cust: Long?, name: String? = "م$cust") =
        HomeEntry(key, key.removePrefix("t").toLongOrNull(), null, key, cust, name, "", false, 0)

    @Test
    fun groupsKeepFirstAppearanceOrder() {
        val g = HomeLogic.group(listOf(entry("t1", 2), entry("t2", 1), entry("t3", 2), entry("t4", null, null)))
        assertEquals(listOf<Long?>(2, 1, null), g.map { it.customerId })
        assertEquals(listOf("t1", "t3"), g[0].items.map { it.key })
    }

    @Test
    fun homeLimitsFourCustomersAndThreeItemsEach() {
        val entries = buildList {
            for (c in 1L..6L) for (i in 1..4) add(entry("t${c * 10 + i}", c))
        } // ۶ متقاضی × ۴ مورد = ۲۴
        val (shown, extra) = HomeLogic.limit(HomeLogic.group(entries))
        assertEquals(4, shown.size)
        assertTrue(shown.all { it.items.size == 3 })
        assertEquals(12, shown.sumOf { it.items.size })
        assertEquals(12, extra)
    }

    @Test
    fun noExtraWhenEverythingFits() {
        val (shown, extra) = HomeLogic.limit(HomeLogic.group(listOf(entry("t1", 1), entry("t2", 1), entry("t3", 2))))
        assertEquals(2, shown.size)
        assertEquals(0, extra)
    }

    @Test
    fun greetingByHour() {
        assertEquals("صبح بخیر", HomeLogic.greeting(8))
        assertEquals("روز بخیر", HomeLogic.greeting(12))
        assertEquals("روز بخیر", HomeLogic.greeting(16))
        assertEquals("عصر بخیر", HomeLogic.greeting(17))
        assertEquals("عصر بخیر", HomeLogic.greeting(20))
        assertEquals("شب بخیر", HomeLogic.greeting(21))
    }

    @Test
    fun eveningSuggestionAndShift() {
        assertFalse(HomeLogic.eveningVisible(18, 19, null, today))
        assertTrue(HomeLogic.eveningVisible(19, 19, null, today))
        assertFalse(HomeLogic.eveningVisible(20, 19, today.toString(), today)) // امروز بسته شده
        assertTrue(HomeLogic.eveningVisible(20, 19, today.minusDays(1).toString(), today))
        assertFalse(HomeLogic.eveningVisible(20, 0, null, today)) // خاموش
        assertEquals(today.plusDays(1), HomeLogic.shifted("tomorrow", today))
        assertEquals(today.plusDays(2), HomeLogic.shifted("after", today))
        assertEquals(today.plusDays(7), HomeLogic.shifted("week", today))
        assertNull(HomeLogic.shifted("drop", today))
    }
}
