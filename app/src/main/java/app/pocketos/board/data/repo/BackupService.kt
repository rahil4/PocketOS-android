package app.pocketos.board.data.repo

import android.content.Context
import android.database.Cursor
import android.database.DatabaseUtils
import android.database.sqlite.SQLiteDatabase
import app.pocketos.board.data.FileStore
import app.pocketos.board.data.RestoreApplier
import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.db.DB_VERSION
import app.pocketos.board.domain.BackupFormat
import app.pocketos.board.domain.CaseStatus
import app.pocketos.board.domain.Csv
import app.pocketos.board.domain.Jalali
import app.pocketos.board.domain.When
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

enum class RestoreKind { DATA, FILES, INVALID }

/** خلاصهٔ آنچه در یک ZIP پشتیبان هست (پیش از بازیابی نشان داده می‌شود). */
data class RestorePreview(
    val kind: RestoreKind,
    val customers: Int = 0,
    val cases: Int = 0,
    val tasks: Int = 0,
    val notes: Int = 0,
    val files: Int = 0,
    val createdAt: Long? = null,
    val error: String? = null,
)

private class CountingOutputStream(out: OutputStream) : FilterOutputStream(out) {
    var count = 0L
        private set

    override fun write(b: Int) { out.write(b); count++ }
    override fun write(b: ByteArray, off: Int, len: Int) { out.write(b, off, len); count += len }
}

/**
 * پشتیبان و بازیابی (همان `backup_data` و `backup_files` وب‌اپ + بازیابی درون اپ).
 * «داده‌ها»: board.db + CSVهای قابل‌خواندن + راهنما. «فایل‌ها»: عکس/صدا/فایل بایت‌به‌بایت.
 */
@Singleton
class BackupService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val db: BoardDatabase,
    private val files: FileStore,
    private val prefs: PrefRepository,
    private val clock: Clock,
) {
    private val dataEntries = setOf(BackupFormat.DB_ENTRY, BackupFormat.README_ENTRY, BackupFormat.INFO_ENTRY)

    /** فایل‌های اپ فقط با نام uuid ذخیره می‌شوند؛ هر ورودیِ دیگر در ZIP «فایل» حساب نمی‌شود. */
    private val fileName = Regex("[0-9a-fA-F-]{36}")

    private fun zoneDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(clock.zone).toLocalDate()

    suspend fun markBackedUp() = prefs.setString(PrefRepository.LAST_BACKUP, LocalDate.now(clock).toString())

    // ── ساخت ──
    /** یک نسخهٔ سازگار از پایگاه‌داده (حتی اگر همزمان چیزی ثبت شود): نوشتن‌ها فقط هنگام کپی کوتاه معطل می‌شوند. */
    private fun snapshotDb(): File {
        db.query("PRAGMA wal_checkpoint(FULL)", null).use { it.moveToFirst() }
        val src = context.getDatabasePath(db.openHelper.databaseName ?: BoardDatabase.NAME)
        val tmp = File.createTempFile("snapshot", ".db", context.cacheDir)
        db.runInTransaction { src.inputStream().use { i -> tmp.outputStream().use { o -> i.copyTo(o) } } }
        return tmp
    }

    private fun Cursor.str(i: Int) = if (isNull(i)) "" else getString(i)

    private fun csvDate(millis: Long?) = millis?.let { Jalali.format(zoneDate(it)) } ?: ""

    private fun csvs(): List<Pair<String, ByteArray>> {
        val sdb = db.openHelper.readableDatabase
        fun rows(sql: String, map: (Cursor) -> List<String>): List<List<String>> =
            sdb.query(sql).use { c -> buildList { while (c.moveToNext()) add(map(c)) } }
        return listOf(
            "customers.csv" to Csv.encode(
                listOf("شناسه", "نام", "تلفن", "ساخته‌شده"),
                rows("SELECT id, name, phone, createdAt FROM customers WHERE deletedAt IS NULL ORDER BY id") {
                    listOf(it.str(0), it.str(1), it.str(2), csvDate(it.getLong(3)))
                },
            ),
            "cases.csv" to Csv.encode(
                listOf("شناسه", "مشتری", "عنوان", "وضعیت", "مهلت"),
                rows(
                    "SELECT k.id, c.name, k.title, k.status, k.dueDay FROM cases k LEFT JOIN customers c ON c.id = k.customerId " +
                        "WHERE k.deletedAt IS NULL ORDER BY k.id",
                ) { listOf(it.str(0), it.str(1), it.str(2), CaseStatus.of(it.str(3)).label, it.str(4)) },
            ),
            "tasks.csv" to Csv.encode(
                listOf("شناسه", "مشتری", "پرونده", "عنوان", "روز", "انجام‌شده", "تکرار"),
                rows(
                    "SELECT t.id, c.name, k.title, t.title, t.dueDay, t.doneAt, t.repeat FROM tasks t " +
                        "LEFT JOIN customers c ON c.id = t.customerId LEFT JOIN cases k ON k.id = t.caseId " +
                        "WHERE t.deletedAt IS NULL ORDER BY t.id",
                ) {
                    listOf(it.str(0), it.str(1), it.str(2), it.str(3), it.str(4), csvDate(if (it.isNull(5)) null else it.getLong(5)), When.repeatLabel(it.str(6).ifEmpty { null }))
                },
            ),
            "notes.csv" to Csv.encode(
                listOf("شناسه", "مشتری", "پرونده", "نوع", "متن", "نام فایل", "زمان"),
                rows(
                    "SELECT i.id, c.name, k.title, i.kind, i.text, i.fileName, i.createdAt FROM items i " +
                        "LEFT JOIN customers c ON c.id = i.customerId LEFT JOIN cases k ON k.id = i.caseId " +
                        "WHERE i.deletedAt IS NULL AND i.kind != 'event' ORDER BY i.id",
                ) { listOf(it.str(0), it.str(1), it.str(2), it.str(3), it.str(4), it.str(5), csvDate(it.getLong(6))) },
            ),
        )
    }

    private fun ZipOutputStream.bytes(name: String, data: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(data)
        closeEntry()
    }

    /** پشتیبان «داده‌ها»؛ تعداد بایت‌های نوشته‌شده را برمی‌گرداند. */
    suspend fun writeData(out: OutputStream): Long = withContext(Dispatchers.IO) {
        val snap = snapshotDb()
        try {
            val counting = CountingOutputStream(out)
            ZipOutputStream(BufferedOutputStream(counting)).use { zip ->
                zip.putNextEntry(ZipEntry(BackupFormat.DB_ENTRY))
                snap.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                zip.bytes(BackupFormat.README_ENTRY, BackupFormat.README.toByteArray(Charsets.UTF_8))
                csvs().forEach { (name, data) -> zip.bytes(name, data) }
                zip.bytes(BackupFormat.INFO_ENTRY, """{"format":1,"dbVersion":$DB_VERSION,"created":${clock.millis()}}""".toByteArray())
            }
            markBackedUp()
            counting.count
        } finally {
            snap.delete()
        }
    }

    /** پشتیبان «عکس‌ها و فایل‌ها»: بایت‌به‌بایت، بدون فشرده‌سازی. (تعداد، بایت) */
    suspend fun writeFiles(out: OutputStream): Pair<Int, Long> = withContext(Dispatchers.IO) {
        val counting = CountingOutputStream(out)
        var n = 0
        val dir = files.file("x").parentFile!!
        ZipOutputStream(BufferedOutputStream(counting)).use { zip ->
            zip.setLevel(Deflater.NO_COMPRESSION)
            dir.listFiles()?.filter { it.isFile }?.sortedBy { it.name }?.forEach { f ->
                zip.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                n++
            }
        }
        markBackedUp()
        n to counting.count
    }

    // ── بازیابی ──
    private fun validate(staged: File): RestorePreview {
        var sdb: SQLiteDatabase? = null
        try {
            val d = SQLiteDatabase.openDatabase(staged.path, null, SQLiteDatabase.OPEN_READWRITE)
            sdb = d
            val v = DatabaseUtils.longForQuery(d, "PRAGMA user_version", null).toInt()
            if (v > DB_VERSION) return RestorePreview(RestoreKind.INVALID, error = "این پشتیبان با نسخهٔ جدیدتری از دفتر ساخته شده؛ اپ را به‌روز کنید.")
            if (v < DB_VERSION) return RestorePreview(RestoreKind.INVALID, error = "این پشتیبان مال نسخه‌ای قدیمی‌تر از ساختار داده است و پشتیبانی نمی‌شود.")
            if (DatabaseUtils.stringForQuery(d, "PRAGMA integrity_check", null) != "ok") {
                return RestorePreview(RestoreKind.INVALID, error = "پایگاه‌دادهٔ داخل پشتیبان سالم نیست.")
            }
            fun n(sql: String) = DatabaseUtils.longForQuery(d, sql, null).toInt()
            return RestorePreview(
                RestoreKind.DATA,
                customers = n("SELECT COUNT(*) FROM customers WHERE deletedAt IS NULL"),
                cases = n("SELECT COUNT(*) FROM cases WHERE deletedAt IS NULL"),
                tasks = n("SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL"),
                notes = n("SELECT COUNT(*) FROM items WHERE deletedAt IS NULL AND kind != 'event'"),
            )
        } catch (_: Exception) {
            return RestorePreview(RestoreKind.INVALID, error = "این فایل، پشتیبان دفتر نیست.")
        } finally {
            runCatching { sdb?.close() }
            File(staged.path + "-wal").delete()
            File(staged.path + "-shm").delete()
        }
    }

    /**
     * یک ZIP را می‌خواند (بدون تغییر در داده‌های فعلی) و خلاصه‌اش را برمی‌گرداند. اگر «داده‌ها» بود،
     * پایگاه‌داده برای بازیابی کنار گذاشته می‌شود تا [commitRestore] آن را قطعی کند.
     */
    suspend fun inspect(open: () -> InputStream): RestorePreview = withContext(Dispatchers.IO) {
        val dir = RestoreApplier.dir(context).also { it.mkdirs() }
        val staged = File(dir, RestoreApplier.STAGED)
        staged.delete()
        var hasDb = false
        var fileCount = 0
        var created: Long? = null
        try {
            ZipInputStream(open().buffered()).use { zin ->
                var e = zin.nextEntry
                while (e != null) {
                    val name = e.name
                    when {
                        e.isDirectory -> Unit
                        name == BackupFormat.DB_ENTRY -> {
                            staged.outputStream().use { zin.copyTo(it) }
                            hasDb = true
                        }
                        name == BackupFormat.INFO_ENTRY ->
                            created = Regex("\"created\":(\\d+)").find(zin.readBytes().toString(Charsets.UTF_8))?.groupValues?.get(1)?.toLongOrNull()
                        name in dataEntries || name.endsWith(".csv") -> Unit
                        fileName.matches(File(name).name) -> fileCount++
                    }
                    zin.closeEntry()
                    e = zin.nextEntry
                }
            }
        } catch (_: Exception) {
            staged.delete()
            return@withContext RestorePreview(RestoreKind.INVALID, error = "این فایل ZIP سالم نیست.")
        }
        when {
            hasDb -> validate(staged).copy(createdAt = created).also { if (it.kind != RestoreKind.DATA) staged.delete() }
            fileCount > 0 -> RestorePreview(RestoreKind.FILES, files = fileCount, createdAt = created)
            else -> RestorePreview(RestoreKind.INVALID, error = "این فایل، پشتیبان دفتر نیست.")
        }
    }

    /** پایگاه‌داده‌ای که [inspect] آماده کرد قطعی می‌شود؛ جایگزینی در شروع بعدی برنامه انجام می‌شود. */
    fun commitRestore(): Boolean {
        val dir = RestoreApplier.dir(context)
        val staged = File(dir, RestoreApplier.STAGED)
        if (!staged.exists()) return false
        val pending = File(dir, RestoreApplier.PENDING)
        pending.delete()
        return staged.renameTo(pending)
    }

    /** فایل‌های یک ZIP «فایل‌ها» را به حافظهٔ اپ برمی‌گرداند (موجودها رونویسی می‌شوند). تعداد را برمی‌گرداند. */
    suspend fun restoreFiles(open: () -> InputStream): Int = withContext(Dispatchers.IO) {
        var n = 0
        ZipInputStream(open().buffered()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                val name = File(e.name).name // فقط نام؛ مسیرِ ../ هرگز
                if (!e.isDirectory && fileName.matches(name)) {
                    files.file(name).outputStream().use { zin.copyTo(it) }
                    n++
                }
                zin.closeEntry()
                e = zin.nextEntry
            }
        }
        n
    }
}
