package app.pocketos.board.data.repo

import javax.inject.Inject
import javax.inject.Singleton

/** «پروندهٔ فعال» برای ثبت از خانه و دریافت اشتراک؛ اگر حذف شده باشد، صندوق ورودی. */
@Singleton
class ActiveTargetRepository @Inject constructor(
    private val prefs: PrefRepository,
    private val cases: CaseRepository,
    private val customers: CustomerRepository,
) {
    suspend fun current(): Target {
        val caseId = prefs.getString(PrefRepository.ACTIVE_CASE)?.toLongOrNull()
        val customerId = prefs.getString(PrefRepository.ACTIVE_CUSTOMER)?.toLongOrNull()
        val case = caseId?.let { cases.get(it) }?.takeIf { it.deletedAt == null }
        val customer = customerId?.let { customers.get(it) }?.takeIf { it.deletedAt == null }
        return when {
            case != null -> Target(null, case.id)
            customer != null -> Target(customer.id, null)
            else -> Target.Inbox
        }
    }

    suspend fun set(t: Target) {
        prefs.setString(PrefRepository.ACTIVE_CASE, t.caseId?.toString() ?: "")
        prefs.setString(PrefRepository.ACTIVE_CUSTOMER, t.customerId?.toString() ?: "")
    }
}
