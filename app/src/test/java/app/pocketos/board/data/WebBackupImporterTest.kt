package app.pocketos.board.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.repo.BackupService
import app.pocketos.board.data.repo.CaseRepository
import app.pocketos.board.data.repo.CustomerRepository
import app.pocketos.board.data.repo.ItemKind
import app.pocketos.board.data.repo.PrefRepository
import app.pocketos.board.data.repo.WebBackupImporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
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
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private val WEB_SCHEMA = listOf(
    "CREATE TABLE users(id INTEGER PRIMARY KEY, name TEXT UNIQUE NOT NULL, pw_hash TEXT NOT NULL, created TEXT NOT NULL)",
    """CREATE TABLE cases(id INTEGER PRIMARY KEY, code TEXT NOT NULL DEFAULT '', title TEXT NOT NULL, client TEXT NOT NULL DEFAULT '',
        status TEXT NOT NULL DEFAULT 'جدید', owner_id INTEGER, due TEXT, created TEXT NOT NULL, last_activity TEXT NOT NULL,
        deleted_at TEXT, customer_id INTEGER, del_customer INTEGER, search_text TEXT)""",
    """CREATE TABLE customers(id INTEGER PRIMARY KEY, name TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '', created TEXT NOT NULL,
        last_activity TEXT NOT NULL, deleted_at TEXT, search_text TEXT, pinned_at TEXT, archived_at TEXT)""",
    """CREATE TABLE items(id INTEGER PRIMARY KEY, client_id TEXT UNIQUE, user_id INTEGER NOT NULL, case_id INTEGER, kind TEXT NOT NULL,
        text TEXT NOT NULL DEFAULT '', file_name TEXT, file_mime TEXT, file_path TEXT, lat REAL, lon REAL, acc REAL, created TEXT NOT NULL,
        filed_at TEXT, thumb_path TEXT, edited_at TEXT, deleted_at TEXT, search_text TEXT, del_case INTEGER, customer_id INTEGER,
        del_customer INTEGER, pinned_at TEXT)""",
    "CREATE TABLE state(user_id INTEGER PRIMARY KEY, active_case_id INTEGER, active_customer_id INTEGER)",
    """CREATE TABLE tasks(id INTEGER PRIMARY KEY, client_id TEXT UNIQUE, case_id INTEGER, user_id INTEGER NOT NULL, title TEXT NOT NULL,
        due TEXT NOT NULL, done_at TEXT, created TEXT NOT NULL, deleted_at TEXT, del_case INTEGER, customer_id INTEGER, del_customer INTEGER,
        repeat TEXT, spawned_by INTEGER, search_text TEXT, anchor TEXT)""",
    """CREATE TABLE prefs(user_id INTEGER PRIMARY KEY, evening_hour INTEGER NOT NULL DEFAULT 19, closed_on TEXT, theme TEXT NOT NULL DEFAULT 'auto',
        fsize TEXT NOT NULL DEFAULT 'md', morning_on INTEGER NOT NULL DEFAULT 1, morning_hour INTEGER NOT NULL DEFAULT 8,
        evening_push INTEGER NOT NULL DEFAULT 0, push_names INTEGER NOT NULL DEFAULT 1, last_morning TEXT, last_evening TEXT,
        backup_remind INTEGER NOT NULL DEFAULT 1, last_backup_dl TEXT, last_backup_remind TEXT)""",
    "CREATE TABLE meta(k TEXT PRIMARY KEY, v TEXT)",
    "CREATE TABLE similar_no(a INTEGER NOT NULL, b INTEGER NOT NULL, PRIMARY KEY(a, b))",
)

private val WEB_DATA = listOf(
    "INSERT INTO users VALUES(1,'rahil','h','2026-01-01T00:00:00Z'),(2,'همکار','h','2026-01-01T00:00:00Z')",
    "INSERT INTO customers(id,name,phone,created,last_activity,deleted_at,pinned_at,archived_at) VALUES" +
        "(1,'آقای رنجبر','09121234567','2026-03-01T08:00:00Z','2026-11-10T08:00:00Z',NULL,'2026-11-01T00:00:00Z',NULL)," +
        "(2,'علي كريمي','','2026-03-02T08:00:00Z','2026-10-10T08:00:00Z',NULL,NULL,'2026-10-01T00:00:00Z')," +
        "(3,'حذف‌شده','','2026-03-03T08:00:00Z','2026-10-10T08:00:00Z','2026-11-12T10:00:00Z',NULL,NULL)",
    "INSERT INTO cases(id,code,title,client,status,due,created,last_activity,deleted_at,customer_id,del_customer) VALUES" +
        "(10,'A-12','افراز زمین','','در حال انجام','2026-11-20','2026-04-01T00:00:00Z','2026-11-09T00:00:00Z',NULL,1,NULL)," +
        "(11,'','پرونده یتیم','','جدید',NULL,'2026-04-01T00:00:00Z','2026-11-09T00:00:00Z',NULL,NULL,NULL)," +
        "(12,'','پروندهٔ متقاضی حذف‌شده','','جدید',NULL,'2026-04-01T00:00:00Z','2026-11-09T00:00:00Z','2026-11-12T10:00:00Z',3,3)," +
        "(13,'','منتظر','','در انتظار',NULL,'2026-04-01T00:00:00Z','2026-11-09T00:00:00Z',NULL,1,NULL)," +
        "(14,'','تمام','','نهایی',NULL,'2026-04-01T00:00:00Z','2026-11-09T00:00:00Z',NULL,1,NULL)",
    "INSERT INTO items(id,client_id,user_id,case_id,kind,text,file_name,file_mime,file_path,lat,lon,created,deleted_at,del_customer,customer_id) VALUES" +
        "(100,'c-1',1,10,'text','شمارهٔ سند ۱۲۳۴۵',NULL,NULL,NULL,35.7,51.4,'2026-11-09T09:00:00Z',NULL,NULL,1)," +
        "(101,NULL,1,NULL,'photo','','نقشه.jpg','image/jpeg','aabbccdd.jpg',NULL,NULL,'2026-11-09T10:00:00Z',NULL,NULL,1)," +
        "(102,NULL,1,NULL,'location','',NULL,NULL,NULL,35.7,51.4,'2026-11-09T11:00:00Z',NULL,NULL,1)," +
        "(103,'done:200',1,10,'event','کار انجام شد: ارسال نامه',NULL,NULL,NULL,NULL,NULL,'2026-11-14T09:00:00Z',NULL,NULL,1)," +
        "(104,NULL,1,NULL,'audio','',NULL,'audio/webm','ee11.webm',NULL,NULL,'2026-11-09T12:00:00Z',NULL,NULL,1)," +
        "(105,NULL,1,NULL,'text','یادداشت حذف‌شده',NULL,NULL,NULL,NULL,NULL,'2026-11-01T12:00:00Z','2026-11-12T10:00:00Z',3,3)," +
        "(106,NULL,1,NULL,'text','در صندوق',NULL,NULL,NULL,NULL,NULL,'2026-11-09T13:00:00Z',NULL,NULL,NULL)," +
        "(107,NULL,1,NULL,'file','','a.dxf','application/octet-stream','ff22.dxf',NULL,NULL,'2026-11-09T14:00:00Z',NULL,NULL,1)",
    "INSERT INTO tasks(id,client_id,case_id,user_id,title,due,done_at,created,deleted_at,del_customer,customer_id,repeat,spawned_by,anchor) VALUES" +
        "(200,NULL,10,1,'ارسال نامه #اداره','2026-11-14','2026-11-14T09:00:00Z','2026-11-01T00:00:00Z',NULL,NULL,1,NULL,NULL,NULL)," +
        "(201,NULL,NULL,1,'تماس','2026-11-16',NULL,'2026-11-14T09:00:00Z',NULL,NULL,1,'weekly',200,'2026-11-15')," +
        "(202,NULL,NULL,1,'کار حذف‌شده','2026-11-16',NULL,'2026-11-01T00:00:00Z','2026-11-12T10:00:00Z',3,3,NULL,NULL,NULL)," +
        "(203,NULL,10,1,'بدون متقاضی مستقیم','2026-11-17',NULL,'2026-11-01T00:00:00Z',NULL,NULL,NULL,NULL,NULL,NULL)",
    "INSERT INTO prefs(user_id,evening_hour,theme,fsize,morning_on,morning_hour,evening_push,push_names,backup_remind) VALUES(1,20,'dark','lg',0,7,1,0,0),(2,18,'light','sm',1,9,0,1,1)",
    "INSERT INTO state(user_id,active_case_id,active_customer_id) VALUES(1,10,NULL)",
    "INSERT INTO similar_no VALUES(2,1),(1,99)",
)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WebBackupImporterTest {
    private lateinit var ctx: Context
    private lateinit var target: BoardDatabase
    private val today = LocalDate.of(2026, 11, 15)
    private val clock = MutableClock.at(today)
    private lateinit var files: FileStore
    private lateinit var importer: WebBackupImporter

    private fun ms(s: String) = Instant.parse(s).toEpochMilli()

    private fun webDb(): SQLiteDatabase {
        val db = SQLiteDatabase.create(null)
        (WEB_SCHEMA + WEB_DATA).forEach { db.execSQL(it) }
        return db
    }

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        RestoreApplier.dir(ctx).deleteRecursively()
        target = Room.inMemoryDatabaseBuilder(ctx, BoardDatabase::class.java).allowMainThreadQueries().build()
        files = FileStore(ctx)
        importer = WebBackupImporter(ctx, files, clock)
    }

    @After
    fun tearDown() = target.close()

    @Test
    fun convertsCustomersCasesTasksItemsAndPrefs() = runBlocking {
        val preview = importer.convert(webDb(), target)
        assertEquals(3, preview.customers) // رنجبر، کریمی و متقاضیِ جایگزینِ پرونده‌ی یتیم
        assertEquals(4, preview.cases)
        assertEquals(3, preview.tasks)
        assertEquals(6, preview.notes) // رویداد و حذف‌شده نمی‌شمارد
        assertEquals(3, preview.files)
        assertEquals(1, preview.orphanCases)
        assertEquals(2, preview.users)

        val c1 = target.customerDao().get(1)!!
        assertEquals("09121234567", c1.phone)
        assertEquals(ms("2026-11-01T00:00:00Z"), c1.pinnedAt)
        assertEquals(ms("2026-03-01T08:00:00Z"), c1.createdAt)
        val c2 = target.customerDao().get(2)!!
        assertEquals(ms("2026-10-01T00:00:00Z"), c2.archivedAt)
        assertTrue("علی کریمی" in c2.searchText) // ي/ك عربی در جست‌وجو یکی می‌شود
        val orphan = target.customerDao().get(4)!!
        assertEquals(WebBackupImporter.ORPHAN_CUSTOMER, orphan.name)
        assertEquals(4L, target.caseDao().get(11)!!.customerId)

        assertEquals("افراز زمین (A-12)", target.caseDao().get(10)!!.title)
        assertEquals("doing", target.caseDao().get(10)!!.status)
        assertEquals("2026-11-20", target.caseDao().get(10)!!.dueDay)
        assertEquals("new", target.caseDao().get(11)!!.status)
        assertEquals("waiting", target.caseDao().get(13)!!.status)
        assertEquals("final", target.caseDao().get(14)!!.status)

        // مورد‌ها
        val text = target.itemDao().get(100)!!
        assertEquals(ItemKind.TEXT, text.kind)
        assertEquals(10L, text.caseId)
        assertEquals(1L, text.customerId)
        assertEquals(35.7, text.lat!!, 0.0001)
        assertEquals("c-1", text.clientId)
        val photo = target.itemDao().get(101)!!
        assertEquals(ItemKind.PHOTO, photo.kind)
        assertEquals("نقشه.jpg", photo.fileName)
        assertEquals("image/jpeg", photo.mime)
        assertEquals(WebBackupImporter.uuidFor("aabbccdd.jpg"), photo.filePath)
        assertEquals(36, photo.filePath!!.length)
        val loc = target.itemDao().get(102)!!
        assertEquals(ItemKind.PLACE, loc.kind)
        assertEquals("موقعیت من", loc.text)
        assertEquals(ItemKind.EVENT, target.itemDao().get(103)!!.kind)
        assertEquals("done:200", target.itemDao().get(103)!!.clientId)
        assertEquals(ItemKind.AUDIO, target.itemDao().get(104)!!.kind)
        assertEquals(ItemKind.FILE, target.itemDao().get(107)!!.kind)
        assertNull(target.itemDao().get(106)!!.customerId) // صندوق ورودی می‌ماند

        // کارها
        val t200 = target.taskDao().get(200)!!
        assertEquals(ms("2026-11-14T09:00:00Z"), t200.doneAt)
        val t201 = target.taskDao().get(201)!!
        assertEquals("weekly", t201.repeat)
        assertEquals("2026-11-15", t201.anchor)
        assertEquals(200L, t201.spawnedBy)
        assertEquals(1L, target.taskDao().get(203)!!.customerId) // از پرونده می‌گیرد

        // تنظیمات کاربر اول
        suspend fun pref(k: String) = target.prefDao().get(k)
        assertEquals("dark", pref(PrefRepository.THEME))
        assertEquals("lg", pref(PrefRepository.FSIZE))
        assertEquals("20", pref(PrefRepository.EVENING_HOUR))
        assertEquals("0", pref(PrefRepository.MORNING_ON))
        assertEquals("7", pref(PrefRepository.MORNING_HOUR))
        assertEquals("1", pref(PrefRepository.EVENING_PUSH))
        assertEquals("0", pref(PrefRepository.PUSH_NAMES))
        assertEquals("0", pref(PrefRepository.BACKUP_REMIND))
        assertEquals("10", pref(PrefRepository.ACTIVE_CASE))
        assertNull(pref(PrefRepository.ACTIVE_CUSTOMER))

        assertEquals(setOf(1L to 2L), target.customerDao().observeNotSimilar().first().map { it.a to it.b }.toSet())
    }

    @Test
    fun softDeletesFollowTheirOwnerSoTheAppCanRestoreThem() = runBlocking {
        importer.convert(webDb(), target)
        val deletedAt = ms("2026-11-12T10:00:00Z")
        assertEquals(deletedAt, target.customerDao().get(3)!!.deletedAt)
        assertEquals(deletedAt, target.caseDao().get(12)!!.deletedAt)
        assertEquals(deletedAt, target.itemDao().get(105)!!.deletedAt)
        assertEquals(deletedAt, target.taskDao().get(202)!!.deletedAt)

        // «بازگردانی» متقاضی در اپ، محتوایی را که همراهش حذف شده بود برمی‌گرداند
        CustomerRepository(target, clock).restore(3)
        assertNull(target.caseDao().get(12)!!.deletedAt)
        assertNull(target.itemDao().get(105)!!.deletedAt)
        assertNull(target.taskDao().get(202)!!.deletedAt)
        assertEquals(3L, target.caseDao().get(12)!!.customerId)
    }

    @Test
    fun tolerantOfOlderWebVersionsWithoutNewerColumns() = runBlocking {
        val old = SQLiteDatabase.create(null)
        listOf(
            "CREATE TABLE users(id INTEGER PRIMARY KEY, name TEXT, pw_hash TEXT, created TEXT)",
            "CREATE TABLE customers(id INTEGER PRIMARY KEY, name TEXT NOT NULL, phone TEXT NOT NULL DEFAULT '', created TEXT NOT NULL, last_activity TEXT NOT NULL)",
            "CREATE TABLE cases(id INTEGER PRIMARY KEY, code TEXT, title TEXT NOT NULL, client TEXT, status TEXT, due TEXT, created TEXT NOT NULL, last_activity TEXT NOT NULL)",
            "CREATE TABLE items(id INTEGER PRIMARY KEY, user_id INTEGER, case_id INTEGER, kind TEXT, text TEXT, created TEXT NOT NULL)",
            "CREATE TABLE tasks(id INTEGER PRIMARY KEY, case_id INTEGER, user_id INTEGER, title TEXT NOT NULL, due TEXT NOT NULL, created TEXT NOT NULL)",
            "INSERT INTO users VALUES(1,'a','h','2026-01-01T00:00:00Z')",
            "INSERT INTO customers VALUES(1,'الف','','2026-03-01T08:00:00Z','2026-03-01T08:00:00Z')",
            "INSERT INTO cases VALUES(1,'','پرونده','','جدید',NULL,'2026-03-01T08:00:00Z','2026-03-01T08:00:00Z')",
            "INSERT INTO items VALUES(1,1,1,'text','یادداشت','2026-03-01T08:00:00Z')",
            "INSERT INTO tasks VALUES(1,1,1,'کار','2026-03-02','2026-03-01T08:00:00Z')",
        ).forEach { old.execSQL(it) }
        val p = importer.convert(old, target)
        assertEquals(1, p.tasks)
        assertEquals(1, p.notes)
        assertEquals(1, p.orphanCases) // پروندهٔ بدون customer_id
        assertEquals("یادداشت", target.itemDao().get(1)!!.text)
    }

    @Test
    fun importFilesRenamesToUuidAndSkipsUnreferenced() = runBlocking {
        importer.convert(webDb(), target)
        val jpg = ByteArray(2000) { (it * 3).toByte() }
        val webm = byteArrayOf(5, 6, 7)
        val zip = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { z ->
                listOf("aabbccdd.jpg" to jpg, "ee11.webm" to webm, "aabbccdd.thumb.jpg" to byteArrayOf(1), "../x/zzz" to byteArrayOf(2)).forEach { (n, b) ->
                    z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry()
                }
            }
        }.toByteArray()
        assertEquals(2 to 3, importer.countFiles { ByteArrayInputStream(zip) })
        val (copied, wanted) = importer.importFiles { ByteArrayInputStream(zip) }
        assertEquals(2, copied)
        assertEquals(3, wanted) // ff22.dxf در ZIP نبود
        assertArrayEquals(jpg, files.file(WebBackupImporter.uuidFor("aabbccdd.jpg")).readBytes())
        assertArrayEquals(webm, files.file(WebBackupImporter.uuidFor("ee11.webm")).readBytes())
        assertFalse(files.file(WebBackupImporter.uuidFor("ff22.dxf")).exists())
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray =
        ByteArrayOutputStream().also { out -> ZipOutputStream(out).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } } }.toByteArray()

    @Test
    fun prepareOnAWebBackupZipThenRestoreEndToEnd() = runBlocking {
        val mainName = "web-main-test.db"
        ctx.deleteDatabase(mainName)
        val main = Room.databaseBuilder(ctx, BoardDatabase::class.java, mainName).allowMainThreadQueries().build()
        val customers = CustomerRepository(main, clock)
        customers.create("دادهٔ قدیمیِ اپ")

        // یک board.db واقعیِ وب‌اپ داخل ZIP «داده‌ها»
        val webFile = File(ctx.cacheDir, "web-src.db").also { it.delete() }
        SQLiteDatabase.openOrCreateDatabase(webFile, null).use { db -> (WEB_SCHEMA + WEB_DATA).forEach { db.execSQL(it) } }
        val zip = zipOf("board.db" to webFile.readBytes(), "README.txt" to "x".toByteArray(), "customers.csv" to "x".toByteArray())

        val preview = importer.prepare { ByteArrayInputStream(zip) }
        assertNotNull(preview)
        assertNull(preview!!.error)
        assertEquals(3, preview.customers)
        assertEquals(4, preview.cases)

        val backup = BackupService(ctx, main, files, PrefRepository(main), clock)
        assertTrue(backup.commitRestore())
        main.close()
        assertTrue(RestoreApplier.applyIfPending(ctx, mainName))

        val restored = Room.databaseBuilder(ctx, BoardDatabase::class.java, mainName).allowMainThreadQueries().build()
        try {
            val list = CustomerRepository(restored, clock).observeSummaries().first()
            assertEquals(setOf("آقای رنجبر", "علي كريمي", WebBackupImporter.ORPHAN_CUSTOMER), list.map { it.name }.toSet())
            assertFalse(list.any { it.name == "دادهٔ قدیمیِ اپ" }) // جایگزین شد
            assertEquals("افراز زمین (A-12)", CaseRepository(restored, clock).observeByCustomer(1).first().first { it.id == 10L }.title)
        } finally {
            restored.close()
            ctx.deleteDatabase(mainName)
        }
    }

    @Test
    fun prepareIgnoresZipsThatAreNotWebBackups() = runBlocking {
        assertNull(importer.prepare { ByteArrayInputStream(byteArrayOf(1, 2, 3)) }) // ZIP نیست
        assertNull(importer.prepare { ByteArrayInputStream(zipOf("hello.txt" to byteArrayOf(1))) }) // board.db ندارد
        assertNull(importer.prepare { ByteArrayInputStream(zipOf("board.db" to "not sqlite".toByteArray())) }) // SQLite نیست

        // پشتیبان خود اپ (Room) وب‌اپ نیست
        val name = "own-backup-test.db"
        ctx.deleteDatabase(name)
        val own = Room.databaseBuilder(ctx, BoardDatabase::class.java, name).allowMainThreadQueries().build()
        try {
            CustomerRepository(own, clock).create("الف")
            val out = ByteArrayOutputStream()
            BackupService(ctx, own, files, PrefRepository(own), clock).writeData(out)
            assertNull(importer.prepare { ByteArrayInputStream(out.toByteArray()) })
        } finally {
            own.close()
            ctx.deleteDatabase(name)
        }
    }
}
