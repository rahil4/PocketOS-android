package app.pocketos.board.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** یک ردیف فهرست متقاضی‌ها با شمارش‌ها. */
data class CustomerRow(
    @Embedded val customer: CustomerEntity,
    val openCases: Int,
    val openTasks: Int,
    val taskTotal: Int,
    val caseTotal: Int,
    val itemCount: Int,
    val nextDue: String?,
)

@Dao
interface CustomerDao {
    @Query(
        """
        SELECT c.*,
          (SELECT COUNT(*) FROM cases k WHERE k.customerId = c.id AND k.deletedAt IS NULL
              AND k.status NOT IN ('final', 'archive')) AS openCases,
          (SELECT COUNT(*) FROM cases k WHERE k.customerId = c.id AND k.deletedAt IS NULL) AS caseTotal,
          (SELECT COUNT(*) FROM tasks t WHERE t.customerId = c.id AND t.deletedAt IS NULL
              AND t.doneAt IS NULL) AS openTasks,
          (SELECT COUNT(*) FROM tasks t WHERE t.customerId = c.id AND t.deletedAt IS NULL) AS taskTotal,
          (SELECT COUNT(*) FROM items i WHERE i.customerId = c.id AND i.deletedAt IS NULL) AS itemCount,
          (SELECT MIN(t.dueDay) FROM tasks t WHERE t.customerId = c.id AND t.deletedAt IS NULL
              AND t.doneAt IS NULL AND t.dueDay IS NOT NULL) AS nextDue
        FROM customers c WHERE c.deletedAt IS NULL
        """,
    )
    fun observeRows(): Flow<List<CustomerRow>>

    @Query("SELECT * FROM customers WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: Long): Flow<CustomerEntity?>

    @Query("SELECT * FROM customers WHERE deletedAt IS NULL ORDER BY lastActivity DESC")
    fun observeActive(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id")
    suspend fun get(id: Long): CustomerEntity?

    @Query("SELECT * FROM customers WHERE deletedAt IS NULL")
    suspend fun allActive(): List<CustomerEntity>

    @Insert
    suspend fun insert(c: CustomerEntity): Long

    @Update
    suspend fun update(c: CustomerEntity)

    /** هر فعالیتی، بایگانی را برمی‌دارد. */
    @Query("UPDATE customers SET lastActivity = :now, archivedAt = NULL WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("UPDATE customers SET pinnedAt = :at WHERE id = :id")
    suspend fun setPinned(id: Long, at: Long?)

    @Query("UPDATE customers SET archivedAt = :at WHERE id = :id")
    suspend fun setArchived(id: Long, at: Long?)

    @Query("UPDATE customers SET deletedAt = :ts WHERE id = :id")
    suspend fun setDeleted(id: Long, ts: Long?)

    @Query("SELECT a, b FROM similar_no")
    fun observeNotSimilar(): Flow<List<SimilarNoEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNotSimilar(e: SimilarNoEntity)
}

@Dao
interface CaseDao {
    @Query("SELECT * FROM cases WHERE customerId = :customerId AND deletedAt IS NULL ORDER BY lastActivity DESC")
    fun observeByCustomer(customerId: Long): Flow<List<CaseEntity>>

    @Query(
        "SELECT * FROM cases WHERE status NOT IN ('final', 'archive') AND deletedAt IS NULL ORDER BY lastActivity DESC",
    )
    fun observeOpen(): Flow<List<CaseEntity>>

    @Query("SELECT * FROM cases WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: Long): Flow<CaseEntity?>

    @Query("SELECT * FROM cases WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeDeleted(): Flow<List<CaseEntity>>

    @Query("SELECT * FROM cases WHERE id = :id")
    suspend fun get(id: Long): CaseEntity?

    @Insert
    suspend fun insert(c: CaseEntity): Long

    @Update
    suspend fun update(c: CaseEntity)

    @Query("UPDATE cases SET deletedAt = :ts WHERE id = :id")
    suspend fun setDeleted(id: Long, ts: Long?)

    @Query("UPDATE cases SET lastActivity = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query(
        """SELECT k.id AS id, k.title AS title, c.name AS customerName, k.status AS status
           FROM cases k LEFT JOIN customers c ON c.id = k.customerId
           WHERE k.deletedAt IS NULL AND k.status NOT IN ('final', 'archive') ORDER BY k.lastActivity DESC LIMIT :limit""",
    )
    suspend fun openBrief(limit: Int): List<OpenCaseBrief>

    @Query("SELECT COUNT(*) FROM cases WHERE customerId = :customerId AND deletedAt IS NULL AND status NOT IN ('final', 'archive')")
    suspend fun countOpenOfCustomer(customerId: Long): Int

    @Query(
        """SELECT k.id AS id, k.title AS title, k.customerId AS customerId, c.name AS customerName,
                  k.status AS status, k.dueDay AS dueDay, k.lastActivity AS lastActivity
           FROM cases k JOIN customers c ON c.id = k.customerId
           WHERE k.deletedAt IS NULL AND c.deletedAt IS NULL AND k.status NOT IN ('final', 'archive')""",
    )
    fun observeOpenForAttention(): Flow<List<OpenCaseRow>>

    @Query(
        """SELECT k.id AS id, k.title AS title, k.customerId AS customerId, c.name AS customerName
           FROM cases k JOIN customers c ON c.id = k.customerId
           WHERE k.deletedAt IS NULL AND c.deletedAt IS NULL AND (k.searchText LIKE :pattern OR c.searchText LIKE :pattern)
           ORDER BY k.lastActivity DESC LIMIT 100""",
    )
    suspend fun searchCases(pattern: String): List<RecentCase>

    @Query(
        """SELECT k.id AS id, k.title AS title, k.customerId AS customerId, c.name AS customerName, k.dueDay AS dueDay
           FROM cases k JOIN customers c ON c.id = k.customerId
           WHERE k.deletedAt IS NULL AND c.deletedAt IS NULL AND k.dueDay IS NOT NULL
             AND k.status NOT IN ('final', 'archive') AND k.dueDay >= :from AND k.dueDay <= :to
           ORDER BY k.dueDay""",
    )
    fun observeDueRange(from: String, to: String): Flow<List<CaseDueRow>>

    @Query(
        """SELECT k.id AS id, k.title AS title, k.customerId AS customerId, c.name AS customerName, k.dueDay AS dueDay
           FROM cases k JOIN customers c ON c.id = k.customerId
           WHERE k.deletedAt IS NULL AND c.deletedAt IS NULL AND k.dueDay IS NOT NULL
             AND k.status NOT IN ('final', 'archive') AND k.dueDay < :before
           ORDER BY k.dueDay LIMIT 100""",
    )
    fun observeDueBefore(before: String): Flow<List<CaseDueRow>>

    @Query(
        """SELECT k.id AS id, k.title AS title, k.customerId AS customerId, c.name AS customerName, k.dueDay AS dueDay
           FROM cases k JOIN customers c ON c.id = k.customerId
           WHERE k.deletedAt IS NULL AND c.deletedAt IS NULL AND k.dueDay IS NOT NULL
             AND k.status NOT IN ('final', 'archive') AND (k.searchText LIKE :pattern OR c.searchText LIKE :pattern)
           ORDER BY k.dueDay LIMIT 100""",
    )
    suspend fun searchDue(pattern: String): List<CaseDueRow>

    @Query(
        """SELECT k.id AS id, k.title AS title, k.customerId AS customerId, c.name AS customerName
           FROM cases k JOIN customers c ON c.id = k.customerId
           WHERE k.deletedAt IS NULL AND c.deletedAt IS NULL AND k.status NOT IN ('final', 'archive')
           ORDER BY k.lastActivity DESC LIMIT :limit""",
    )
    fun observeRecent(limit: Int): Flow<List<RecentCase>>
}

/**
 * عملیات دسته‌جمعی روی چند جدول (حذف/بازگردانی نرم، ادغام، پاک‌سازی سطل).
 * حذف نرمِ محتوا همان زمانِ حذفِ صاحبش را می‌گیرد؛ بازگردانی فقط همان‌ها را برمی‌گرداند.
 */
@Dao
interface BulkDao {
    @Query("UPDATE cases SET deletedAt = :ts WHERE customerId = :cid AND deletedAt IS NULL")
    suspend fun deleteCasesOfCustomer(cid: Long, ts: Long)

    @Query("UPDATE items SET deletedAt = :ts WHERE customerId = :cid AND deletedAt IS NULL")
    suspend fun deleteItemsOfCustomer(cid: Long, ts: Long)

    @Query("UPDATE tasks SET deletedAt = :ts WHERE customerId = :cid AND deletedAt IS NULL")
    suspend fun deleteTasksOfCustomer(cid: Long, ts: Long)

    @Query("UPDATE cases SET deletedAt = NULL WHERE customerId = :cid AND deletedAt = :ts")
    suspend fun restoreCasesOfCustomer(cid: Long, ts: Long)

    @Query("UPDATE items SET deletedAt = NULL WHERE customerId = :cid AND deletedAt = :ts")
    suspend fun restoreItemsOfCustomer(cid: Long, ts: Long)

    @Query("UPDATE tasks SET deletedAt = NULL WHERE customerId = :cid AND deletedAt = :ts")
    suspend fun restoreTasksOfCustomer(cid: Long, ts: Long)

    @Query("UPDATE items SET deletedAt = :ts WHERE caseId = :caseId AND deletedAt IS NULL")
    suspend fun deleteItemsOfCase(caseId: Long, ts: Long)

    @Query("UPDATE tasks SET deletedAt = :ts WHERE caseId = :caseId AND deletedAt IS NULL")
    suspend fun deleteTasksOfCase(caseId: Long, ts: Long)

    @Query("UPDATE items SET deletedAt = NULL WHERE caseId = :caseId AND deletedAt = :ts")
    suspend fun restoreItemsOfCase(caseId: Long, ts: Long)

    @Query("UPDATE tasks SET deletedAt = NULL WHERE caseId = :caseId AND deletedAt = :ts")
    suspend fun restoreTasksOfCase(caseId: Long, ts: Long)

    @Query("UPDATE cases SET customerId = :to WHERE customerId = :from")
    suspend fun moveCases(from: Long, to: Long)

    @Query("UPDATE items SET customerId = :to WHERE customerId = :from")
    suspend fun moveItems(from: Long, to: Long)

    @Query("UPDATE tasks SET customerId = :to WHERE customerId = :from")
    suspend fun moveTasks(from: Long, to: Long)

    @Query("UPDATE items SET customerId = :to WHERE caseId = :caseId")
    suspend fun moveItemsOfCase(caseId: Long, to: Long)

    @Query("UPDATE tasks SET customerId = :to WHERE caseId = :caseId")
    suspend fun moveTasksOfCase(caseId: Long, to: Long)

    @Query("DELETE FROM items WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeItems(cutoff: Long)

    @Query("DELETE FROM tasks WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeTasks(cutoff: Long)

    @Query("DELETE FROM cases WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeCases(cutoff: Long)

    @Query("DELETE FROM customers WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff")
    suspend fun purgeCustomers(cutoff: Long)
}

/** کار همراه نام متقاضی/پرونده برای تقویم و جست‌وجو. */
data class CalTaskRow(
    @Embedded val task: TaskEntity,
    val customerName: String?,
    val caseTitle: String?,
    val customerSearch: String?,
    val caseSearch: String?,
)

/** مهلت پروندهٔ باز. */
data class CaseDueRow(val id: Long, val title: String, val customerId: Long, val customerName: String, val dueDay: String)

/** یادداشت همراه نام متقاضی (نتیجهٔ جست‌وجو). */
data class NoteRow(@Embedded val item: ItemEntity, val customerName: String?)

/** پروندهٔ باز با اطلاعات لازم برای «نیاز به توجه». */
data class OpenCaseRow(
    val id: Long,
    val title: String,
    val customerId: Long,
    val customerName: String,
    val status: String,
    val dueDay: String?,
    val lastActivity: Long,
)

data class CustomerCount(val id: Long, val name: String, val n: Int)

/** پروندهٔ باز با نام متقاضی و وضعیت (پاسخ دستیار). */
data class OpenCaseBrief(val id: Long, val title: String, val customerName: String?, val status: String)

data class KindCount(val kind: String, val n: Int)

/** پروندهٔ باز همراه نام متقاضی، برای انتخاب‌گر «ثبت در». */
data class RecentCase(val id: Long, val title: String, val customerId: Long, val customerName: String)

@Dao
interface ItemDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(i: ItemEntity): Long

    @Update
    suspend fun update(i: ItemEntity)

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: Long): ItemEntity?

    @Query("SELECT * FROM items WHERE id = :id AND deletedAt IS NULL")
    fun observe(id: Long): Flow<ItemEntity?>

    @Query(
        """SELECT * FROM items WHERE deletedAt IS NULL AND pinnedAt IS NOT NULL
           AND (:customerId IS NULL OR customerId = :customerId) AND (:caseId IS NULL OR caseId = :caseId)
           ORDER BY pinnedAt DESC""",
    )
    fun observePinned(customerId: Long?, caseId: Long?): Flow<List<ItemEntity>>

    @Query(
        """SELECT * FROM items WHERE deletedAt IS NULL AND pinnedAt IS NULL
           AND (:customerId IS NULL OR customerId = :customerId) AND (:caseId IS NULL OR caseId = :caseId)
           AND (:kind IS NULL OR kind = :kind)
           ORDER BY createdAt DESC, id DESC LIMIT :limit""",
    )
    fun observeStream(customerId: Long?, caseId: Long?, kind: String?, limit: Int): Flow<List<ItemEntity>>

    @Query(
        """SELECT kind, COUNT(*) AS n FROM items WHERE deletedAt IS NULL
           AND (:customerId IS NULL OR customerId = :customerId) AND (:caseId IS NULL OR caseId = :caseId)
           GROUP BY kind""",
    )
    fun observeKindCounts(customerId: Long?, caseId: Long?): Flow<List<KindCount>>

    @Query(
        """SELECT * FROM items WHERE customerId IS NULL AND caseId IS NULL AND deletedAt IS NULL
           ORDER BY createdAt DESC, id DESC LIMIT 80""",
    )
    fun observeInbox(): Flow<List<ItemEntity>>

    @Query("SELECT COUNT(*) FROM items WHERE customerId IS NULL AND caseId IS NULL AND deletedAt IS NULL AND kind != 'event'")
    fun observeInboxCount(): Flow<Int>

    @Query("UPDATE items SET deletedAt = :ts WHERE id = :id")
    suspend fun setDeleted(id: Long, ts: Long?)

    @Query("UPDATE items SET pinnedAt = :at WHERE id = :id")
    suspend fun setPinned(id: Long, at: Long?)

    @Query("UPDATE items SET customerId = :customerId, caseId = :caseId WHERE id = :id")
    suspend fun setTarget(id: Long, customerId: Long?, caseId: Long?)

    @Query("DELETE FROM items WHERE clientId = :clientId")
    suspend fun deleteByClientId(clientId: String)

    @Query("SELECT searchText FROM items WHERE deletedAt IS NULL AND searchText LIKE '%#%'")
    suspend fun tagTexts(): List<String>

    @Query("SELECT * FROM items WHERE deletedAt IS NULL AND searchText LIKE :pattern ORDER BY createdAt DESC LIMIT 300")
    suspend fun searchLike(pattern: String): List<ItemEntity>

    @Query(
        """SELECT i.*, c.name AS customerName FROM items i LEFT JOIN customers c ON c.id = i.customerId
           WHERE i.deletedAt IS NULL AND i.kind != 'event' AND i.searchText LIKE :pattern
           ORDER BY i.createdAt DESC LIMIT 200""",
    )
    suspend fun searchNotes(pattern: String): List<NoteRow>

    @Query(
        """SELECT i.*, c.name AS customerName FROM items i LEFT JOIN customers c ON c.id = i.customerId
           WHERE i.deletedAt IS NULL AND i.kind = 'text' AND i.text != '' ORDER BY i.createdAt DESC, i.id DESC LIMIT :limit""",
    )
    suspend fun recentTextNotes(limit: Int): List<NoteRow>

    @Query("SELECT COUNT(*) FROM items WHERE deletedAt IS NULL AND kind != 'event' AND createdAt >= :since")
    suspend fun countCreatedSince(since: Long): Int

    @Query("SELECT filePath FROM items WHERE deletedAt IS NOT NULL AND deletedAt < :cutoff AND filePath IS NOT NULL")
    suspend fun purgeablePaths(cutoff: Long): List<String>
}

@Dao
interface TaskDao {
    @Insert
    suspend fun insert(t: TaskEntity): Long

    @Update
    suspend fun update(t: TaskEntity)

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun get(id: Long): TaskEntity?

    @Query(
        """SELECT * FROM tasks WHERE customerId = :customerId AND deletedAt IS NULL AND doneAt IS NULL
           ORDER BY dueDay IS NULL, dueDay, createdAt""",
    )
    fun observeOpenByCustomer(customerId: Long): Flow<List<TaskEntity>>

    @Query(
        """SELECT * FROM tasks WHERE caseId = :caseId AND deletedAt IS NULL AND doneAt IS NULL
           ORDER BY dueDay IS NULL, dueDay, createdAt""",
    )
    fun observeOpenByCase(caseId: Long): Flow<List<TaskEntity>>

    @Query(
        """SELECT t.*, c.name AS customerName, k.title AS caseTitle, c.searchText AS customerSearch, k.searchText AS caseSearch
           FROM tasks t LEFT JOIN customers c ON c.id = t.customerId LEFT JOIN cases k ON k.id = t.caseId
           WHERE t.deletedAt IS NULL AND (c.id IS NULL OR c.deletedAt IS NULL)
             AND t.dueDay >= :from AND t.dueDay <= :to AND (:like IS NULL OR t.searchText LIKE :like)
           ORDER BY t.dueDay, t.doneAt IS NOT NULL, t.createdAt LIMIT :limit""",
    )
    fun observeCalRange(from: String, to: String, like: String?, limit: Int): Flow<List<CalTaskRow>>

    @Query(
        """SELECT t.*, c.name AS customerName, k.title AS caseTitle, c.searchText AS customerSearch, k.searchText AS caseSearch
           FROM tasks t LEFT JOIN customers c ON c.id = t.customerId LEFT JOIN cases k ON k.id = t.caseId
           WHERE t.deletedAt IS NULL AND (c.id IS NULL OR c.deletedAt IS NULL) AND t.doneAt IS NULL
             AND t.dueDay < :before AND (:like IS NULL OR t.searchText LIKE :like)
           ORDER BY t.dueDay LIMIT 100""",
    )
    fun observeOverdue(before: String, like: String?): Flow<List<CalTaskRow>>

    @Query(
        """SELECT t.*, c.name AS customerName, k.title AS caseTitle, c.searchText AS customerSearch, k.searchText AS caseSearch
           FROM tasks t LEFT JOIN customers c ON c.id = t.customerId LEFT JOIN cases k ON k.id = t.caseId
           WHERE t.deletedAt IS NULL AND (c.id IS NULL OR c.deletedAt IS NULL)
             AND (t.searchText LIKE :pattern OR c.searchText LIKE :pattern OR k.searchText LIKE :pattern)
           ORDER BY t.dueDay IS NULL, t.dueDay LIMIT 400""",
    )
    suspend fun searchCal(pattern: String): List<CalTaskRow>

    @Query(
        """SELECT t.*, c.name AS customerName, k.title AS caseTitle, c.searchText AS customerSearch, k.searchText AS caseSearch
           FROM tasks t LEFT JOIN customers c ON c.id = t.customerId LEFT JOIN cases k ON k.id = t.caseId
           WHERE t.deletedAt IS NULL AND (c.id IS NULL OR c.deletedAt IS NULL) AND t.doneAt IS NULL
             AND t.dueDay >= :from AND t.dueDay <= :to
           ORDER BY t.dueDay, t.id LIMIT :limit""",
    )
    fun observeOpenRange(from: String, to: String, limit: Int): Flow<List<CalTaskRow>>

    @Query("SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL AND doneAt IS NULL AND dueDay < :before")
    fun observeOverdueCount(before: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL AND doneAt IS NULL AND dueDay = :day")
    fun observeOpenCountOn(day: String): Flow<Int>

    /** «بستن روز»: کارهای بازِ امروز و گذشته. */
    @Query(
        """SELECT t.*, c.name AS customerName, k.title AS caseTitle, c.searchText AS customerSearch, k.searchText AS caseSearch
           FROM tasks t LEFT JOIN customers c ON c.id = t.customerId LEFT JOIN cases k ON k.id = t.caseId
           WHERE t.deletedAt IS NULL AND (c.id IS NULL OR c.deletedAt IS NULL) AND t.doneAt IS NULL AND t.dueDay <= :day
           ORDER BY t.dueDay, t.id""",
    )
    suspend fun openUpTo(day: String): List<CalTaskRow>

    @Query(
        """SELECT c.id AS id, c.name AS name, COUNT(*) AS n FROM tasks t JOIN customers c ON c.id = t.customerId
           WHERE t.deletedAt IS NULL AND t.doneAt IS NULL AND c.deletedAt IS NULL
           GROUP BY c.id ORDER BY n DESC, c.name LIMIT :limit""",
    )
    suspend fun openCountsByCustomer(limit: Int): List<CustomerCount>

    @Query("SELECT COUNT(*) FROM tasks WHERE customerId = :customerId AND deletedAt IS NULL AND doneAt IS NULL")
    suspend fun countOpenOfCustomer(customerId: Long): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL AND doneAt >= :since")
    suspend fun countDoneSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL AND doneAt IS NULL AND dueDay = :day")
    suspend fun countOpenOn(day: String): Int

    /** «همه را به فردا ببر»: کارهای بازِ قدیمی (تا [cutoff]) یک‌جا روی یک روز. */
    @Query("UPDATE tasks SET dueDay = :to, anchor = NULL WHERE deletedAt IS NULL AND doneAt IS NULL AND dueDay <= :cutoff")
    suspend fun moveOldTo(cutoff: String, to: String): Int

    @Query("UPDATE tasks SET deletedAt = :ts WHERE deletedAt IS NULL AND doneAt IS NULL AND dueDay <= :cutoff")
    suspend fun deleteOld(cutoff: String, ts: Long): Int

    /** هر تغییر در کارها، متقاضی‌ها یا پرونده‌ها این را دوباره منتشر می‌کند (برای تازه‌سازی نتیجهٔ جست‌وجو). */
    @Query("SELECT (SELECT COUNT(*) FROM tasks) + (SELECT COUNT(*) FROM customers) + (SELECT COUNT(*) FROM cases)")
    fun observeVersion(): Flow<Int>

    @Query("SELECT COUNT(*) FROM tasks WHERE caseId = :caseId AND deletedAt IS NULL AND doneAt IS NULL")
    suspend fun countOpenInCase(caseId: Long): Int

    @Query("UPDATE tasks SET deletedAt = :ts WHERE id = :id")
    suspend fun setDeleted(id: Long, ts: Long?)

    @Query("UPDATE tasks SET customerId = :customerId, caseId = :caseId WHERE id = :id")
    suspend fun setTarget(id: Long, customerId: Long?, caseId: Long?)

    /** نوبتِ ساخته‌شده توسط «انجام شد» که کاربر هنوز دست نزده؛ با برداشتن «انجام شد» حذف می‌شود. */
    @Query("DELETE FROM tasks WHERE spawnedBy = :parentId AND doneAt IS NULL AND anchor IS NULL")
    suspend fun deleteSpawnedUntouched(parentId: Long)

    @Query("SELECT searchText FROM tasks WHERE deletedAt IS NULL AND searchText LIKE '%#%'")
    suspend fun tagTexts(): List<String>

    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND searchText LIKE :pattern ORDER BY dueDay IS NULL, dueDay LIMIT 300")
    suspend fun searchLike(pattern: String): List<TaskEntity>
}

@Dao
interface PrefDao {
    @Query("SELECT value FROM prefs WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(p: PrefEntity)
}

/** یک شاخهٔ درخت: شمارش‌های هر متقاضی. */
data class BranchRow(
    val id: Long,
    val name: String,
    val archivedAt: Long?,
    val done: Int,
    val doneWeek: Int,
    val open: Int,
    val fruits: Int,
    val lastActivity: Long,
)

/** شمارنده‌های «درخت» در خانه. */
@Dao
interface TreeDao {
    @Query(
        """SELECT c.id AS id, c.name AS name, c.archivedAt AS archivedAt,
           (SELECT COUNT(*) FROM tasks t WHERE t.customerId = c.id AND t.deletedAt IS NULL AND t.doneAt IS NOT NULL) AS done,
           (SELECT COUNT(*) FROM tasks t WHERE t.customerId = c.id AND t.deletedAt IS NULL AND t.doneAt >= :from AND t.doneAt < :to) AS doneWeek,
           (SELECT COUNT(*) FROM tasks t WHERE t.customerId = c.id AND t.deletedAt IS NULL AND t.doneAt IS NULL) AS open,
           (SELECT COUNT(*) FROM cases k WHERE k.customerId = c.id AND k.deletedAt IS NULL AND k.status = 'final') AS fruits,
           c.lastActivity AS lastActivity
           FROM customers c WHERE c.deletedAt IS NULL ORDER BY c.lastActivity DESC""",
    )
    fun observeBranches(from: Long, to: Long): Flow<List<BranchRow>>

    /** کارهای انجام‌شده با زمان انجام در [from, to) (میلی‌ثانیه)؛ حذف‌شده‌ها نیستند. */
    @Query("SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL AND doneAt >= :from AND doneAt < :to")
    fun observeDone(from: Long, to: Long): Flow<Int>

    /** کارهای بازِ دارای مهلت در بازهٔ روزها (متن ISO). */
    @Query("SELECT COUNT(*) FROM tasks WHERE deletedAt IS NULL AND doneAt IS NULL AND dueDay >= :from AND dueDay <= :to")
    fun observeOpenDue(from: String, to: String): Flow<Int>
}
