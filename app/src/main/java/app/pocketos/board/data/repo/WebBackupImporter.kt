package app.pocketos.board.data.repo

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.room.withTransaction
import app.pocketos.board.data.FileStore
import app.pocketos.board.data.RestoreApplier
import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.db.CaseEntity
import app.pocketos.board.data.db.CustomerEntity
import app.pocketos.board.data.db.ItemEntity
import app.pocketos.board.data.db.PrefEntity
import app.pocketos.board.data.db.SimilarNoEntity
import app.pocketos.board.data.db.TaskEntity
import app.pocketos.board.domain.CaseStatus
import app.pocketos.board.domain.Normalize
import app.pocketos.board.domain.Phone
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/** خلاصهٔ تبدیل پشتیبان وب‌اپ. */
data class WebPreview(
    val customers: Int = 0,
    val cases: Int = 0,
    val tasks: Int = 0,
    val notes: Int = 0,
    /** فایل‌هایی که پایگاه‌داده به آن‌ها اشاره دارد. */
    val files: Int = 0,
    /** پرونده‌هایی که در وب متقاضی نداشتند ← زیر متقاضیِ «پرونده‌های بدون متقاضی». */
    val orphanCases: Int = 0,
    val users: Int = 0,
    val error: String? = null,
)

/**
 * تبدیل پشتیبان وب‌اپ (Flask/SQLite: `board-data-*.zip` و `board-files-*.zip`) به ساختار این اپ.
 * شناسه‌ها حفظ می‌شود؛ زمان‌های ISO به میلی‌ثانیه؛ وضعیت‌های فارسی به کد؛ فایل‌ها با نام uuid؛
 * حذف‌های نرم همراه صاحبشان (متقاضی/پرونده) طوری ثبت می‌شود که «بازگردانی» اپ درست کار کند.
 */
@Singleton
class WebBackupImporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val files: FileStore,
    private val clock: Clock,
) {
    companion object {
        const val ORPHAN_CUSTOMER = "پرونده‌های بدون متقاضی"
        private const val TEMP_DB = "web-import.db"
        const val FILES_LIST = "web-files.txt"

        /** نام فایلِ وب‌اپ (uuid4.hex + پسوند) ← نام uuid در این اپ؛ همیشه یکسان. */
        fun uuidFor(webPath: String): String = UUID.nameUUIDFromBytes(("web:$webPath").toByteArray()).toString()

        private val STATUS = mapOf(
            "جدید" to CaseStatus.NEW, "در حال انجام" to CaseStatus.DOING, "در انتظار" to CaseStatus.WAITING,
            "نهایی" to CaseStatus.FINAL, "آرشیو" to CaseStatus.ARCHIVE,
        )
    }

    // ── خواندن ساده از SQLite وب ──
    private class Row(val c: Cursor) {
        fun str(n: String): String? = c.getColumnIndex(n).let { if (it < 0 || c.isNull(it)) null else c.getString(it) }
        fun long(n: String): Long? = c.getColumnIndex(n).let { if (it < 0 || c.isNull(it)) null else c.getLong(it) }
        fun dbl(n: String): Double? = c.getColumnIndex(n).let { if (it < 0 || c.isNull(it)) null else c.getDouble(it) }
    }

    private fun SQLiteDatabase.hasTable(name: String): Boolean =
        rawQuery("SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", arrayOf(name)).use { it.moveToFirst() }

    private fun <T> SQLiteDatabase.each(table: String, order: String = "id", f: (Row) -> T): List<T> {
        if (!hasTable(table)) return emptyList()
        val orderBy = if (rawQuery("PRAGMA table_info($table)", null).use { c -> generateSequence { if (c.moveToNext()) c.getString(1) else null }.any { it == order } }) " ORDER BY $order" else ""
        return rawQuery("SELECT * FROM $table$orderBy", null).use { c ->
            val r = Row(c)
            buildList { while (c.moveToNext()) add(f(r)) }
        }
    }

    /** زمان ISO وب‌اپ («2026-11-15T08:30:00Z») ← میلی‌ثانیه. */
    private fun ms(s: String?): Long? {
        if (s.isNullOrBlank()) return null
        return runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(s.removeSuffix("Z")).toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
    }

    private fun isWeb(web: SQLiteDatabase): Boolean =
        listOf("users", "customers", "cases", "items", "tasks").all { web.hasTable(it) } && !web.hasTable("room_master_table")

    /** پایگاه‌دادهٔ وب را به [target] (Room، خالی) منتقل می‌کند. */
    suspend fun convert(web: SQLiteDatabase, target: BoardDatabase): WebPreview {
        val now = clock.millis()
        fun key(vararg parts: String?) = Normalize.key(parts.filterNotNull().joinToString(" "))

        val customers = web.each("customers") { r ->
            val name = Normalize.clean(r.str("name")).ifEmpty { "بدون نام" }
            val phone = Phone.normalize(r.str("phone"))
            val created = ms(r.str("created")) ?: now
            CustomerEntity(
                id = r.long("id")!!, name = name, phone = phone, pinnedAt = ms(r.str("pinned_at")), archivedAt = ms(r.str("archived_at")),
                searchText = key(name, phone), createdAt = created, lastActivity = ms(r.str("last_activity")) ?: created, deletedAt = ms(r.str("deleted_at")),
            )
        }.toMutableList()
        val customerIds = customers.map { it.id }.toMutableSet()
        val customerDeleted = customers.associate { it.id to it.deletedAt }

        // پرونده‌ی بی‌متقاضی ← یک متقاضیِ جایگزین (اپ هر پرونده را زیر یک متقاضی می‌خواهد)
        var orphanId: Long? = null
        fun orphan(): Long {
            orphanId?.let { return it }
            val id = (customerIds.maxOrNull() ?: 0L) + 1
            customers += CustomerEntity(id = id, name = ORPHAN_CUSTOMER, searchText = key(ORPHAN_CUSTOMER), createdAt = now, lastActivity = now)
            customerIds += id
            orphanId = id
            return id
        }

        data class CaseRow(val entity: CaseEntity, val delCustomer: Long?)
        var orphanCases = 0
        val caseRows = web.each("cases") { r ->
            val created = ms(r.str("created")) ?: now
            val cid = r.long("customer_id")?.takeIf { it in customerIds } ?: run { orphanCases++; orphan() }
            val code = r.str("code")?.trim().orEmpty()
            var title = Normalize.clean(r.str("title")).ifEmpty { "بدون عنوان" }
            if (code.isNotEmpty() && code !in title) title = "$title ($code)"
            CaseRow(
                CaseEntity(
                    id = r.long("id")!!, customerId = cid, title = title, status = (STATUS[r.str("status")] ?: CaseStatus.NEW).code,
                    dueDay = r.str("due")?.takeIf { it.isNotBlank() }, searchText = key(title), createdAt = created,
                    lastActivity = ms(r.str("last_activity")) ?: created, deletedAt = ms(r.str("deleted_at")),
                ),
                r.long("del_customer"),
            )
        }
        val caseById = caseRows.associate { it.entity.id to it.entity }
        val caseDeleted = caseById.mapValues { it.value.deletedAt }

        /** حذف نرم: همراه صاحب (پرونده/متقاضی) همان زمان را می‌گیرد تا بازگردانیِ اپ محتوا را برگرداند. */
        fun resolveDeleted(own: Long?, delCase: Long?, delCustomer: Long?): Long? {
            if (own == null) return null
            if (delCase != null) caseDeleted[delCase]?.let { return it }
            if (delCustomer != null) customerDeleted[delCustomer]?.let { return it }
            return own
        }

        val referenced = LinkedHashSet<String>()
        val items = web.each("items") { r ->
            val kindWeb = r.str("kind").orEmpty()
            val mime = r.str("file_mime")
            val webPath = r.str("file_path")?.takeIf { it.isNotBlank() }
            webPath?.let { referenced += it }
            val kind = when {
                kindWeb == "event" -> ItemKind.EVENT
                kindWeb == "location" -> ItemKind.PLACE
                kindWeb == "photo" || (webPath != null && mime?.startsWith("image/") == true) -> ItemKind.PHOTO
                kindWeb == "audio" || (webPath != null && mime?.startsWith("audio/") == true) -> ItemKind.AUDIO
                webPath != null || kindWeb == "file" -> ItemKind.FILE
                else -> ItemKind.TEXT
            }
            val text = r.str("text").orEmpty().ifEmpty { if (kind == ItemKind.PLACE) "موقعیت من" else "" }
            val caseId = r.long("case_id")?.takeIf { it in caseById }
            val customerId = (r.long("customer_id") ?: caseId?.let { caseById[it]?.customerId })?.takeIf { it in customerIds }
            val fileName = r.str("file_name")
            ItemEntity(
                id = r.long("id")!!, kind = kind, customerId = customerId, caseId = caseId, text = text,
                fileName = fileName, filePath = webPath?.let { uuidFor(it) }, mime = mime,
                lat = r.dbl("lat"), lng = r.dbl("lon"), pinnedAt = ms(r.str("pinned_at")),
                searchText = key(text, fileName), createdAt = ms(r.str("created")) ?: now,
                deletedAt = resolveDeleted(ms(r.str("deleted_at")), r.long("del_case"), r.long("del_customer")),
                clientId = r.str("client_id")?.takeIf { it.isNotBlank() },
            )
        }

        val tasks = web.each("tasks") { r ->
            val caseId = r.long("case_id")?.takeIf { it in caseById }
            val customerId = (r.long("customer_id") ?: caseId?.let { caseById[it]?.customerId })?.takeIf { it in customerIds }
            val title = Normalize.clean(r.str("title")).ifEmpty { "بدون عنوان" }
            TaskEntity(
                id = r.long("id")!!, customerId = customerId, caseId = caseId, title = title,
                dueDay = r.str("due")?.takeIf { it.isNotBlank() }, repeat = r.str("repeat")?.takeIf { it.isNotBlank() },
                anchor = r.str("anchor")?.takeIf { it.isNotBlank() }, spawnedBy = r.long("spawned_by"),
                doneAt = ms(r.str("done_at")), searchText = key(title), createdAt = ms(r.str("created")) ?: now,
                deletedAt = resolveDeleted(ms(r.str("deleted_at")), r.long("del_case"), r.long("del_customer")),
            )
        }

        val similar = web.each("similar_no", order = "a") { r -> r.long("a")!! to r.long("b")!! }
            .filter { (a, b) -> a in customerIds && b in customerIds && a != b }
            .map { (a, b) -> SimilarNoEntity(minOf(a, b), maxOf(a, b)) }

        // تنظیمات: کاربر اول (اپ تک‌کاربره است)
        val prefs = ArrayList<PrefEntity>()
        val firstUser = web.each("users") { it.long("id")!! }.firstOrNull()
        if (web.hasTable("prefs")) {
            web.rawQuery("SELECT * FROM prefs WHERE user_id = ?", arrayOf((firstUser ?: 0L).toString())).use { c ->
                if (c.moveToFirst()) {
                    val r = Row(c)
                    fun put(k: String, v: String?) { if (v != null) prefs += PrefEntity(k, v) }
                    put(PrefRepository.EVENING_HOUR, r.long("evening_hour")?.toString())
                    put(PrefRepository.THEME, r.str("theme")?.takeIf { it in listOf("auto", "dark", "light") })
                    put(PrefRepository.FSIZE, r.str("fsize")?.takeIf { it in listOf("sm", "md", "lg", "xl") })
                    r.long("morning_on")?.let { put(PrefRepository.MORNING_ON, if (it != 0L) "1" else "0") }
                    put(PrefRepository.MORNING_HOUR, r.long("morning_hour")?.toString())
                    r.long("evening_push")?.let { put(PrefRepository.EVENING_PUSH, if (it != 0L) "1" else "0") }
                    r.long("push_names")?.let { put(PrefRepository.PUSH_NAMES, if (it != 0L) "1" else "0") }
                    r.long("backup_remind")?.let { put(PrefRepository.BACKUP_REMIND, if (it != 0L) "1" else "0") }
                }
            }
        }
        if (web.hasTable("state")) {
            web.rawQuery("SELECT * FROM state WHERE user_id = ?", arrayOf((firstUser ?: 0L).toString())).use { c ->
                if (c.moveToFirst()) {
                    val r = Row(c)
                    r.long("active_case_id")?.takeIf { it in caseById && caseDeleted[it] == null }?.let { prefs += PrefEntity(PrefRepository.ACTIVE_CASE, it.toString()) }
                    r.long("active_customer_id")?.takeIf { it in customerIds && customerDeleted[it] == null }?.let { prefs += PrefEntity(PrefRepository.ACTIVE_CUSTOMER, it.toString()) }
                }
            }
        }

        target.withTransaction {
            customers.forEach { target.customerDao().insert(it) }
            caseRows.forEach { target.caseDao().insert(it.entity) }
            items.forEach { target.itemDao().insert(it) }
            tasks.forEach { target.taskDao().insert(it) }
            similar.forEach { target.customerDao().insertNotSimilar(it) }
            prefs.forEach { target.prefDao().put(it) }
        }

        // فهرست فایل‌های ارجاع‌شده برای مرحلهٔ دوم (انتخاب ZIP فایل‌ها)
        File(RestoreApplier.dir(context).also { it.mkdirs() }, FILES_LIST).writeText(referenced.joinToString("\n"))

        return WebPreview(
            customers = customers.count { it.deletedAt == null },
            cases = caseRows.count { it.entity.deletedAt == null },
            tasks = tasks.count { it.deletedAt == null },
            notes = items.count { it.deletedAt == null && it.kind != ItemKind.EVENT },
            files = referenced.size,
            orphanCases = orphanCases,
            users = web.each("users") { 1 }.size,
        )
    }

    /**
     * ZIP «داده‌ها»ی وب‌اپ را می‌خواند، به ساختار این اپ تبدیل می‌کند و برای بازیابی آماده نگه می‌دارد
     * (`BackupService.commitRestore` قطعی‌اش می‌کند). اگر ZIP پشتیبان وب‌اپ نبود null.
     */
    suspend fun prepare(open: () -> InputStream): WebPreview? = withContext(Dispatchers.IO) {
        val cache = File(context.cacheDir, "webimport").also { it.deleteRecursively(); it.mkdirs() }
        val webFile = File(cache, "web.db")
        var found = false
        try {
            ZipInputStream(open().buffered()).use { zin ->
                var e = zin.nextEntry
                while (e != null && !found) {
                    if (!e.isDirectory && e.name == "board.db") {
                        webFile.outputStream().use { zin.copyTo(it) }
                        found = true
                    }
                    e = zin.nextEntry
                }
            }
        } catch (_: Exception) {
            return@withContext null
        }
        if (!found) return@withContext null
        val web = try {
            SQLiteDatabase.openDatabase(webFile.path, null, SQLiteDatabase.OPEN_READWRITE)
        } catch (_: Exception) {
            return@withContext null
        }
        try {
            if (!isWeb(web)) return@withContext null
            context.deleteDatabase(TEMP_DB)
            val target = Room.databaseBuilder(context, BoardDatabase::class.java, TEMP_DB).build()
            try {
                val preview = convert(web, target)
                target.close()
                val stagedDir = RestoreApplier.dir(context).also { it.mkdirs() }
                val staged = File(stagedDir, RestoreApplier.STAGED)
                File(stagedDir, RestoreApplier.PENDING).delete()
                context.getDatabasePath(TEMP_DB).copyTo(staged, overwrite = true)
                preview
            } catch (e: Exception) {
                runCatching { target.close() }
                WebPreview(error = "تبدیل پشتیبان وب‌اپ انجام نشد: ${e.message ?: "خطای نامشخص"}")
            } finally {
                context.deleteDatabase(TEMP_DB)
            }
        } finally {
            runCatching { web.close() }
            cache.deleteRecursively()
        }
    }

    /** چند فایلِ ارجاع‌شده در این ZIP هست؟ (پیدا شده، ارجاع‌شده) بدون کپی. */
    suspend fun countFiles(open: () -> InputStream): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val list = File(RestoreApplier.dir(context), FILES_LIST)
        val wanted = if (list.exists()) list.readLines().filter { it.isNotBlank() }.toSet() else emptySet()
        var found = 0
        ZipInputStream(open().buffered()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                if (!e.isDirectory && File(e.name).name in wanted) found++
                zin.closeEntry()
                e = zin.nextEntry
            }
        }
        found to wanted.size
    }

    /** فایل‌های ZIP «فایل‌ها»ی وب‌اپ را با نام uuid به حافظهٔ اپ می‌برد. (کپی‌شده، ارجاع‌شده) */
    suspend fun importFiles(open: () -> InputStream): Pair<Int, Int> = withContext(Dispatchers.IO) {
        val list = File(RestoreApplier.dir(context), FILES_LIST)
        val wanted = if (list.exists()) list.readLines().filter { it.isNotBlank() }.toSet() else emptySet()
        var copied = 0
        ZipInputStream(open().buffered()).use { zin ->
            var e = zin.nextEntry
            while (e != null) {
                val name = File(e.name).name
                if (!e.isDirectory && name in wanted) {
                    files.file(uuidFor(name)).outputStream().use { zin.copyTo(it) }
                    copied++
                }
                zin.closeEntry()
                e = zin.nextEntry
            }
        }
        copied to wanted.size
    }
}
