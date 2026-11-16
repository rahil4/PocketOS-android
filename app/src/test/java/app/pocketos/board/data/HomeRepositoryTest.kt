package app.pocketos.board.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.repo.CalendarRepository
import app.pocketos.board.data.repo.CaseRepository
import app.pocketos.board.data.repo.CustomerRepository
import app.pocketos.board.data.repo.HomeRepository
import app.pocketos.board.data.repo.ItemKind
import app.pocketos.board.data.repo.ItemRepository
import app.pocketos.board.data.repo.SearchRepository
import app.pocketos.board.data.repo.Target
import app.pocketos.board.data.repo.TaskRepository
import app.pocketos.board.domain.CaseStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeRepositoryTest {
    private lateinit var db: BoardDatabase
    private val today = LocalDate.of(2026, 11, 15)
    private val clock = MutableClock.at(today)
    private lateinit var customers: CustomerRepository
    private lateinit var cases: CaseRepository
    private lateinit var items: ItemRepository
    private lateinit var tasks: TaskRepository
    private lateinit var home: HomeRepository
    private lateinit var search: SearchRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, BoardDatabase::class.java).allowMainThreadQueries().build()
        customers = CustomerRepository(db, clock)
        cases = CaseRepository(db, clock)
        items = ItemRepository(db, clock, FileStore(ctx))
        tasks = TaskRepository(db, clock, items)
        home = HomeRepository(db, clock)
        search = SearchRepository(db, CalendarRepository(db), clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun todayData(oldLimit: Int = 0) = home.observeToday(oldLimit).first()

    @Test
    fun todayShowsTodayAndYesterdayAndOnlyCountsOlder() = runBlocking {
        val c = customers.create("آقای رنجبر")
        val k = cases.create(c, "افراز زمین")
        tasks.create(Target(c, k), "امروزی", today)
        tasks.create(Target(c), "دیروزی", today.minusDays(1))
        tasks.create(Target(c), "قدیمی ۳ روز", today.minusDays(3))
        tasks.create(Target(c), "قدیمی ۹ روز", today.minusDays(9))
        tasks.create(Target(c), "فردایی", today.plusDays(1))
        val done = tasks.create(Target(c), "انجام‌شده", today)
        tasks.done(done)

        val d = todayData()
        assertEquals(setOf("امروزی", "دیروزی"), d.rows.filter { it.entry.isTask }.map { it.entry.title }.toSet())
        assertEquals(2, d.oldCount)
        assertEquals(1, d.tomorrowCount)
        // زیرنویس: پرونده · یادداشت روز، و کهربایی برای مانده
        val yesterday = d.rows.first { it.entry.title == "دیروزی" }.entry
        assertEquals("از دیروز مانده", yesterday.sub)
        assertTrue(yesterday.amber)
        val todayRow = d.rows.first { it.entry.title == "امروزی" }.entry
        assertEquals("افراز زمین · امروز", todayRow.sub)
        assertFalse(todayRow.amber)

        // با oldLimit قدیمی‌ها هم می‌آیند
        val all = todayData(oldLimit = 100)
        assertEquals(4, all.rows.count { it.entry.isTask })
        assertEquals(2, all.oldCount)
    }

    @Test
    fun attentionCasesAppearWithCalmReasons() = runBlocking {
        val c = customers.create("الف")
        val k = cases.create(c, "پروندهٔ جدید") // «جدید»
        val w = cases.create(c, "منتظر")
        cases.setStatus(w, CaseStatus.WAITING)
        clock.now += 3L * 86_400_000 // ۳ روز بی‌فعالیت: «جدید» توجه می‌خواهد، «منتظر» هنوز نه
        val d = home.observeToday().first()
        val caseRows = d.rows.filter { !it.entry.isTask }
        assertEquals(listOf(k), caseRows.map { it.entry.caseId })
        assertEquals("هنوز نگاه نشده", caseRows[0].entry.sub)
        assertTrue(caseRows[0].entry.keepOk)

        clock.now += 12L * 86_400_000 // ۱۵ روز: «منتظر» هم پیگیری می‌خواهد
        val later = home.observeToday().first().rows.filter { !it.entry.isTask }
        assertTrue(later.any { it.entry.caseId == w && it.entry.sub.contains("منتظر جواب") })
    }

    @Test
    fun caseDeadlineMakesItOverAndNotKeepable() = runBlocking {
        val c = customers.create("الف")
        val k = cases.create(c, "پرونده", today.minusDays(2))
        val row = home.observeToday().first().rows.single { it.entry.caseId == k }.entry
        assertEquals("مهلت ۲ روز پیش بود", row.sub.substringBefore(" · "))
        assertTrue(row.amber)
        assertFalse(row.keepOk)
    }

    @Test
    fun seenActionsOnCases() = runBlocking {
        val c = customers.create("الف")
        val k = cases.create(c, "پرونده")
        clock.now += 86_400_000
        cases.markSeen(k) // جدید ← در حال انجام
        assertEquals("doing", db.caseDao().get(k)!!.status)

        val prev = cases.markFinal(k)
        assertEquals(CaseStatus.DOING, prev)
        assertEquals("final", db.caseDao().get(k)!!.status)
        cases.setStatus(k, prev!!) // «بازگردانی»
        assertEquals("doing", db.caseDao().get(k)!!.status)

        val n = cases.create(c, "تازه")
        cases.markOpened(n)
        assertEquals("doing", db.caseDao().get(n)!!.status)
        cases.setStatus(n, CaseStatus.WAITING)
        cases.markOpened(n) // فقط «جدید» عوض می‌شود
        assertEquals("waiting", db.caseDao().get(n)!!.status)
    }

    @Test
    fun bulkMoveOldToTomorrowAndDrop() = runBlocking {
        val c = customers.create("الف")
        val old1 = tasks.create(Target(c), "قدیمی", today.minusDays(5))
        val old2 = tasks.create(Target(c), "قدیمی‌تر", today.minusDays(2))
        val recent = tasks.create(Target(c), "دیروز", today.minusDays(1))

        assertEquals(2, home.bulkOld(drop = false))
        assertEquals(today.plusDays(1).toString(), db.taskDao().get(old1)!!.dueDay)
        assertEquals(today.plusDays(1).toString(), db.taskDao().get(old2)!!.dueDay)
        assertEquals(today.minusDays(1).toString(), db.taskDao().get(recent)!!.dueDay)

        val more = tasks.create(Target(c), "باز هم قدیمی", today.minusDays(3))
        assertEquals(1, home.bulkOld(drop = true))
        assertTrue(db.taskDao().get(more)!!.deletedAt != null)
        assertNull(db.taskDao().get(recent)!!.deletedAt)
    }

    @Test
    fun closingTheDayMovesOrDropsEachTask() = runBlocking {
        val c = customers.create("الف")
        val a = tasks.create(Target(c), "الف", today)
        val b = tasks.create(Target(c), "ب", today.minusDays(1))
        val d = tasks.create(Target(c), "د", today)
        val e = tasks.create(Target(c), "ه", today)
        val doneTask = tasks.create(Target(c), "انجام‌شده", today)
        tasks.done(doneTask)
        items.add(Target(c), ItemKind.TEXT, "یادداشت امروز")

        val ev = home.loadEvening()
        assertEquals(4, ev.tasks.size)
        assertEquals(1, ev.made)
        assertEquals(1, ev.done)

        val tomorrowN = home.closeDay(mapOf(a to "tomorrow", b to "after", d to "week", e to "drop"))
        assertEquals(today.plusDays(1).toString(), db.taskDao().get(a)!!.dueDay)
        assertEquals(today.plusDays(2).toString(), db.taskDao().get(b)!!.dueDay)
        assertEquals(today.plusDays(7).toString(), db.taskDao().get(d)!!.dueDay)
        assertTrue(db.taskDao().get(e)!!.deletedAt != null)
        assertEquals(1, tomorrowN)
    }

    @Test
    fun globalSearchFindsCustomersCasesTasksAndNotes() = runBlocking {
        val c = customers.create("آقای رنجبر", "09121234567")
        val k = cases.create(c, "افراز زمین")
        tasks.create(Target(c, k), "تحویل نقشه #اداره", today.plusDays(1))
        items.add(Target(c), ItemKind.TEXT, "شمارهٔ سند ۱۲۳۴۵ را فرستاد")
        items.add(Target(c), ItemKind.EVENT, "کار انجام شد: رنجبر") // رویداد در نتیجه نمی‌آید

        val r = search.search("رنجبر")
        assertEquals(1, r.customers.size)
        assertEquals(1, r.tasks.upcoming.size + r.tasks.past.size) // کارِ این مشتری؛ پرونده مهلت ندارد
        assertEquals(0, r.notes.size) // «رنجبر» در متن یادداشت نیست
        assertEquals(1, search.search("افراز").cases.size)
        assertEquals(1, search.search("سند").notes.size)
        assertEquals("آقای رنجبر", search.search("سند").notes[0].customerName)
        assertEquals(1, search.search("0912").customers.size)
        assertEquals(1, search.search("#اداره").tasks.upcoming.size)
        assertEquals(0, search.search("").count)
    }
}
