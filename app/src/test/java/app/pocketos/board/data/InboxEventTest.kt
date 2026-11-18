package app.pocketos.board.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.repo.ItemKind
import app.pocketos.board.data.repo.ItemRepository
import app.pocketos.board.data.repo.Target
import app.pocketos.board.data.repo.TaskRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class InboxEventTest {
    private lateinit var db: BoardDatabase
    private val clock = MutableClock.at(LocalDate.of(2026, 11, 15))
    private lateinit var items: ItemRepository
    private lateinit var tasks: TaskRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, BoardDatabase::class.java).allowMainThreadQueries().build()
        items = ItemRepository(db, clock, FileStore(ctx))
        tasks = TaskRepository(db, clock, items)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun doneHistoryEventsAreNotCountedInTheInboxButCanBeDeleted() = runBlocking {
        items.add(Target.Inbox, ItemKind.TEXT, "یادداشت واقعی")
        tasks.done(tasks.create(Target.Inbox, "کار بی‌متقاضی", null))
        // صندوق هم یادداشت را دارد و هم رویدادِ «کار انجام شد»، ولی فقط یادداشت «مورد» حساب می‌شود
        val inbox = items.observeInbox().first()
        assertEquals(2, inbox.size)
        assertEquals(1, items.observeInboxCount().first())

        val event = inbox.first { it.kind == ItemKind.EVENT }
        items.delete(event.id)
        assertEquals(listOf(ItemKind.TEXT), items.observeInbox().first().map { it.kind })
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class EveningObserveTest {
    private lateinit var db: BoardDatabase
    private val today = LocalDate.of(2026, 11, 15)
    private val clock = MutableClock.at(today)

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, BoardDatabase::class.java).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun eveningRowsCarryCustomerNameAndFollowTaskChanges() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val customers = app.pocketos.board.data.repo.CustomerRepository(db, clock)
        val tasks = TaskRepository(db, clock, ItemRepository(db, clock, FileStore(ctx)))
        val home = app.pocketos.board.data.repo.HomeRepository(db, clock)
        val c = customers.create("آقای رنجبر")
        val id = tasks.create(Target(c, null), "ارسال نقشه", today)

        val first = home.observeEvening().first()
        assertEquals(listOf("آقای رنجبر"), first.tasks.map { it.customerName })

        tasks.done(id)
        assertEquals(0, home.observeEvening().first().tasks.size)
    }
}
