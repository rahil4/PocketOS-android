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

    fun observeByCustomer(customerId: Long): Flow<List<CaseEntity>> = cases.observeByCustomer(customerId)

    suspend fun create(customerId: Long, title: String, due: LocalDate? = null): Long = db.withTransaction {
        val clean = Normalize.norm(title)
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
        val clean = Normalize.norm(title).ifEmpty { c.title }
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
}
