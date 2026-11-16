package app.pocketos.board.data

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.repo.ActiveTargetRepository
import app.pocketos.board.data.repo.CaseRepository
import app.pocketos.board.data.repo.CustomerRepository
import app.pocketos.board.data.repo.ItemKind
import app.pocketos.board.data.repo.ItemRepository
import app.pocketos.board.data.repo.PrefRepository
import app.pocketos.board.data.repo.ReminderService
import app.pocketos.board.data.repo.ReminderSink
import app.pocketos.board.data.repo.SettingsRepository
import app.pocketos.board.data.repo.ShareHandler
import app.pocketos.board.data.repo.Target
import app.pocketos.board.data.repo.TaskRepository
import app.pocketos.board.domain.Payload
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDate

private class FakeSink(var can: Boolean = true) : ReminderSink {
    val shown = ArrayList<Payload>()
    override fun canNotify() = can
    override fun show(payload: Payload) { shown += payload }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhoneFeaturesTest {
    private lateinit var ctx: Context
    private lateinit var db: BoardDatabase
    private val today = LocalDate.of(2026, 11, 15)
    private val clock = MutableClock.at(today, hour = 10)
    private lateinit var prefs: PrefRepository
    private lateinit var settings: SettingsRepository
    private lateinit var customers: CustomerRepository
    private lateinit var cases: CaseRepository
    private lateinit var items: ItemRepository
    private lateinit var tasks: TaskRepository
    private lateinit var files: FileStore
    private lateinit var sink: FakeSink
    private lateinit var reminders: ReminderService
    private lateinit var share: ShareHandler

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, BoardDatabase::class.java).allowMainThreadQueries().build()
        prefs = PrefRepository(db)
        settings = SettingsRepository(prefs)
        customers = CustomerRepository(db, clock)
        cases = CaseRepository(db, clock)
        files = FileStore(ctx)
        items = ItemRepository(db, clock, files)
        tasks = TaskRepository(db, clock, items)
        sink = FakeSink()
        reminders = ReminderService(db, prefs, settings, sink, clock)
        share = ShareHandler(ctx, items, customers, files, ActiveTargetRepository(prefs, cases, customers))
    }

    @After
    fun tearDown() = db.close()

    // ── تنظیمات ──
    @Test
    fun settingsDefaultsAndPersistence() = runBlocking {
        val d = settings.load()
        assertEquals("auto", d.theme)
        assertEquals("md", d.fsize)
        assertEquals(19, d.eveningHour)
        assertTrue(d.morningOn)
        assertEquals(8, d.morningHour)
        assertFalse(d.eveningPush)
        assertTrue(d.pushNames)

        settings.update { it.copy(theme = "dark", fsize = "xl", eveningHour = 0, morningOn = false, morningHour = 6, eveningPush = true, pushNames = false) }
        val n = SettingsRepository(prefs).load() // نمونهٔ تازه از همان prefs
        assertEquals("dark", n.theme)
        assertEquals("xl", n.fsize)
        assertEquals(0, n.eveningHour)
        assertFalse(n.morningOn)
        assertEquals(6, n.morningHour)
        assertTrue(n.eveningPush)
        assertFalse(n.pushNames)
        assertEquals(1.25f, n.scale)

        prefs.setString(PrefRepository.THEME, "neon")
        prefs.setString(PrefRepository.FSIZE, "huge")
        assertEquals("auto", settings.load().theme)
        assertEquals("md", settings.load().fsize)
    }

    // ── اعلان ──
    @Test
    fun morningReminderIsSentOncePerDayAndOnlyWithTasks() = runBlocking {
        assertTrue(reminders.run().isEmpty()) // کاری نیست ← اعلانی نمی‌رود
        val c = customers.create("آقای رنجبر")
        tasks.create(Target(c), "تحویل نقشه", today)
        assertTrue(reminders.run().isEmpty()) // امروز همین الان «فرستاده‌شده» علامت خورد (حتی بدون کار)

        clock.now += 86_400_000 // فردا ساعت ۱۰
        tasks.create(Target(c), "تماس", today.plusDays(1))
        val sent = reminders.run()
        assertEquals(1, sent.size)
        assertEquals("امروز ۱ کار داری · ۱ مانده", sent[0].title)
        assertEquals("آقای رنجبر: تماس", sent[0].body)
        assertEquals("home", sent[0].route)
        assertTrue(reminders.run().isEmpty()) // همان روز دوباره نه
        assertEquals(1, sink.shown.size)
    }

    @Test
    fun remindersRespectWindowsSettingsAndPermission() = runBlocking {
        val c = customers.create("الف")
        tasks.create(Target(c), "کار", today)

        clock.now = MutableClock.at(today, 7).now // پیش از ساعت ۸
        assertTrue(reminders.run().isEmpty())
        clock.now = MutableClock.at(today, 13).now // بعد از ۸+۴
        assertTrue(reminders.run().isEmpty())

        clock.now = MutableClock.at(today, 9).now
        sink.can = false // مجوز نیست ← نه می‌فرستد نه «فرستاده‌شده» علامت می‌زند
        assertTrue(reminders.run().isEmpty())
        sink.can = true
        assertEquals(1, reminders.run().size) // بعد از مجوز همان روز می‌رسد

        settings.update { it.copy(pushNames = false) }
        clock.now = MutableClock.at(today.plusDays(1), 9).now
        assertEquals("برای دیدن، برنامه را باز کنید", reminders.run().single().body)

        settings.update { it.copy(morningOn = false) }
        clock.now = MutableClock.at(today.plusDays(2), 9).now
        assertTrue(reminders.run().isEmpty())
    }

    @Test
    fun eveningReminderOnlyWhenEnabledOpenTasksAndDayNotClosed() = runBlocking {
        val c = customers.create("الف")
        tasks.create(Target(c), "کار", today)
        settings.update { it.copy(morningOn = false, eveningPush = true) }

        clock.now = MutableClock.at(today, 18).now
        assertTrue(reminders.run().isEmpty()) // هنوز ساعت ۱۹ نشده
        clock.now = MutableClock.at(today, 19).now
        val e = reminders.run().single()
        assertEquals("روز را ببند و سبک شو", e.title)
        assertEquals("۱ کار هنوز باز است؛ یک لمس برای تصمیم.", e.body)
        assertEquals("evening", e.route)
        assertTrue(reminders.run().isEmpty()) // یک‌بار در روز

        // روز بعد، روز بسته شده ← اعلان نه
        clock.now = MutableClock.at(today.plusDays(1), 19).now
        prefs.setString(PrefRepository.CLOSED_ON, today.plusDays(1).toString())
        assertTrue(reminders.run().isEmpty())
    }

    @Test
    fun testNotificationAlwaysSends() = runBlocking {
        assertTrue(reminders.sendTest())
        assertEquals("اعلان آزمایشی", sink.shown.last().title)
        sink.can = false
        assertFalse(reminders.sendTest())
    }

    // ── اشتراک ──
    @Test
    fun sharedTextBecomesNoteInInboxOrActiveTarget() = runBlocking {
        val r = share.handle("  لینک یا متن  ", emptyList())
        assertEquals(1, r.items)
        assertEquals(1, items.observeInboxCount().first())

        val c = customers.create("الف")
        val k = cases.create(c, "پرونده")
        prefs.setString(PrefRepository.ACTIVE_CASE, k.toString())
        share.handle("برای پرونده", emptyList())
        val inCase = items.observeStream(null, k, null, 20).first()
        assertEquals(listOf("برای پرونده"), inCase.map { it.text })
        assertEquals(c, inCase[0].customerId)

        // پروندهٔ فعال حذف شد ← صندوق ورودی
        cases.delete(k)
        share.handle("بعد از حذف", emptyList())
        assertEquals(2, items.observeInboxCount().first())
    }

    @Test
    fun sharedFilesAreCopiedByteForByte() = runBlocking {
        val bytes = ByteArray(5000) { (it * 7).toByte() }
        val f = File(ctx.cacheDir, "نقشه.pdf").also { it.writeBytes(bytes) }
        val r = share.handle(null, listOf(Uri.fromFile(f), Uri.fromFile(File(ctx.cacheDir, "نیست.bin"))))
        assertEquals(1, r.items)
        assertEquals(1, r.failed)
        val item = items.observeInbox().first().single()
        assertEquals(ItemKind.FILE, item.kind)
        assertArrayEquals(bytes, files.file(item.filePath!!).readBytes())
    }

    @Test
    fun sharedVcardCreatesCustomersWithoutDuplicates() = runBlocking {
        customers.create("آقای رنجبر") // از پیش هست؛ فقط تلفنِ خالی پر می‌شود
        val v = "BEGIN:VCARD\nVERSION:3.0\nFN:آقای رنجبر\nTEL:+98 912 123 4567\nEND:VCARD\nBEGIN:VCARD\nVERSION:3.0\nFN:خانم محمدی\nTEL:09350000000\nEND:VCARD"
        val r = share.handle(v, emptyList())
        assertEquals(2, r.contacts)
        assertEquals(0, r.items)
        val all = customers.observeSummaries().first()
        assertEquals(2, all.size)
        assertEquals("09121234567", all.first { it.name == "آقای رنجبر" }.phone)

        // فایل .vcf
        val f = File(ctx.cacheDir, "contact.vcf").also { it.writeText("BEGIN:VCARD\nFN:کریمی\nTEL:09360000000\nEND:VCARD") }
        val r2 = share.handle(null, listOf(Uri.fromFile(f)))
        assertEquals(1, r2.contacts)
        assertEquals(3, customers.observeSummaries().first().size)
    }

    @Test
    fun vcardFileEncodings() {
        val utf16 = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "BEGIN:VCARD\nFN:علی\nEND:VCARD".toByteArray(Charsets.UTF_16LE)
        assertTrue(share.decode(utf16).contains("علی"))
        assertTrue(share.decode("FN:علی".toByteArray(Charsets.UTF_8)).contains("علی"))
        // windows-1256 (UTF-8 معتبر نیست)
        val cp = "FN:علي".toByteArray(java.nio.charset.Charset.forName("windows-1256"))
        assertTrue(share.decode(cp).contains("علي"))
    }
}
