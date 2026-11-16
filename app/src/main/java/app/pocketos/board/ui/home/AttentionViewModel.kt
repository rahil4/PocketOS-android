package app.pocketos.board.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pocketos.board.data.repo.CaseRepository
import app.pocketos.board.data.repo.HomeRepository
import app.pocketos.board.domain.Digits
import app.pocketos.board.domain.HomeLogic
import app.pocketos.board.ui.UiMessenger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AttentionUi(
    val loaded: Boolean = false,
    val groups: List<HomeGroupUi> = emptyList(),
    val oldN: Int = 0,
    /** مانده‌های قدیمی که در فهرست نیامده (سقف ۱۰۰). */
    val hiddenOld: Int = 0,
)

@HiltViewModel
class AttentionViewModel @Inject constructor(
    private val repo: HomeRepository,
    private val cases: CaseRepository,
    private val ui: UiMessenger,
) : ViewModel() {
    val state: StateFlow<AttentionUi> = repo.observeToday(oldLimit = OLD_LIMIT).map { data ->
        val shownOld = data.rows.count { it.entry.isTask && it.entry.lateDays >= HomeLogic.OLD_LATE }
        AttentionUi(true, groupRows(data.rows), data.oldCount, (data.oldCount - shownOld).coerceAtLeast(0))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AttentionUi())

    fun bulk(drop: Boolean) {
        viewModelScope.launch {
            val n = repo.bulkOld(drop)
            ui.show(Digits.fa("$n کار ") + if (drop) "رها شد؛ در سطل بازیافت می‌ماند" else "به فردا رفت")
        }
    }

    fun caseDone(title: String, caseId: Long) {
        viewModelScope.launch {
            val prev = cases.markFinal(caseId) ?: return@launch
            ui.show("«$title» نهایی شد", "بازگردانی") { cases.setStatus(caseId, prev) }
        }
    }

    fun caseKeep(caseId: Long) {
        viewModelScope.launch { cases.markSeen(caseId) }
    }

    private companion object {
        const val OLD_LIMIT = 100
    }
}
