package app.pocketos.board.data.repo

import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.db.CustomerEntity
import app.pocketos.board.data.db.NoteRow
import app.pocketos.board.data.db.RecentCase
import app.pocketos.board.domain.Normalize
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class GlobalResults(
    val customers: List<CustomerEntity> = emptyList(),
    val cases: List<RecentCase> = emptyList(),
    val tasks: SearchResult = SearchResult(),
    val notes: List<NoteRow> = emptyList(),
) {
    val count: Int get() = customers.size + cases.size + tasks.upcoming.size + tasks.past.size + notes.size
}

/** جست‌وجوی سراسری: متقاضی، پرونده، کار و متن یادداشت‌ها. */
@Singleton
class SearchRepository @Inject constructor(
    private val db: BoardDatabase,
    private val calendar: CalendarRepository,
    private val clock: Clock,
) {
    suspend fun search(query: String): GlobalResults {
        val words = Normalize.words(query).map { it.trimStart('#') }.filter { it.isNotEmpty() }
        if (words.isEmpty()) return GlobalResults()
        val pattern = "%${words.first()}%"
        val customers = db.customerDao().allActive()
            .filter { c -> words.all { it in c.searchText } }.sortedByDescending { it.lastActivity }.take(20)
        val cases = db.caseDao().searchCases(pattern).filter { c ->
            val hay = Normalize.key("${c.title} ${c.customerName}")
            words.all { it in hay }
        }.take(20)
        val notes = db.itemDao().searchNotes(pattern).filter { n ->
            words.all { it in n.item.searchText }
        }.take(30)
        val tasks = calendar.search(query, LocalDate.now(clock))
        return GlobalResults(customers, cases, tasks, notes)
    }
}
