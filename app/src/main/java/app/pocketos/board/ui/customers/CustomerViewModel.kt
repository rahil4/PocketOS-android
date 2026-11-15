package app.pocketos.board.ui.customers

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pocketos.board.data.db.CaseEntity
import app.pocketos.board.data.db.CustomerEntity
import app.pocketos.board.data.repo.CaseRepository
import app.pocketos.board.data.repo.CustomerRepository
import app.pocketos.board.ui.UiMessenger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class CustomerUi(
    val loaded: Boolean = false,
    val customer: CustomerEntity? = null,
    val cases: List<CaseEntity> = emptyList(),
)

@HiltViewModel
class CustomerViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: CustomerRepository,
    private val caseRepo: CaseRepository,
    private val ui: UiMessenger,
    clock: Clock,
) : ViewModel() {
    private val id: Long = checkNotNull(savedState.get<Long>("id"))
    val today: LocalDate = LocalDate.now(clock)

    val state: StateFlow<CustomerUi> = combine(repo.observe(id), caseRepo.observeByCustomer(id)) { c, cases ->
        CustomerUi(loaded = true, customer = c, cases = cases)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CustomerUi())

    /** سایر مشتری‌ها برای ادغام. */
    val others = repo.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun update(name: String, phone: String) {
        viewModelScope.launch { repo.update(id, name, phone) }
    }

    fun setStar(on: Boolean) {
        viewModelScope.launch { repo.setStar(id, on) }
    }

    fun setArchived(on: Boolean) {
        viewModelScope.launch { repo.setArchived(id, on) }
    }

    fun createCase(title: String, due: LocalDate?, onDone: (Long) -> Unit) {
        viewModelScope.launch { onDone(caseRepo.create(id, title, due)) }
    }

    fun merge(into: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.merge(id, into)
            onDone()
        }
    }

    /** حذف فوری با «بازگردانی». */
    fun delete(name: String) {
        viewModelScope.launch {
            repo.delete(id)
            ui.show("«$name» حذف شد", "بازگردانی") { repo.restore(id) }
        }
    }
}
