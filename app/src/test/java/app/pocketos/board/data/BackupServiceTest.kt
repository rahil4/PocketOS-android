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
import app.pocketos.board.data.repo.ItemRepository
import app.pocketos.board.data.repo.PrefRepository
import app.pocketos.board.data.repo.RestoreKind
import app.pocketos.board.data.repo.Target
import app.pocketos.board.data.repo.TaskRepository
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
import java.time.LocalDate
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val DB_NAME = "backup-test.db"

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupServiceTest {
    private lateinit var ctx: Context
    private lateinit var db: BoardDatabase
    private val today = LocalDate.of(2026, 11, 15)
    private val clock = MutableClock.at(today)
    private lateinit var files: FileStore
    private lateinit var prefs: PrefRepository
    private lateinit var customers: CustomerRepository
    private lateinit var cases: CaseRepository
    private lateinit var items: ItemRepository
    private lateinit var tasks: TaskRepository
    private lateinit var backup: BackupService

    private fun openDb() = Room.databaseBuilder(ctx, BoardDatabase::class.java, DB_NAME).allowMainThreadQueries().build()

    private fun wire() {
        files = FileStore(ctx)
        prefs = PrefRepository(db)
        customers = CustomerRepository(db, clock)
        cases = CaseRepository(db, clock)
        items = ItemRepository(db, clock, files)
        tasks = TaskRepository(db, clock, items)
        backup = BackupService(ctx, db, files, prefs, clock)
    }

    @Before
    fun setUp() {
        ctx = ApplicationProvider.getApplicationContext()
        ctx.deleteDatabase(DB_NAME)
        RestoreApplier.dir(ctx).deleteRecursively()
        db = openDb()
        wire()
    }

    @After
    fun tearDown() {
        db.close()
        ctx.deleteDatabase(DB_NAME)
    }

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return out.toByteArray()
    }

    private fun entries(zip: ByteArray): Map<String, ByteArray> {
        val m = LinkedHashMap<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(zip)).use { z ->
            var e = z.nextEntry
            while (e != null) { m[e.name] = z.readBytes(); e = z.nextEntry }
        }
        return m
    }

    private suspend fun populate() {
        val c = customers.create("آقای رنجبر", "09121234567")
        val k = cases.create(c, "افراز زمین")
        tasks.create(Target(c, k), "تحویل نقشه #اداره", today.plusDays(1))
        items.add(Target(c), ItemKind.TEXT, "شمارهٔ سند, ۱۲۳۴۵")
        val gone = customers.create("حذف‌شده")
        customers.delete(gone)
    }

    @Test
    fun dataZipHasDbReadableCsvsAndReadme() = runBlocking {
        populate()
        val out = ByteArrayOutputStream()
        val bytes = backup.writeData(out)
        assertEquals(out.size().toLong(), bytes)
        val e = entries(out.toByteArray())
        assertEquals(setOf("board.db", "README.txt", "backup.json", "customers.csv", "cases.csv", "tasks.csv", "notes.csv"), e.keys)
        assertEquals("SQLite format 3", String(e["board.db"]!!, 0, 15, Charsets.US_ASCII))
        val customersCsv = String(e["customers.csv"]!!, Charsets.UTF_8)
        assertTrue(customersCsv.startsWith("﻿شناسه,نام,تلفن,ساخته‌شده\r\n"))
        assertTrue(customersCsv.contains("آقای رنجبر,09121234567"))
        assertFalse(customersCsv.contains("حذف‌شده")) // حذف‌شده‌ها در CSV نیست
        assertTrue(String(e["cases.csv"]!!, Charsets.UTF_8).contains("آقای رنجبر,افراز زمین,جدید"))
        assertTrue(String(e["tasks.csv"]!!, Charsets.UTF_8).contains("تحویل نقشه #اداره,2026-11-16"))
        assertTrue(String(e["notes.csv"]!!, Charsets.UTF_8).contains("\"شمارهٔ سند, ۱۲۳۴۵\""))
        assertTrue(String(e["README.txt"]!!, Charsets.UTF_8).contains("بازیابی از پشتیبان"))
        assertEquals(today.toString(), prefs.getString(PrefRepository.LAST_BACKUP))
    }

    @Test
    fun filesZipIsByteForByte() = runBlocking {
        val a = ByteArray(3000) { (it * 31).toByte() }
        val b = ByteArray(10) { 7 }
        val fa = files.create("a.jpg", "image/jpeg").also { files.file(it.path).writeBytes(a) }
        val fb = files.create("b.m4a", "audio/mp4").also { files.file(it.path).writeBytes(b) }
        val out = ByteArrayOutputStream()
        val (n, _) = backup.writeFiles(out)
        assertEquals(2, n)
        val e = entries(out.toByteArray())
        assertArrayEquals(a, e[fa.path])
        assertArrayEquals(b, e[fb.path])
    }

    @Test
    fun inspectDataZipShowsCountsAndDate() = runBlocking {
        populate()
        val out = ByteArrayOutputStream().also { backup.writeData(it) }
        val p = backup.inspect { ByteArrayInputStream(out.toByteArray()) }
        assertEquals(RestoreKind.DATA, p.kind)
        assertEquals(1, p.customers) // حذف‌شده نمی‌شمارد
        assertEquals(1, p.cases)
        assertEquals(1, p.tasks)
        assertEquals(1, p.notes)
        assertEquals(clock.millis(), p.createdAt)
    }

    @Test
    fun inspectFilesZipAndInvalidInputs() = runBlocking {
        val id = UUID.randomUUID().toString()
        val filesZip = zipOf(id to byteArrayOf(1, 2, 3), "readme.txt" to byteArrayOf(1))
        val f = backup.inspect { ByteArrayInputStream(filesZip) }
        assertEquals(RestoreKind.FILES, f.kind)
        assertEquals(1, f.files) // فقط نام uuid فایل حساب می‌شود

        assertEquals(RestoreKind.INVALID, backup.inspect { ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)) }.kind)
        assertEquals(RestoreKind.INVALID, backup.inspect { ByteArrayInputStream(zipOf("hello.txt" to byteArrayOf(1))) }.kind)
        // board.db که SQLite نیست
        val fake = backup.inspect { ByteArrayInputStream(zipOf("board.db" to "not a database".toByteArray())) }
        assertEquals(RestoreKind.INVALID, fake.kind)
        assertNotNull(fake.error)
    }

    @Test
    fun newerDatabaseVersionIsRefused() = runBlocking {
        val tmp = File(ctx.cacheDir, "newer.db")
        tmp.delete()
        SQLiteDatabase.openOrCreateDatabase(tmp, null).use { it.execSQL("CREATE TABLE x(a)"); it.execSQL("PRAGMA user_version = 99") }
        val p = backup.inspect { ByteArrayInputStream(zipOf("board.db" to tmp.readBytes())) }
        assertEquals(RestoreKind.INVALID, p.kind)
        assertTrue(p.error!!.contains("جدیدتری"))
        assertFalse(File(RestoreApplier.dir(ctx), RestoreApplier.STAGED).exists()) // چیزی برای بازیابی نگه نمی‌دارد
    }

    @Test
    fun backupThenRestoreBringsBackTheSavedData() = runBlocking {
        populate()
        val out = ByteArrayOutputStream().also { backup.writeData(it) }
        // بعد از پشتیبان چیزی اضافه می‌شود؛ بازیابی باید آن را کنار بگذارد.
        customers.create("بعد از پشتیبان")
        assertEquals(2, customers.observeSummaries().first().size)

        val p = backup.inspect { ByteArrayInputStream(out.toByteArray()) }
        assertEquals(RestoreKind.DATA, p.kind)
        assertTrue(backup.commitRestore())
        db.close()
        assertTrue(RestoreApplier.applyIfPending(ctx, DB_NAME))
        assertFalse(File(RestoreApplier.dir(ctx), RestoreApplier.PENDING).exists())

        db = openDb()
        wire()
        val list = customers.observeSummaries().first()
        assertEquals(listOf("آقای رنجبر"), list.map { it.name })
        assertEquals(1, tasks.observeOpenByCustomer(list[0].id).first().size)

        // نسخهٔ پیش از بازیابی هنوز هست و «بعد از پشتیبان» را دارد
        val before = File(RestoreApplier.dir(ctx), RestoreApplier.BEFORE)
        assertTrue(before.exists())
        SQLiteDatabase.openDatabase(before.path, null, SQLiteDatabase.OPEN_READONLY).use {
            val c = it.rawQuery("SELECT COUNT(*) FROM customers WHERE deletedAt IS NULL", null)
            c.moveToFirst()
            assertEquals(2, c.getInt(0))
            c.close()
        }
    }

    @Test
    fun applyDoesNothingWithoutPendingRestore() {
        assertFalse(RestoreApplier.applyIfPending(ctx, DB_NAME))
        assertNull(null)
    }

    @Test
    fun restoreFilesKeepsOnlyUuidNamesInsideTheFilesFolder() = runBlocking {
        val id = UUID.randomUUID().toString()
        val zip = zipOf("../../$id" to byteArrayOf(9, 9), "evil.sh" to byteArrayOf(1), "customers.csv" to byteArrayOf(1))
        val n = backup.restoreFiles { ByteArrayInputStream(zip) }
        assertEquals(1, n)
        assertArrayEquals(byteArrayOf(9, 9), files.file(id).readBytes())
        assertFalse(files.file("evil.sh").exists())
    }
}
