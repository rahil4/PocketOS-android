package app.pocketos.board.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.db.ItemEntity
import app.pocketos.board.data.db.TaskEntity
import app.pocketos.board.data.repo.CaseRepository
import app.pocketos.board.data.repo.CustomerRepository
import app.pocketos.board.data.repo.TrashRepository
import app.pocketos.board.domain.CaseStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

/** ساعت دستی تا زمان‌ها قابل کنترل باشند. */
private class MutableClock(var now: Long) : Clock() {
    override fun getZone() = ZoneOffset.UTC
    override fun withZone(zone: java.time.ZoneId?) = this
    override fun instant(): Instant = Instant.ofEpochMilli(now)
    override fun millis(): Long = now
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RepositoryTest {
    private lateinit var db: BoardDatabase
    private val clock = MutableClock(1_800_000_000_000L)
    private lateinit var customers: CustomerRepository
    private lateinit var cases: CaseRepository
    private lateinit var trash: TrashRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, BoardDatabase::class.java).allowMainThreadQueries().build()
        customers = CustomerRepository(db, clock)
        cases = CaseRepository(db, clock)
        trash = TrashRepository(db, clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun summaries() = customers.observeSummaries().first()

    @Test
    fun createDedupesByNormalizedNameAndFillsPhone() = runBlocking {
        val a = customers.create("آقای رنجبر", "")
        val b = customers.create("  آقای   رنجبر ", "+98 912 123 4567")
        assertEquals(a, b)
        val list = summaries()
        assertEquals(1, list.size)
        assertEquals("09121234567", list[0].phone)
        assertTrue(list[0].isEmpty)
    }

    @Test
    fun searchTextCoversNameAndPhone() = runBlocking {
        customers.create("علي کریمی", "۰۹۱۲۱۲۳۴۵۶۷")
        val s = summaries().single()
        assertTrue("علی" in s.searchText)
        assertTrue("09121234567" in s.searchText)
    }

    @Test
    fun deleteAndRestoreCustomerBringsBackOnlyItsOwnContent() = runBlocking {
        val c = customers.create("الف")
        val k1 = cases.create(c, "پروندهٔ ۱")
        val k2 = cases.create(c, "پروندهٔ ۲")
        cases.delete(k2) // پیش‌تر جدا حذف شده
        clock.now += 1_000
        customers.delete(c)
        assertTrue(summaries().isEmpty())
        clock.now += 1_000
        customers.restore(c)
        val s = summaries().single()
        assertEquals(1, s.openCases) // فقط k1 برمی‌گردد
        assertNotNull(db.caseDao().observe(k1).first())
        assertNull(db.caseDao().observe(k2).first())
    }

    @Test
    fun mergeMovesEverythingAndSoftDeletesSource() = runBlocking {
        val a = customers.create("آقای رنجبر", "")
        val b = customers.create("رنجبر دوم", "09120000000")
        val k = cases.create(b, "پرونده")
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO tasks (customerId, caseId, title, searchText, createdAt) VALUES ($b, $k, 'کار', '', 1)",
        )
        db.openHelper.writableDatabase.execSQL(
            "INSERT INTO items (kind, customerId, caseId, text, searchText, createdAt) VALUES ('text', $b, $k, 'یادداشت', '', 1)",
        )
        customers.merge(b, a)
        val list = summaries()
        assertEquals(listOf(a), list.map { it.id })
        assertEquals(1, list[0].openCases)
        assertEquals(1, list[0].openTasks)
        assertEquals("09120000000", list[0].phone) // تلفن مبدأ چون مقصد نداشت
        assertFalse(list[0].isEmpty)
        assertEquals(a, db.caseDao().get(k)!!.customerId)
    }

    @Test
    fun caseStatusDeleteRestoreAndMove() = runBlocking {
        val a = customers.create("الف")
        val b = customers.create("ب")
        val k = cases.create(a, "پرونده")
        cases.setStatus(k, CaseStatus.FINAL)
        assertEquals(0, summaries().first { it.id == a }.openCases)
        cases.setStatus(k, CaseStatus.DOING)
        cases.moveTo(k, b)
        assertEquals(1, summaries().first { it.id == b }.openCases)
        cases.delete(k)
        assertEquals(0, summaries().first { it.id == b }.openCases)
        cases.restore(k)
        assertEquals(1, summaries().first { it.id == b }.openCases)
    }

    @Test
    fun archiveHidesFromActiveButActivityUnarchives() = runBlocking {
        val a = customers.create("الف")
        customers.setArchived(a, true)
        assertTrue(summaries().single().archived)
        cases.create(a, "پرونده")
        assertFalse(summaries().single().archived)
    }

    @Test
    fun purgeRemovesOnlyOldTrash() = runBlocking {
        val old = customers.create("قدیمی")
        val fresh = customers.create("تازه")
        customers.delete(old)
        clock.now += 31L * 86_400_000
        customers.delete(fresh)
        trash.purge()
        assertNull(db.customerDao().get(old))
        assertNotNull(db.customerDao().get(fresh))
    }

    @Test
    fun notSimilarIsStoredOrdered() = runBlocking {
        customers.markNotSimilar(9, 3)
        assertEquals(setOf(3L to 9L), customers.observeNotSimilar().first())
    }
}
