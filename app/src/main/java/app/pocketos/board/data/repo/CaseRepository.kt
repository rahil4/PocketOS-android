package app.pocketos.board.data.repo

import androidx.room.withTransaction
import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.db.CaseEntity
import app.pocketos.board.domain.CaseStatus
import app.pocketos.board.domain.Normalize
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaseRepository @Inject constructor(private val db: BoardDatabase, private val clock: Clock) {
    private val cases = db.caseDao()
    private val customers = db.customerDao()
    private val bulk = db.bulkDao()

    fun observe(id: Long): Flow<CaseEntity?> = cases.observe(id)

    suspend fun get(id: Long): CaseEntity? = cases.get(id)

    fun observeRecent(limit: Int) = cases.observeRecent(limit)

    fun observeByCustomer(customerId: Long): Flow<List<CaseEntity>> = cases.observeByCustomer(customerId)

    suspend fun create(customerId: Long, title: String, due: LocalDate? = null): Long = db.withTransaction {
        val clean = Normalize.clean(title)
        require(clean.isNotEmpty()) { "عنوان خالی است" }
        val now = clock.millis()
        val id = cases.insert(
            CaseEntity(
                customerId = customerId, title = clean, dueDay = due?.toString(),
                searchText = Normalize.key(clean), createdAt = now, lastActivity = now,
            ),
        )
        customers.touch(customerId, now)
        id
    }

    suspend fun update(id: Long, title: String, due: LocalDate?) = db.withTransaction {
        val c = cases.get(id) ?: return@withTransaction
        val clean = Normalize.clean(title).ifEmpty { c.title }
        val now = clock.millis()
        cases.update(c.copy(title = clean, dueDay = due?.toString(), searchText = Normalize.key(clean), lastActivity = now))
        customers.touch(c.customerId, now)
    }

    suspend fun setStatus(id: Long, status: CaseStatus) = db.withTransaction {
        val c = cases.get(id) ?: return@withTransaction
        val now = clock.millis()
        cases.update(c.copy(status = status.code, lastActivity = now))
        customers.touch(c.customerId, now)
    }

    /** پرونده با یادداشت و کارهایش به مشتری دیگر می‌رود. */
    suspend fun moveTo(id: Long, customerId: Long) = db.withTransaction {
        val c = cases.get(id) ?: return@withTransaction
        if (c.customerId == customerId) return@withTransaction
        val now = clock.millis()
        cases.update(c.copy(customerId = customerId, lastActivity = now))
        bulk.moveItemsOfCase(id, customerId)
        bulk.moveTasksOfCase(id, customerId)
        customers.touch(customerId, now)
    }

    suspend fun delete(id: Long) = db.withTransaction {
        val ts = clock.millis()
        cases.setDeleted(id, ts)
        bulk.deleteItemsOfCase(id, ts)
        bulk.deleteTasksOfCase(id, ts)
    }

    suspend fun restore(id: Long) = db.withTransaction {
        val c = cases.get(id) ?: return@withTransaction
        val ts = c.deletedAt ?: return@withTransaction
        cases.setDeleted(id, null)
        bulk.restoreItemsOfCase(id, ts)
        bulk.restoreTasksOfCase(id, ts)
        // اگر صاحبش در سطل است، خودِ مشتری هم برمی‌گردد (نه محتوای دیگرش).
        val owner = customers.get(c.customerId)
        if (owner?.deletedAt != null) customers.setDeleted(owner.id, null)
        customers.touch(c.customerId, clock.millis())
    }

    /** «✓ تمام شد» در «نیاز به توجه»: پرونده «نهایی» می‌شود. وضعیت قبلی را برای «بازگردانی» برمی‌گرداند. */
    suspend fun markFinal(id: Long): CaseStatus? = db.withTransaction {
        val c = cases.get(id) ?: return@withTransaction null
        val prev = CaseStatus.of(c.status)
        cases.update(c.copy(status = CaseStatus.FINAL.code, lastActivity = clock.millis()))
        prev
    }

    /** «دیدم، بماند»: «جدید» ← «در حال انجام»؛ در هر حال فعالیت به الان می‌رسد. */
    suspend fun markSeen(id: Long) = db.withTransaction {
        val c = cases.get(id) ?: return@withTransaction
        val status = if (CaseStatus.of(c.status) == CaseStatus.NEW) CaseStatus.DOING.code else c.status
        cases.update(c.copy(status = status, lastActivity = clock.millis()))
    }

    /** باز شدن واقعیِ پروندهٔ «جدید» (بعد از چند ثانیه) آن را «در حال انجام» می‌کند. */
    suspend fun markOpened(id: Long) = db.withTransaction {
        val c = cases.get(id) ?: return@withTransaction
        if (c.deletedAt == null && CaseStatus.of(c.status) == CaseStatus.NEW) {
            cases.update(c.copy(status = CaseStatus.DOING.code, lastActivity = clock.millis()))
        }
    }
}
