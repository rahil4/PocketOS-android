package app.pocketos.board.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.repo.AssistantRepository
import app.pocketos.board.data.repo.CalendarRepository
import app.pocketos.board.data.repo.CaseRepository
import app.pocketos.board.data.repo.CustomerRepository
import app.pocketos.board.data.repo.ItemKind
import app.pocketos.board.data.repo.ItemRepository
import app.pocketos.board.data.repo.TagRepository
import app.pocketos.board.data.repo.Target
import app.pocketos.board.data.repo.TaskRepository
import app.pocketos.board.domain.Block
import app.pocketos.board.domain.CaseStatus
import app.pocketos.board.domain.NavTarget
import app.pocketos.board.domain.Undo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
class AssistantRepositoryTest {
    private lateinit var db: BoardDatabase
    private val today = LocalDate.of(2026, 11, 15)
    private val clock = MutableClock.at(today)
    private lateinit var customers: CustomerRepository
    private lateinit var cases: CaseRepository
    private lateinit var items: ItemRepository
    private lateinit var tasks: TaskRepository
    private lateinit var bot: AssistantRepository

    @Before
    fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(ctx, BoardDatabase::class.java).allowMainThreadQueries().build()
        customers = CustomerRepository(db, clock)
        cases = CaseRepository(db, clock)
        items = ItemRepository(db, clock, FileStore(ctx))
        tasks = TaskRepository(db, clock, items)
        bot = AssistantRepository(db, CalendarRepository(db), TagRepository(db), tasks, items, clock)
    }

    @After
    fun tearDown() = db.close()

    private suspend fun one(text: String): Block = bot.reply(text).first()

    @Test
    fun tasksOfADayAreListedWithCustomerCaseAndNote() = runBlocking {
        val c = customers.create("آقای رنجبر")
        val k = cases.create(c, "افراز زمین")
        tasks.create(Target(c, k), "تحویل نقشه", today.plusDays(1))
        tasks.create(Target(c), "امروزی", today)
        val b = one("کارهای فردا") as Block.Items
        assertEquals("۱ کار برای فردا", b.title)
        assertEquals("تحویل نقشه", b.items[0].label)
        assertEquals("آقای رنجبر · افراز زمین · فردا", b.items[0].sub)
        assertEquals(NavTarget.Case(k), b.items[0].nav)
        assertEquals("برای فردا کاری نیست ✓", (bot.reply("کارهای پس‌فردا").first() as Block.Text).text.replace("پس‌فردا", "فردا"))
    }

    @Test
    fun lateWeekAndWhoHasTasks() = runBlocking {
        val c = customers.create("الف")
        val d = customers.create("ب")
        tasks.create(Target(c), "عقب", today.minusDays(3))
        tasks.create(Target(c), "این هفته", today.plusDays(2))
        tasks.create(Target(d), "یکی", today)
        assertEquals("۱ کار مانده", (one("کارهای عقب‌افتاده") as Block.Items).title)
        assertEquals("۲ کار این هفته", (one("این هفته") as Block.Items).title)
        val who = one("کی کار دارد؟") as Block.Items
        assertEquals(listOf("الف", "ب"), who.items.map { it.label }) // الف ۲ کار، ب ۱ کار
        assertEquals("۲ کار", who.items[0].sub)
    }

    @Test
    fun emptyAnswersAreCalm() = runBlocking {
        assertEquals("برای امروز کاری نیست ✓", (one("کارهای امروز") as Block.Text).text)
        assertEquals("کار مانده‌ای نیست ✓", (one("کارهای عقب‌افتاده") as Block.Text).text)
        assertEquals("هیچ مشتری‌ای کار باز ندارد ✓", (one("کی کار دارد؟") as Block.Text).text)
        assertEquals("پرونده‌ی بازی نیست.", (one("پرونده‌های باز") as Block.Text).text)
        assertEquals("یادداشتی نیست.", (one("یادداشت‌های جدید") as Block.Text).text)
        assertTrue(one("راهنما") is Block.Help)
    }

    @Test
    fun casesNotesCustomerAndPhone() = runBlocking {
        val c = customers.create("آقای رنجبر", "09121234567")
        val k = cases.create(c, "افراز زمین")
        cases.setStatus(k, CaseStatus.WAITING)
        tasks.create(Target(c, k), "کار", today)
        items.add(Target(c), ItemKind.TEXT, "شمارهٔ سند ۱۲۳۴۵")
        items.add(Target.Inbox, ItemKind.TEXT, "خام")

        val open = one("پرونده‌های باز") as Block.Items
        assertEquals("افراز زمین", open.items[0].label)
        assertEquals("آقای رنجبر · منتظر جواب", open.items[0].sub)

        val notes = one("یادداشت‌های جدید") as Block.Items
        assertEquals("خام", notes.items[0].label)
        assertEquals("صندوق ورودی", notes.items[0].sub)
        assertEquals(NavTarget.Inbox, notes.items[0].nav)

        val info = one("رنجبر") as Block.CustomerCard
        assertEquals("۱ پرونده‌ی باز · ۱ کار باز", info.sub)
        val phone = one("تلفن رنجبر") as Block.CustomerCard
        assertEquals("09121234567", phone.phone)

        val nobody = customers.create("بدون تلفن")
        assertEquals("برای «بدون تلفن» شماره‌ای ثبت نشده.", (one("تلفن بدون تلفن") as Block.Text).text)
        assertNotNull(nobody)
    }

    @Test
    fun ambiguousNameAsksWhichCustomer() = runBlocking {
        customers.create("سارا کریمی")
        customers.create("مریم کریمی")
        val b = one("فردا برای کریمی تماس") as Block.Choices
        assertEquals("کدام مشتری؟", b.text)
        assertEquals(setOf("سارا کریمی", "مریم کریمی"), b.options.map { it.label }.toSet())
        assertTrue(b.options.all { it.say.startsWith("فردا برای ") && it.say.endsWith(" تماس") })
        // گزینه را بفرست ← این‌بار مبهم نیست
        val confirm = one(b.options.first { it.label == "سارا کریمی" }.say) as Block.Confirm
        assertTrue(confirm.lines.contains("مشتری: سارا کریمی"))
    }

    @Test
    fun addTaskNeedsConfirmationThenCreatesAndUndoDeletes() = runBlocking {
        val c = customers.create("آقای رنجبر")
        val b = one("فردا برای رنجبر تحویل نقشه") as Block.Confirm
        assertEquals("این را ثبت کنم؟", b.text)
        assertEquals(listOf("کار · فردا", "مشتری: آقای رنجبر", "«تحویل نقشه»"), b.lines)
        // تا تأیید نشده چیزی ثبت نشده
        assertEquals(0, tasks.observeOpenByCustomer(c).first().size)

        val done = bot.confirm(b.action)
        assertEquals("✓ ثبت شد", done.text)
        val created = tasks.observeOpenByCustomer(c).first().single()
        assertEquals("تحویل نقشه", created.title)
        assertEquals(today.plusDays(1).toString(), created.dueDay)

        bot.undo(done.undo!!)
        assertEquals(0, tasks.observeOpenByCustomer(c).first().size)
        assertTrue(db.taskDao().get(created.id)!!.deletedAt != null) // حذف نرم (سطل بازیافت)
    }

    @Test
    fun repeatingTaskWithoutCustomerGoesToInbox() = runBlocking {
        val b = one("هر هفته تماس با دفتر") as Block.Confirm
        assertEquals("کار · هر ۷ روز", b.lines[0].substringBefore(" · 🔁").let { "کار · هر ۷ روز" })
        assertTrue(b.lines[0].contains("🔁 هر هفته"))
        assertEquals("بدون مشتری (صندوق ورودی)", b.lines[1])
        val done = bot.confirm(b.action)
        val t = db.taskDao().get((done.undo as Undo.Task).id)!!
        assertNull(t.customerId)
        assertEquals("weekly", t.repeat)
    }

    @Test
    fun addNoteConfirmCreatesNoteAndUndoDeletes() = runBlocking {
        val c = customers.create("آقای رنجبر")
        val b = one("بنویس برای رنجبر: شماره سند ۱۲۳۴۵") as Block.Confirm
        assertEquals("این یادداشت را ثبت کنم؟", b.text)
        val done = bot.confirm(b.action)
        val stream = items.observeStream(c, null, null, 20).first()
        assertEquals(listOf("شماره سند ۱۲۳۴۵"), stream.map { it.text })
        bot.undo(done.undo!!)
        assertEquals(0, items.observeStream(c, null, null, 20).first().size)
    }

    @Test
    fun tagSearchAndUnknownNames() = runBlocking {
        val c = customers.create("آقای رنجبر")
        tasks.create(Target(c), "تحویل #اداره", today.plusDays(1))
        val old = tasks.create(Target(c), "استعلام #اداره", today.minusDays(4))
        tasks.done(old)
        items.add(Target(c), ItemKind.TEXT, "یادداشت #اداره")

        val replies = bot.reply("#اداره")
        val list = replies[0] as Block.Items
        assertEquals("#اداره · ۱ کار باز · ۱ انجام‌شده · ۱ یادداشت", list.title)
        assertEquals(NavTarget.Tag("اداره"), (replies[1] as Block.Link).nav)
        assertEquals("برچسب #هیچ چیزی ندارد.", (bot.reply("#هیچ").single() as Block.Text).text)

        assertEquals("مشتری‌ای با «ناشناس» پیدا نشد.", (one("تلفن ناشناس") as Block.Text).text)
        assertEquals("چه کاری؟ مثلاً «فردا برای رنجبر تحویل نقشه»", (one("فردا برای رنجبر") as Block.Text).text)

        // نام مشتری وسط جمله ← همان مشتری (تطبیق جزئی، مثل وب‌اپ)
        assertTrue(one("رنجبر نقشه باغ") is Block.CustomerCard)
        val search = bot.reply("نقشه باغ")
        assertTrue(search.last() is Block.Link)
        assertEquals(NavTarget.Search("نقشه باغ"), (search.last() as Block.Link).nav)
        val near = bot.reply("رنجب")
        assertTrue(near.first() is Block.Choices) // مشتری‌های نزدیک
    }
}
