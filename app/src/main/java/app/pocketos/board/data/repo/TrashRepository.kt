package app.pocketos.board.data.repo

import androidx.room.withTransaction
import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.db.CaseEntity
import app.pocketos.board.data.db.CustomerEntity
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TrashRepository @Inject constructor(private val db: BoardDatabase, private val clock: Clock) {
    private val customers = db.customerDao()
    private val cases = db.caseDao()
    private val bulk = db.bulkDao()

    fun deletedCustomers(): Flow<List<CustomerEntity>> = customers.observeDeleted()

    fun deletedCases(): Flow<List<CaseEntity>> = cases.observeDeleted()

    /** پاک‌سازی واقعیِ هر چیزی که بیش از ۳۰ روز در سطل بوده. (حذف فایل‌ها با مرحلهٔ ۳ می‌آید.) */
    suspend fun purge() = db.withTransaction {
        val cutoff = clock.millis() - RETENTION_DAYS * DAY
        bulk.purgeItems(cutoff)
        bulk.purgeTasks(cutoff)
        bulk.purgeCases(cutoff)
        bulk.purgeCustomers(cutoff)
    }

    companion object {
        const val RETENTION_DAYS = 30L
        private const val DAY = 86_400_000L

        /** روزهای باقی‌مانده تا پاک شدن. */
        fun daysLeft(deletedAt: Long, nowMillis: Long): Long =
            (RETENTION_DAYS - (nowMillis - deletedAt) / DAY).coerceIn(0, RETENTION_DAYS)
    }
}
