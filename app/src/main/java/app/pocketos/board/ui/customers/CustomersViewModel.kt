package app.pocketos.board.ui.customers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pocketos.board.data.repo.CustomerRepository
import app.pocketos.board.domain.CustomerList
import app.pocketos.board.domain.CustomerSections
import app.pocketos.board.domain.CustomerSummary
import app.pocketos.board.domain.Similar
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

data class CustomersState(
    val loaded: Boolean = false,
    val sections: CustomerSections = CustomerSections(),
    val total: Int = 0,
    val withTasks: Int = 0,
    val similar: Pair<CustomerSummary, CustomerSummary>? = null,
    val empties: List<CustomerSummary> = emptyList(),
    /** نامی که جست‌وجو کرده و دقیقاً همنامش هست (دکمهٔ «ساخت» لازم نیست). */
    val exactMatch: Boolean = false,
)

@HiltViewModel
class CustomersViewModel @Inject constructor(
    private val repo: CustomerRepository,
    private val clock: Clock,
) : ViewModel() {
    val query = MutableStateFlow("")
    val alpha = MutableStateFlow(false)

    val state: StateFlow<CustomersState> = combine(
        repo.observeSummaries(), repo.observeNotSimilar(), query, alpha,
    ) { all, notSimilar, q, alphaSort ->
        val now = clock.millis()
        val sections = CustomerList.build(all, q, alphaSort, now)
        val pair = Similar.firstPair(all.filter { !it.archived }.map { it.id to it.name }, notSimilar)
        val byId = all.associateBy { it.id }
        val qKey = app.pocketos.board.domain.Normalize.key(q)
        CustomersState(
            loaded = true,
            sections = sections,
            total = all.count { !it.archived },
            withTasks = all.count { !it.archived && it.openTasks > 0 },
            similar = pair?.let { (a, b) -> byId[a]?.let { x -> byId[b]?.let { y -> x to y } } },
            empties = CustomerList.empties(all, now),
            exactMatch = qKey.isNotEmpty() && all.any { app.pocketos.board.domain.Normalize.key(it.name) == qKey },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomersState())

    fun create(name: String, phone: String, onDone: (Long) -> Unit) {
        viewModelScope.launch { onDone(repo.create(name, phone)) }
    }

    fun merge(from: Long, to: Long) {
        viewModelScope.launch { repo.merge(from, to) }
    }

    fun notSimilar(a: Long, b: Long) {
        viewModelScope.launch { repo.markNotSimilar(a, b) }
    }

    fun archiveAll(ids: List<Long>) {
        viewModelScope.launch { repo.archiveAll(ids) }
    }
}
