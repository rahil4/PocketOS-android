package app.pocketos.board.data.repo

import androidx.room.withTransaction
import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.db.CustomerEntity
import app.pocketos.board.data.db.CustomerRow
import app.pocketos.board.data.db.SimilarNoEntity
import app.pocketos.board.domain.CaseChip
import app.pocketos.board.domain.CustomerSummary
import app.pocketos.board.domain.Normalize
import app.pocketos.board.domain.Phone
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

internal fun customerSearchText(name: String, phone: String) = Normalize.key("$name $phone")

@Singleton
class CustomerRepository @Inject constructor(private val db: BoardDatabase, private val clock: Clock) {
    private val customers = db.customerDao()
    private val cases = db.caseDao()
    private val bulk = db.bulkDao()

    /** فهرست مشتری‌ها با شمارش‌ها و حداکثر دو پروندهٔ بازِ هر مشتری. */
    fun observeSummaries(): Flow<List<CustomerSummary>> =
        combine(customers.observeRows(), cases.observeOpen()) { rows, open ->
            val byCustomer = open.groupBy { it.customerId }
            rows.map { it.toSummary(byCustomer[it.customer.id].orEmpty().take(2).map { c -> CaseChip(c.id, c.title) }) }
        }

    fun observe(id: Long): Flow<CustomerEntity?> = customers.observe(id)

    /** مشتری‌های فعال برای انتخاب‌گرها (ادغام، انتقال). */
    fun observeActive(): Flow<List<CustomerEntity>> = customers.observeActive()

    fun observeNotSimilar(): Flow<Set<Pair<Long, Long>>> =
        customers.observeNotSimilar().map { l -> l.map { it.a to it.b }.toSet() }

    /** مشتری تازه؛ اگر همنام باشد (حتی بایگانی‌شده) همان برمی‌گردد، بایگانی برداشته می‌شود و تلفنِ خالی پر می‌شود. */
    suspend fun create(name: String, phone: String = ""): Long = db.withTransaction {
        val clean = Normalize.norm(name)
        require(clean.isNotEmpty()) { "نام خالی است" }
        val ph = Phone.normalize(phone)
        val now = clock.millis()
        val same = customers.allActive().firstOrNull { Normalize.key(it.name) == Normalize.key(clean) }
        if (same != null) {
            val newPhone = if (same.phone.isBlank()) ph else same.phone
            customers.update(
                same.copy(phone = newPhone, archivedAt = null, lastActivity = now, searchText = customerSearchText(same.name, newPhone)),
            )
            same.id
        } else {
            customers.insert(
                CustomerEntity(
                    name = clean, phone = ph, searchText = customerSearchText(clean, ph),
                    createdAt = now, lastActivity = now,
                ),
            )
        }
    }

    suspend fun update(id: Long, name: String, phone: String) = db.withTransaction {
        val c = customers.get(id) ?: return@withTransaction
        val clean = Normalize.norm(name).ifEmpty { c.name }
        val ph = Phone.normalize(phone)
        customers.update(c.copy(name = clean, phone = ph, searchText = customerSearchText(clean, ph), lastActivity = clock.millis(), archivedAt = null))
    }

    suspend fun setStar(id: Long, on: Boolean) = customers.setPinned(id, if (on) clock.millis() else null)

    suspend fun setArchived(id: Long, on: Boolean) = customers.setArchived(id, if (on) clock.millis() else null)

    suspend fun archiveAll(ids: List<Long>) = db.withTransaction { ids.forEach { setArchived(it, true) } }

    /** حذف نرم همراه محتوا؛ زمان حذف را برای «بازگردانی» برمی‌گرداند. */
    suspend fun delete(id: Long): Long = db.withTransaction {
        val ts = clock.millis()
        customers.setDeleted(id, ts)
        bulk.deleteCasesOfCustomer(id, ts)
        bulk.deleteItemsOfCustomer(id, ts)
        bulk.deleteTasksOfCustomer(id, ts)
        ts
    }

    /** فقط محتوایی برمی‌گردد که همراه همین مشتری حذف شده بود. */
    suspend fun restore(id: Long) = db.withTransaction {
        val ts = customers.get(id)?.deletedAt ?: return@withTransaction
        customers.setDeleted(id, null)
        bulk.restoreCasesOfCustomer(id, ts)
        bulk.restoreItemsOfCustomer(id, ts)
        bulk.restoreTasksOfCustomer(id, ts)
        customers.touch(id, clock.millis())
    }

    /** همهٔ پرونده/مورد/کارهای مبدأ به مقصد می‌رود؛ مبدأ نرم حذف می‌شود (جابه‌جایی برگشت ندارد). */
    suspend fun merge(fromId: Long, toId: Long) = db.withTransaction {
        if (fromId == toId) return@withTransaction
        val from = customers.get(fromId) ?: return@withTransaction
        val to = customers.get(toId) ?: return@withTransaction
        bulk.moveCases(fromId, toId)
        bulk.moveItems(fromId, toId)
        bulk.moveTasks(fromId, toId)
        val phone = to.phone.ifBlank { from.phone }
        customers.update(
            to.copy(phone = phone, searchText = customerSearchText(to.name, phone), lastActivity = clock.millis(), archivedAt = null),
        )
        customers.setDeleted(fromId, clock.millis())
    }

    suspend fun markNotSimilar(a: Long, b: Long) =
        customers.insertNotSimilar(SimilarNoEntity(minOf(a, b), maxOf(a, b)))

    private fun CustomerRow.toSummary(chips: List<CaseChip>) = CustomerSummary(
        id = customer.id,
        name = customer.name,
        phone = customer.phone,
        pinned = customer.pinnedAt != null,
        archived = customer.archivedAt != null,
        openCases = openCases,
        openTasks = openTasks,
        nextDue = nextDue?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
        lastActivity = customer.lastActivity,
        isEmpty = caseTotal == 0 && taskTotal == 0 && itemCount == 0,
        createdAt = customer.createdAt,
        searchText = customer.searchText,
        cases = chips,
    )
}
