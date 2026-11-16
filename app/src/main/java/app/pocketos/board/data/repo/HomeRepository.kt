package app.pocketos.board.data.repo

import androidx.room.withTransaction
import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.db.CalTaskRow
import app.pocketos.board.data.db.OpenCaseRow
import app.pocketos.board.data.db.TaskEntity
import app.pocketos.board.domain.Attention
import app.pocketos.board.domain.CaseInfo
import app.pocketos.board.domain.CaseStatus
import app.pocketos.board.domain.HomeEntry
import app.pocketos.board.domain.HomeLogic
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** ردیف خانه همراه خودِ کار (برای منو و ویرایش)؛ ردیف پرونده task ندارد. */
data class HomeRow(val entry: HomeEntry, val task: TaskEntity?, val caseTitle: String? = null)

data class HomeData(
    val rows: List<HomeRow> = emptyList(),
    /** کارهای باز با روز پیش از «دیروز» (۲ روز و قدیمی‌تر). */
    val oldCount: Int = 0,
    val tomorrowCount: Int = 0,
)

data class EveningData(val tasks: List<CalTaskRow>, val made: Int, val done: Int)

@Singleton
class HomeRepository @Inject constructor(private val db: BoardDatabase, private val clock: Clock) {
    private val tasks = db.taskDao()
    private val cases = db.caseDao()
    private val items = db.itemDao()

    private fun parse(day: String?): LocalDate? = day?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun today(): LocalDate = LocalDate.now(clock)

    private fun taskRow(r: CalTaskRow, today: LocalDate): HomeRow? {
        val due = parse(r.task.dueDay) ?: return null
        val late = ChronoUnit.DAYS.between(due, today).toInt()
        return HomeRow(
            HomeEntry(
                key = "t${r.task.id}", taskId = r.task.id, caseId = r.task.caseId, title = r.task.title,
                customerId = r.task.customerId, customerName = r.customerName,
                sub = (r.caseTitle?.let { "$it · " } ?: "") + HomeLogic.taskNote(due, today),
                amber = late > 0, lateDays = late, repeat = r.task.repeat,
            ),
            r.task, r.caseTitle,
        )
    }

    private fun caseInfo(r: OpenCaseRow, today: LocalDate): CaseInfo {
        val last = Instant.ofEpochMilli(r.lastActivity).atZone(clock.zone).toLocalDate()
        return CaseInfo(
            r.id, r.title, r.customerId, r.customerName, CaseStatus.of(r.status), parse(r.dueDay),
            ChronoUnit.DAYS.between(last, today).toInt().coerceAtLeast(0),
        )
    }

    private fun attentionRows(rows: List<OpenCaseRow>, today: LocalDate): List<HomeRow> =
        Attention.list(rows.map { caseInfo(it, today) }, today).map { a ->
            HomeRow(
                HomeEntry(
                    key = "k${a.case.id}", taskId = null, caseId = a.case.id, title = a.case.title,
                    customerId = a.case.customerId, customerName = a.case.customerName,
                    sub = a.reasons.joinToString(" · ") { it.second }, amber = a.reasons.first().first == app.pocketos.board.domain.Reason.OVER,
                    lateDays = 0, keepOk = a.keepOk,
                ),
                null,
            )
        }

    /**
     * «امروز»: کارهای باز دیروز و امروز + پرونده‌های نیازمند توجه. مانده‌های قدیمی‌تر فقط شمرده می‌شوند
     * (و با [oldLimit] > 0 تا آن تعداد در ابتدای فهرست می‌آیند: صفحهٔ «امروز»).
     */
    fun observeToday(oldLimit: Int = 0): Flow<HomeData> {
        val today = today()
        val recentFrom = today.minusDays(HomeLogic.OLD_LATE - 1L)
        val old = if (oldLimit > 0) tasks.observeOverdue(recentFrom.toString(), null) else flowOf(emptyList())
        val flows = combine(
            tasks.observeOpenRange(recentFrom.toString(), today.toString(), 300),
            old,
            tasks.observeOverdueCount(recentFrom.toString()),
            tasks.observeOpenCountOn(today.plusDays(1).toString()),
            cases.observeOpenForAttention(),
        ) { recent, oldRows, oldN, tomorrowN, openCases ->
            val rows = oldRows.take(oldLimit).mapNotNull { taskRow(it, today) } +
                recent.mapNotNull { taskRow(it, today) } + attentionRows(openCases, today)
            HomeData(rows, oldN, tomorrowN)
        }
        return flows
    }

    /** «همه را به فردا ببر» یا «رها کن» برای مانده‌های قدیمی (۲ روز و قدیمی‌تر). تعداد را برمی‌گرداند. */
    suspend fun bulkOld(drop: Boolean): Int = db.withTransaction {
        val cutoff = today().minusDays(HomeLogic.OLD_LATE.toLong()).toString()
        if (drop) tasks.deleteOld(cutoff, clock.millis()) else tasks.moveOldTo(cutoff, today().plusDays(1).toString())
    }

    // ── بستن روز ──
    private fun dayStartMillis(): Long = today().atStartOfDay(clock.zone).toInstant().toEpochMilli()

    suspend fun loadEvening(): EveningData {
        val start = dayStartMillis()
        return EveningData(tasks.openUpTo(today().toString()), items.countCreatedSince(start), tasks.countDoneSince(start))
    }

    /** هر کار مانده به روزِ انتخابی می‌رود یا رها می‌شود. تعداد کارهای فردا را برمی‌گرداند. */
    suspend fun closeDay(choices: Map<Long, String>): Int = db.withTransaction {
        val today = today()
        for ((id, choice) in choices) {
            val t = tasks.get(id) ?: continue
            if (t.deletedAt != null || t.doneAt != null) continue
            val to = HomeLogic.shifted(choice, today)
            if (to == null) tasks.setDeleted(id, clock.millis()) else tasks.update(t.copy(dueDay = to.toString(), anchor = null))
        }
        tasks.countOpenOn(today.plusDays(1).toString())
    }
}
