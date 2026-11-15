package app.pocketos.board.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pocketos.board.data.db.CaseEntity
import app.pocketos.board.data.db.CustomerEntity
import app.pocketos.board.data.repo.CaseRepository
import app.pocketos.board.data.repo.CustomerRepository
import app.pocketos.board.data.repo.TrashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import javax.inject.Inject

data class TrashState(
    val loaded: Boolean = false,
    val customers: List<CustomerEntity> = emptyList(),
    val cases: List<CaseEntity> = emptyList(),
)

@HiltViewModel
class TrashViewModel @Inject constructor(
    trash: TrashRepository,
    private val customers: CustomerRepository,
    private val cases: CaseRepository,
    val clock: Clock,
) : ViewModel() {
    val state: StateFlow<TrashState> = combine(trash.deletedCustomers(), trash.deletedCases()) { c, k ->
        TrashState(true, c, k)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrashState())

    fun restoreCustomer(id: Long) {
        viewModelScope.launch { customers.restore(id) }
    }

    fun restoreCase(id: Long) {
        viewModelScope.launch { cases.restore(id) }
    }
}
