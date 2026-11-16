package app.pocketos.board.ui.evening

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pocketos.board.data.db.CalTaskRow
import app.pocketos.board.data.repo.HomeRepository
import app.pocketos.board.data.repo.PrefRepository
import app.pocketos.board.domain.Digits
import app.pocketos.board.ui.UiMessenger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class EveningUi(
    val loaded: Boolean = false,
    val tasks: List<CalTaskRow> = emptyList(),
    val made: Int = 0,
    val done: Int = 0,
    /** انتخاب هر کار: tomorrow | after | week | drop (پیش‌فرض فردا). */
    val choices: Map<Long, String> = emptyMap(),
)

@HiltViewModel
class EveningViewModel @Inject constructor(
    private val repo: HomeRepository,
    private val prefs: PrefRepository,
    private val ui: UiMessenger,
    private val clock: Clock,
) : ViewModel() {
    private var closing = false
    private val _state = MutableStateFlow(EveningUi())
    val state: StateFlow<EveningUi> = _state

    init {
        viewModelScope.launch {
            val d = repo.loadEvening()
            _state.value = EveningUi(true, d.tasks, d.made, d.done, d.tasks.associate { it.task.id to "tomorrow" })
        }
    }

    fun choose(taskId: Long, choice: String) {
        _state.value = _state.value.let { it.copy(choices = it.choices + (taskId to choice)) }
    }

    /** «روز بسته شد»: هر کار به روز انتخابی می‌رود یا رها می‌شود؛ امروز بسته علامت می‌خورد. */
    fun close(onDone: () -> Unit) {
        if (closing) return
        closing = true
        viewModelScope.launch {
            val tomorrowN = repo.closeDay(_state.value.choices)
            prefs.setString(PrefRepository.CLOSED_ON, LocalDate.now(clock).toString())
            ui.show(if (tomorrowN > 0) Digits.fa("روز بسته شد. فردا $tomorrowN کار داری.") else "روز بسته شد. فردا خلوت است.")
            onDone()
        }
    }
}
