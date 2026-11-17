package app.pocketos.board.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.repo.CalendarRepository
import app.pocketos.board.data.repo.CaseRepository
import app.pocketos.board.data.repo.CustomerRepository
import app.pocketos.board.data.repo.ItemRepository
import app.pocketos.board.data.repo.Target
import app.pocketos.board.data.repo.TaskRepository
import app.pocketos.board.domain.CalRange
import app.pocketos.board.domain.CalView
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CalendarRepositoryTest {
    private lateinit var db: BoardDatabase
    private val today = LocalDate.of(2026, 11, 15)
    private val clock = MutableClock.at(today)
    private lateinit var customers: CustomerRepository
    private lateinit var cases: CaseRepository
    private lateinit var tasks: TaskRepository
    private lateinit var cal: CalendarRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, BoardDatabase::class.java).allowMainThreadQueries().build()
        customers = CustomerRepository(db, clock)
        cases = CaseRepository(db, clock)
        tasks = TaskRepository(db, clock, ItemRepository(db, clock, FileStore(ctx)))
        cal = CalendarRepository(db)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun range(from: LocalDate, to: LocalDate, tag: String? = null) = cal.observeRange(from, to, tag).first()

    @Test
    fun rangeIncludesOpenDoneAndCaseDeadlinesButNotDeleted() = runBlocking {
        val c = customers.create("آقای رنجبر")
        val k = cases.create(c, "افراز زمین", today.plusDays(2))
        val open = tasks.create(Target(c, k), "تحویل نقشه", today)
        val done = tasks.create(Target(c), "تماس", today)
        tasks.done(done)
        val gone = tasks.create(Target(c), "حذف‌شده", today)
        tasks.delete(gone)
        tasks.create(Target(c), "بیرون بازه", today.plusDays(30))

        val r = range(today, today.plusDays(7))
        val titles = r.entries.map { it.item.title }
        assertTrue("تحویل نقشه" in titles)
        assertTrue("تماس" in titles)
        assertTrue("مهلت: افراز زمین" in titles)
        assertFalse("حذف‌شده" in titles)
        assertFalse("بیرون بازه" in titles)
        assertTrue(r.entries.first { it.item.taskId == done }.item.done)
        assertEquals("افراز زمین", r.entries.first { it.item.taskId == open }.item.caseTitle)
        assertEquals("آقای رنجبر", r.entries.first { it.item.taskId == open }.item.customerName)
    }

    @Test
    fun deletedCustomerHidesItsTasks() = runBlocking {
        val c = customers.create("الف")
        tasks.create(Target(c), "کار", today)
        customers.delete(c)
        assertTrue(range(today, today).entries.isEmpty())
    }

    @Test
    fun tagFilterIsExactAndHidesCaseDeadlines() = runBlocking {
        val c = customers.create("الف")
        cases.create(c, "پرونده", today)
        tasks.create(Target(c), "الف #اداره", today)
        tasks.create(Target(c), "ب #ادارهکل", today)
        tasks.create(Target(c), "ج", today)
        val r = range(today, today, "اداره")
        assertEquals(listOf("الف #اداره"), r.entries.map { it.item.title })
    }

    @Test
    fun overdueHasOnlyOpenPastTasksAndPastCaseDeadlines() = runBlocking {
        val c = customers.create("الف")
        cases.create(c, "گذشته", today.minusDays(2))
        tasks.create(Target(c), "باز و گذشته", today.minusDays(3))
        val d = tasks.create(Target(c), "انجام‌شده و گذشته", today.minusDays(3))
        tasks.done(d)
        tasks.create(Target(c), "امروز", today)
        val od = cal.observeOverdue(today, null).first()
        assertEquals(listOf("باز و گذشته", "مهلت: گذشته"), od.map { it.item.title })
    }

    @Test
    fun monthRangeCoversTheWholeGrid() = runBlocking {
        val c = customers.create("الف")
        val (from, to) = CalRange.of(CalView.MONTH, today)
        tasks.create(Target(c), "اول شبکه", from)
        tasks.create(Target(c), "آخر شبکه", to)
        tasks.create(Target(c), "پیش از شبکه", from.minusDays(1))
        val titles = range(from, to).entries.map { it.item.title }
        assertEquals(listOf("اول شبکه", "آخر شبکه"), titles)
    }

    @Test
    fun searchByTitleCustomerCaseAndTagSplitsUpcomingAndPast() = runBlocking {
        val c = customers.create("آقای رنجبر")
        val k = cases.create(c, "افراز زمین", today.plusDays(5))
        tasks.create(Target(c, k), "تحویل نقشه #اداره", today.plusDays(1))
        val old = tasks.create(Target(c), "استعلام #اداره", today.minusDays(4))
        tasks.done(old)
        tasks.create(Target(c), "بی‌ربط", today.plusDays(2))

        // نام متقاضی: همهٔ کارهایش + مهلت پرونده
        val byCustomer = cal.search("رنجبر", today)
        assertEquals(3 + 1 - 0, byCustomer.upcoming.size + byCustomer.past.size)
        // پروندهٔ مهلت‌دار
        assertTrue(cal.search("افراز", today).upcoming.any { it.item.isCaseDue })
        // برچسب دقیق
        val tag = cal.search("#اداره", today)
        assertEquals(listOf("تحویل نقشه #اداره"), tag.upcoming.map { it.item.title })
        assertEquals(listOf("استعلام #اداره"), tag.past.map { it.item.title })
        // چند واژه: همه باید باشند
        assertEquals(1, cal.search("تحویل رنجبر", today).upcoming.size)
        assertTrue(cal.search("", today).upcoming.isEmpty())
        // کار باز و گذشته در «پیش رو» می‌ماند
        tasks.create(Target(c), "عقب‌افتاده", today.minusDays(9))
        assertTrue(cal.search("عقب", today).upcoming.isNotEmpty())
    }
}
