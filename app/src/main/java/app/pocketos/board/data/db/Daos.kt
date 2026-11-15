package app.pocketos.board.data.db

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** یک ردیف فهرست مشتری‌ها با شمارش‌ها. */
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
