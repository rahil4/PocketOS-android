package app.pocketos.board.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pocketos.board.data.repo.CaseRepository
import app.pocketos.board.data.repo.HomeData
import app.pocketos.board.data.repo.HomeRepository
import app.pocketos.board.data.repo.HomeRow
import app.pocketos.board.data.repo.ItemRepository
import app.pocketos.board.data.repo.PrefRepository
import app.pocketos.board.domain.HomeLogic
import app.pocketos.board.domain.Jalali
import app.pocketos.board.ui.UiMessenger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject

/** گروهِ متقاضی با ردیف‌های همراهِ خودشان. */
data class HomeGroupUi(val customerId: Long?, val name: String?, val rows: List<HomeRow>)

fun groupRows(rows: List<HomeRow>): List<HomeGroupUi> {
    val byKey = rows.associateBy { it.entry.key }
    return HomeLogic.group(rows.map { it.entry }).map { g -> HomeGroupUi(g.customerId, g.name, g.items.mapNotNull { byKey[it.key] }) }
}

data class HomeUi(
    val loaded: Boolean = false,
    val greeting: String = "",
    val todayLong: String = "",
    val groups: List<HomeGroupUi> = emptyList(),
    val extraN: Int = 0,
    val tomorrowN: Int = 0,
    val inboxN: Int = 0,
    val evening: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: HomeRepository,
    items: ItemRepository,
    private val cases: CaseRepository,
    private val prefs: PrefRepository,
    private val ui: UiMessenger,
    private val launch: app.pocketos.board.ui.LaunchRequests,
    private val clock: Clock,
) : ViewModel() {
    val quickNote: StateFlow<Boolean> = launch.quickNote

    fun consumeQuickNote() { launch.quickNote.value = false }

    /** با بازگشت به برنامه (شاید روز یا ساعت عوض شده) دوباره حساب می‌شود. */
    private val tick = MutableStateFlow(0)
    val tomorrow: LocalDate get() = LocalDate.now(clock).plusDays(1)

    val state: StateFlow<HomeUi> = tick.flatMapLatest { repo.observeToday() }
        .combine(items.observeInboxCount()) { data, inbox -> data to inbox }
        .mapLatest { (data, inbox) -> build(data, inbox) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUi())

    private suspend fun build(data: HomeData, inbox: Int): HomeUi {
        val now = ZonedDateTime.now(clock)
        val today = now.toLocalDate()
        val (shown, hidden) = HomeLogic.limit(HomeLogic.group(data.rows.map { it.entry }))
        val byKey = data.rows.associateBy { it.entry.key }
        val groups = shown.map { g -> HomeGroupUi(g.customerId, g.name, g.items.mapNotNull { byKey[it.key] }) }
        val eveningHour = prefs.getString(PrefRepository.EVENING_HOUR)?.toIntOrNull() ?: DEFAULT_EVENING_HOUR
        return HomeUi(
            loaded = true,
            greeting = HomeLogic.greeting(now.hour),
            todayLong = Jalali.formatLong(today),
            groups = groups,
            extraN = hidden + data.oldCount,
            tomorrowN = data.tomorrowCount,
            inboxN = inbox,
            evening = HomeLogic.eveningVisible(now.hour, eveningHour, prefs.getString(PrefRepository.CLOSED_ON), today),
        )
    }

    fun refresh() { tick.value++ }

    // ── «نیاز به توجه» ──
    fun caseDone(entryTitle: String, caseId: Long) {
        viewModelScope.launch {
            val prev = cases.markFinal(caseId) ?: return@launch
            ui.show("«$entryTitle» نهایی شد", "بازگردانی") { cases.setStatus(caseId, prev) }
        }
    }

    fun caseKeep(caseId: Long) {
        viewModelScope.launch { cases.markSeen(caseId) }
    }

    companion object {
        const val DEFAULT_EVENING_HOUR = 19
    }
}
