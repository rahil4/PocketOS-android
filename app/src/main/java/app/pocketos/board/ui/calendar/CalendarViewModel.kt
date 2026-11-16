package app.pocketos.board.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pocketos.board.data.repo.CalEntry
import app.pocketos.board.data.repo.CalendarRepository
import app.pocketos.board.data.repo.PrefRepository
import app.pocketos.board.data.repo.SearchResult
import app.pocketos.board.domain.CalRange
import app.pocketos.board.domain.CalendarLogic
import app.pocketos.board.domain.Dot
import app.pocketos.board.domain.CalView
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class CalUi(
    val loaded: Boolean = false,
    val view: CalView = CalView.MONTH,
    val anchor: LocalDate = LocalDate.now(),
    val from: LocalDate = LocalDate.now(),
    val to: LocalDate = LocalDate.now(),
    val entries: List<CalEntry> = emptyList(),
    val overdue: List<CalEntry> = emptyList(),
    val truncated: Boolean = false,
    val tag: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repo: CalendarRepository,
    private val prefs: PrefRepository,
    private val clock: Clock,
) : ViewModel() {
    val today: LocalDate get() = LocalDate.now(clock)

    private val view = MutableStateFlow(CalView.MONTH)
    private val anchor = MutableStateFlow(LocalDate.now(clock))
    private val tag = MutableStateFlow<String?>(null)

    val query = MutableStateFlow("")
    val searching = MutableStateFlow(false)

    init {
        viewModelScope.launch { view.value = CalView.of(prefs.getString(PrefRepository.CAL_VIEW)) }
    }

    val ui: StateFlow<CalUi> = combine(view, anchor, tag) { v, a, t -> Triple(v, a, t) }
        .flatMapLatest { (v, a, t) ->
            val (from, to) = CalRange.of(v, a)
            val now = today
            val overdue = if (v != CalView.MONTH && now in from..to) repo.observeOverdue(now, t) else flow { emit(emptyList()) }
            combine(repo.observeRange(from, to, t), overdue) { range, od ->
                CalUi(true, v, a, from, to, range.entries, od, range.truncated, t)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CalUi())

    /** نقطهٔ روزهای هفته برای نوار هفتهٔ نمای «روز». */
    val strip: StateFlow<Map<LocalDate, Dot>> = combine(view, anchor, tag) { v, a, t -> Triple(v, a, t) }
        .flatMapLatest { (v, a, t) ->
            if (v != CalView.DAY) flowOf(emptyMap())
            else {
                val (from, to) = CalRange.of(CalView.WEEK, a)
                repo.observeRange(from, to, t).map { CalendarLogic.dots(it.entries.map { e -> e.item }, today) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    /** نتیجهٔ جست‌وجو؛ با هر تغییرِ کارها دوباره حساب می‌شود. null یعنی جست‌وجو فعال نیست. */
    val search: StateFlow<SearchResult?> = combine(query.debounce(200), repo.observeVersion()) { q, _ -> q }
        .flatMapLatest { q -> flow { emit(if (q.isBlank()) null else repo.search(q, today)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setView(v: CalView) {
        view.value = v
        viewModelScope.launch { prefs.setString(PrefRepository.CAL_VIEW, v.code) }
    }

    fun goToday() { anchor.value = today }

    fun go(dir: Int) { anchor.value = CalRange.shift(view.value, anchor.value, dir) }

    fun select(day: LocalDate) { anchor.value = day }

    fun setTag(t: String?) { tag.value = t }
}
