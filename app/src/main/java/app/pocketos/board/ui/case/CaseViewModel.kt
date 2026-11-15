package app.pocketos.board.ui.case

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pocketos.board.data.db.CaseEntity
import app.pocketos.board.data.db.CustomerEntity
import app.pocketos.board.data.repo.CaseRepository
import app.pocketos.board.data.repo.CustomerRepository
import app.pocketos.board.domain.CaseStatus
import app.pocketos.board.ui.UiMessenger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class CaseUi(
    val loaded: Boolean = false,
    val case: CaseEntity? = null,
    val customer: CustomerEntity? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CaseViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: CaseRepository,
    private val customers: CustomerRepository,
    private val ui: UiMessenger,
    clock: Clock,
) : ViewModel() {
    private val id: Long = checkNotNull(savedState.get<Long>("id"))
    val today: LocalDate = LocalDate.now(clock)

    val state: StateFlow<CaseUi> = repo.observe(id)
        .flatMapLatest { c ->
            if (c == null) flowOf(CaseUi(loaded = true))
            else customers.observe(c.customerId).let { f ->
                kotlinx.coroutines.flow.combine(flowOf(c), f) { cc, cu -> CaseUi(loaded = true, case = cc, customer = cu) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CaseUi())

    val others = customers.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setStatus(s: CaseStatus) {
        viewModelScope.launch { repo.setStatus(id, s) }
    }

    fun update(title: String, due: LocalDate?) {
        viewModelScope.launch { repo.update(id, title, due) }
    }

    fun moveTo(customerId: Long) {
        viewModelScope.launch { repo.moveTo(id, customerId) }
    }

    fun delete(title: String) {
        viewModelScope.launch {
            repo.delete(id)
            ui.show("«$title» حذف شد", "بازگردانی") { repo.restore(id) }
        }
    }
}
