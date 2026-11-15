package app.pocketos.board.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.repo.CaseRepository
import app.pocketos.board.data.repo.CustomerRepository
import app.pocketos.board.data.repo.ItemKind
import app.pocketos.board.data.repo.ItemRepository
import app.pocketos.board.data.repo.TagRepository
import app.pocketos.board.data.repo.Target
import app.pocketos.board.data.repo.TaskRepository
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
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskItemTest {
    private lateinit var db: BoardDatabase
    private val today = LocalDate.of(2026, 11, 15)
    private val clock = MutableClock.at(today)
    private lateinit var files: FileStore
    private lateinit var customers: CustomerRepository
    private lateinit var cases: CaseRepository
    private lateinit var items: ItemRepository
    private lateinit var tasks: TaskRepository
    private lateinit var tags: TagRepository
    private lateinit var trash: TrashRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, BoardDatabase::class.java).allowMainThreadQueries().build()
        files = FileStore(ctx)
        customers = CustomerRepository(db, clock)
        cases = CaseRepository(db, clock)
        items = ItemRepository(db, clock, files)
        tasks = TaskRepository(db, clock, items)
        tags = TagRepository(db)
        trash = TrashRepository(db, clock, files)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun open(customerId: Long) = tasks.observeOpenByCustomer(customerId).first()

    @Test
    fun doneCreatesHistoryEventAndUndoneRemovesIt() = runBlocking {
        val c = customers.create("الف")
        val t = tasks.create(Target(c), "تحویل نقشه #اداره", today)
        assertEquals(1, open(c).size)

        val r = tasks.done(t)
        assertNull(r.nextTaskId)
        assertEquals(0, open(c).size)
        val stream = items.observeStream(c, null, null, 20).first()
        assertEquals(1, stream.size)
        assertEquals(ItemKind.EVENT, stream[0].kind)
        assertTrue(stream[0].text.contains("تحویل نقشه"))

        // دوبار فشردن، رویداد تکراری نمی‌سازد
        tasks.done(t)
        assertEquals(1, items.observeStream(c, null, null, 20).first().size)

        tasks.undone(t)
        assertEquals(1, open(c).size)
        assertEquals(0, items.observeStream(c, null, null, 20).first().size)
    }

    @Test
    fun repeatingTaskSpawnsNextOnFixedScheduleAndUndoRemovesIt() = runBlocking {
        val c = customers.create("الف")
        val t = tasks.create(Target(c), "گزارش", LocalDate.of(2026, 11, 10), "weekly")
        val r = tasks.done(t)
        assertNotNull(r.nextTaskId)
        val next = open(c).single()
        assertEquals("2026-11-17", next.dueDay) // ۱۰ + ۷ = ۱۷ (اولین نوبتِ بعد از امروز)
        assertEquals("weekly", next.repeat)
        assertEquals(t, next.spawnedBy)

        tasks.undone(t)
        val back = open(c).single()
        assertEquals(t, back.id)
        assertEquals("2026-11-10", back.dueDay)
    }

    @Test
    fun movingOneOccurrenceKeepsTheSchedule() = runBlocking {
        val c = customers.create("الف")
        val t = tasks.create(Target(c), "جلسه", LocalDate.of(2026, 11, 17), "weekly")
        val old = tasks.setDay(t, LocalDate.of(2026, 11, 19))
        assertEquals(LocalDate.of(2026, 11, 17), old)
        assertEquals("2026-11-17", open(c).single().anchor)

        // برگرداندن به روز برنامه، anchor را پاک می‌کند
        tasks.setDay(t, LocalDate.of(2026, 11, 17))
        assertNull(open(c).single().anchor)

        // نوبتِ جابه‌جاشده انجام شود ← نوبت بعدی از برنامهٔ ثابت
        tasks.setDay(t, LocalDate.of(2026, 11, 19))
        tasks.done(t)
        assertEquals("2026-11-24", open(c).single().dueDay)
    }

    @Test
    fun finishingLastTaskOfOpenCaseAsksToFinishCase() = runBlocking {
        val c = customers.create("الف")
        val k = cases.create(c, "پرونده")
        val t1 = tasks.create(Target(c, k), "یک", today)
        val t2 = tasks.create(Target(c, k), "دو", today)
        assertNull(tasks.done(t1).askFinishCaseId)
        assertEquals(k, tasks.done(t2).askFinishCaseId)

        cases.setStatus(k, CaseStatus.FINAL)
        val t3 = tasks.create(Target(c, k), "سه", today)
        assertNull(tasks.done(t3).askFinishCaseId) // پروندهٔ نهایی دوباره پیشنهاد نمی‌دهد
    }

    @Test
    fun noteConvertsToTaskAndBack() = runBlocking {
        val c = customers.create("الف")
        val k = cases.create(c, "پرونده")
        val n = items.add(Target(null, k), ItemKind.TEXT, "تحویل نقشه #اداره")
        val t = tasks.fromNote(n, "تحویل نقشه #اداره", today.plusDays(1), null)
        assertEquals(0, items.observeStream(c, null, null, 20).first().size)
        val task = open(c).single()
        assertEquals(k, task.caseId)
        assertEquals(c, task.customerId)

        tasks.undoFromNote(t, n)
        assertEquals(0, open(c).size)
        assertEquals(1, items.observeStream(c, null, null, 20).first().size)
    }

    @Test
    fun itemsInboxPinMoveAndKindCounts() = runBlocking {
        val inbox = items.add(Target.Inbox, ItemKind.TEXT, "خام")
        assertEquals(1, items.observeInboxCount().first())

        val c = customers.create("الف")
        items.move(inbox, Target(c))
        assertEquals(0, items.observeInboxCount().first())

        val p = items.add(Target(c), ItemKind.TEXT, "مهم")
        items.add(Target(c), ItemKind.PLACE, "موقعیت", lat = 35.7, lng = 51.4)
        items.setPinned(p, true)
        assertEquals(listOf(p), items.observePinned(c, null).first().map { it.id })
        assertFalse(items.observeStream(c, null, null, 20).first().any { it.id == p }) // سنجاق‌شده در سابقه تکرار نمی‌شود
        val counts = items.observeKindCounts(c, null).first().associate { it.kind to it.n }
        assertEquals(2, counts[ItemKind.TEXT])
        assertEquals(1, counts[ItemKind.PLACE])
        assertEquals(1, items.observeStream(c, null, ItemKind.PLACE, 20).first().size)

        items.updateText(p, "مهم‌تر")
        assertEquals("مهم‌تر", db.itemDao().get(p)!!.text)
    }

    @Test
    fun itemAddedToCaseTakesTheCaseCustomer() = runBlocking {
        val c = customers.create("الف")
        val k = cases.create(c, "پرونده")
        val id = items.add(Target(null, k), ItemKind.TEXT, "یادداشت")
        assertEquals(c, db.itemDao().get(id)!!.customerId)
        assertEquals(k, db.itemDao().get(id)!!.caseId)
    }

    @Test
    fun tagsAreExactAndCounted() = runBlocking {
        val c = customers.create("الف")
        tasks.create(Target(c), "تحویل #اداره", today)
        tasks.create(Target(c), "نامه #ادارهکل", today)
        items.add(Target(c), ItemKind.TEXT, "یادداشت #اداره و #نقشه")
        val all = tags.all().toMap()
        assertEquals(2, all["اداره"])
        assertEquals(1, all["ادارهکل"])
        assertEquals(1, all["نقشه"])

        val r = tags.results("#اداره")
        assertEquals(1, r.openTasks.size)
        assertEquals(1, r.notes.size)
        assertTrue(r.doneTasks.isEmpty())

        tasks.done(r.openTasks[0].id)
        val after = tags.results("اداره")
        assertEquals(0, after.openTasks.size)
        assertEquals(1, after.doneTasks.size)
    }

    @Test
    fun purgeDeletesFilesOfOldTrash() = runBlocking {
        val c = customers.create("الف")
        val f = files.create("a.jpg", "image/jpeg")
        val id = items.add(Target(c), f.kind, file = f)
        assertTrue(files.file(f.path).exists())
        items.delete(id)
        clock.now += 31L * 86_400_000
        trash.purge()
        assertFalse(files.file(f.path).exists())
        assertNull(db.itemDao().get(id))
    }

    @Test
    fun deletingCustomerAlsoHidesTasksAndRestoreBringsThemBack() = runBlocking {
        val c = customers.create("الف")
        tasks.create(Target(c), "کار", today)
        items.add(Target(c), ItemKind.TEXT, "یادداشت")
        customers.delete(c)
        assertEquals(0, open(c).size)
        assertEquals(0, items.observeStream(c, null, null, 20).first().size)
        customers.restore(c)
        assertEquals(1, open(c).size)
        assertEquals(1, items.observeStream(c, null, null, 20).first().size)
    }
}
