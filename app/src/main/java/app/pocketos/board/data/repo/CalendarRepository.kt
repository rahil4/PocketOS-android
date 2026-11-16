package app.pocketos.board.data.repo

import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.db.CalTaskRow
import app.pocketos.board.data.db.CaseDueRow
import app.pocketos.board.data.db.TaskEntity
import app.pocketos.board.domain.CalItem
import app.pocketos.board.domain.Normalize
import app.pocketos.board.domain.Tags
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** ردیف تقویم همراه خودِ کار (برای منو و ویرایش)؛ مهلت پرونده task ندارد. */
data class CalEntry(val item: CalItem, val task: TaskEntity?)

data class RangeData(val entries: List<CalEntry> = emptyList(), val truncated: Boolean = false)

data class SearchResult(val upcoming: List<CalEntry> = emptyList(), val past: List<CalEntry> = emptyList())

@Singleton
class CalendarRepository @Inject constructor(db: BoardDatabase) {
    private val tasks = db.taskDao()
    private val cases = db.caseDao()

    private fun likeFor(tag: String?): String? = tag?.let { "%#${Normalize.key(it).trimStart('#')}%" }

    private fun CalTaskRow.toEntry(): CalEntry? {
        val day = task.dueDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
        return CalEntry(
            CalItem(
                key = "t${task.id}", day = day, title = task.title, customerId = task.customerId, customerName = customerName,
                caseId = task.caseId, caseTitle = caseTitle, taskId = task.id, done = task.doneAt != null, repeat = task.repeat,
            ),
            task,
        )
    }

    private fun CaseDueRow.toEntry(): CalEntry? {
        val day = runCatching { LocalDate.parse(dueDay) }.getOrNull() ?: return null
        return CalEntry(
            CalItem(
                key = "k$id", day = day, title = "مهلت پرونده: $title", customerId = customerId, customerName = customerName,
                caseId = id, caseTitle = title, taskId = null, done = false, repeat = null,
            ),
            null,
        )
    }

    /** کارها و مهلت‌های پروندهٔ یک بازه (با فیلتر برچسب اختیاری: آن‌وقت مهلت پرونده‌ها نمی‌آید). */
    fun observeRange(from: LocalDate, to: LocalDate, tag: String?): Flow<RangeData> {
        val caseFlow = if (tag == null) cases.observeDueRange(from.toString(), to.toString()) else flowOf(emptyList())
        return combine(tasks.observeCalRange(from.toString(), to.toString(), likeFor(tag), LIMIT + 1), caseFlow) { t, k ->
            val exact = if (tag == null) t else t.filter { Tags.hasTag(it.task.title, tag) }
            val truncated = t.size > LIMIT
            RangeData((exact.take(LIMIT).mapNotNull { it.toEntry() } + k.mapNotNull { it.toEntry() }), truncated)
        }
    }

    /** «مانده از قبل»: کارهای بازِ پیش از [today] و مهلت‌های گذشتهٔ پرونده‌ها. */
    fun observeOverdue(today: LocalDate, tag: String?): Flow<List<CalEntry>> {
        val caseFlow = if (tag == null) cases.observeDueBefore(today.toString()) else flowOf(emptyList())
        return combine(tasks.observeOverdue(today.toString(), likeFor(tag)), caseFlow) { t, k ->
            val exact = if (tag == null) t else t.filter { Tags.hasTag(it.task.title, tag) }
            (exact.mapNotNull { it.toEntry() } + k.mapNotNull { it.toEntry() }).sortedBy { it.item.day }
        }
    }

    fun observeVersion(): Flow<Int> = tasks.observeVersion()

    /**
     * جست‌وجو در عنوان، برچسب دقیق (#اداره)، نام مشتری/پرونده و مهلت پرونده‌ها؛ حداکثر ۱۰۰ نتیجه.
     * «پیش رو»: کارهای باز و هر چه از امروز به بعد است (نزدیک‌ترین اول)؛ «گذشته»: انجام‌شده‌های پیش از امروز (تازه‌ترین اول).
     */
    suspend fun search(query: String, today: LocalDate): SearchResult {
        val tag = Tags.tagsIn(query).firstOrNull()
        val words = Normalize.words(query).map { it.trimStart('#') }.filter { it.isNotEmpty() }
        if (tag == null && words.isEmpty()) return SearchResult()
        val first = tag?.let { "#$it" } ?: words.first()
        val pattern = "%$first%"

        val rows = tasks.searchCal(pattern).filter { r ->
            if (tag != null) Tags.hasTag(r.task.title, tag)
            else {
                val hay = "${r.task.searchText} ${r.customerSearch.orEmpty()} ${r.caseSearch.orEmpty()}"
                words.all { it in hay }
            }
        }
        val dueRows = if (tag != null) emptyList() else cases.searchDue(pattern).filter { r ->
            val hay = Normalize.key("${r.title} ${r.customerName}")
            words.all { it in hay }
        }
        val all = (rows.mapNotNull { it.toEntry() } + dueRows.mapNotNull { it.toEntry() })
        val upcoming = all.filter { !it.item.done || it.item.day >= today }.sortedBy { it.item.day }
        val past = all.filter { it.item.done && it.item.day < today }.sortedByDescending { it.item.day }
        return SearchResult(upcoming.take(MAX_SEARCH), past.take((MAX_SEARCH - upcoming.size).coerceAtLeast(0)))
    }

    companion object {
        const val LIMIT = 400
        const val MAX_SEARCH = 100
    }
}
