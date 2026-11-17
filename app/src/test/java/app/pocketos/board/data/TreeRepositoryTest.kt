package app.pocketos.board.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.repo.CaseRepository
import app.pocketos.board.data.repo.CustomerRepository
import app.pocketos.board.data.repo.ItemRepository
import app.pocketos.board.data.repo.Target
import app.pocketos.board.data.repo.TaskRepository
import app.pocketos.board.data.repo.TreeRepository
import app.pocketos.board.domain.CaseStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TreeRepositoryTest {
    private lateinit var db: BoardDatabase
    private val today = LocalDate.of(2026, 11, 15) // یکشنبه ۲۴ آبان ۱۴۰۵؛ هفته از شنبه ۱۴ آبان
    private val clock = MutableClock.at(today)
    private lateinit var customers: CustomerRepository
    private lateinit var cases: CaseRepository
    private lateinit var tasks: TaskRepository
    private lateinit var tree: TreeRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, BoardDatabase::class.java).allowMainThreadQueries().build()
        customers = CustomerRepository(db, clock)
        cases = CaseRepository(db, clock)
        tasks = TaskRepository(db, clock, ItemRepository(db, clock, FileStore(ctx)))
        tree = TreeRepository(db, clock)
    }

    @After
    fun tearDown() = db.close()

    private fun at(d: LocalDate) { clock.now = MutableClock.at(d).now }

    @Test
    fun countsLeavesBudsAndFruitsPerBranch() = runBlocking {
        val a = customers.create("آقای رنجبر")
        val b = customers.create("خانم کریمی")
        val caseA = cases.create(a, "افراز زمین")

        // دو کار همین هفته و یکی هفتهٔ قبل (شنبه ۷ نوامبر، همان روزِ شروع هفتهٔ قبل)
        at(LocalDate.of(2026, 11, 7)); tasks.done(tasks.create(Target(a, null), "قدیمی", null))
        at(today)
        tasks.done(tasks.create(Target(a, caseA), "ارسال نقشه", null))
        tasks.done(tasks.create(Target(a, null), "تماس", null))
        tasks.create(Target(a, null), "مهلت‌دار این هفته", LocalDate.of(2026, 11, 18))
        tasks.create(Target(b, null), "بدون مهلت", null)
        val gone = tasks.create(Target(a, null), "حذف‌شده", null).also { tasks.done(it); tasks.delete(it) }
        assertEquals(true, gone > 0)
        cases.setStatus(caseA, CaseStatus.FINAL)

        val s = tree.observe(today).first()
        val ra = s.branches.first { it.id == a }
        assertEquals(3, ra.done)
        assertEquals(2, ra.doneWeek)
        assertEquals(1, ra.open)
        assertEquals(1, ra.fruits)
        val rb = s.branches.first { it.id == b }
        assertEquals(0, rb.done)
        assertEquals(1, rb.open)
        assertEquals(1, s.fruits)
        assertEquals(3, s.total)

        assertEquals(2, s.week.cur)
        assertEquals(1, s.week.prev) // هفتهٔ قبل تا همین روزِ هفته (شنبه و یکشنبه)
        assertEquals(3, s.weekPlanned) // ۲ انجام‌شده + ۱ مانده با مهلت در این هفته
        assertEquals(3, s.month.cur)
        assertEquals(0, s.month.prev)
        assertEquals(3, s.season.cur)
        assertEquals("پاییز", s.seasonName)
    }

    @Test
    fun undoingDoneRemovesTheLeafAndEmptyTreeIsCalm() = runBlocking {
        val empty = tree.observe(today).first()
        assertEquals(0, empty.total)
        assertEquals(0, empty.weekPlanned)
        assertEquals(emptyList<Long>(), empty.branches.map { it.id })

        val a = customers.create("الف")
        val t = tasks.create(Target(a, null), "کار", null)
        tasks.done(t)
        assertEquals(1, tree.observe(today).first().total)
        tasks.undone(t)
        val s = tree.observe(today).first()
        assertEquals(0, s.total)
        assertEquals(0, s.week.cur)
        assertEquals(1, s.branches.single().open)
    }

    @Test
    fun deletedCustomerHasNoBranchAndArchivedKeepsIt() = runBlocking {
        val a = customers.create("الف")
        val b = customers.create("ب")
        customers.setArchived(a, true)
        customers.delete(b)
        val s = tree.observe(today).first()
        assertEquals(listOf(a), s.branches.map { it.id })
        assertEquals(true, s.branches.single().archivedAt != null)
        assertNull(s.branches.firstOrNull { it.id == b })
    }
}
