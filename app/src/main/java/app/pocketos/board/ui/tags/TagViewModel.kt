package app.pocketos.board.ui.tags

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pocketos.board.data.repo.TagRepository
import app.pocketos.board.data.repo.TagResults
import app.pocketos.board.data.repo.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class TagState(val loaded: Boolean = false, val results: TagResults? = null)

@HiltViewModel
class TagViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: TagRepository,
    private val tasks: TaskRepository,
    clock: Clock,
) : ViewModel() {
    val tag: String = checkNotNull(savedState.get<String>("name"))
    val today: LocalDate = LocalDate.now(clock)
    private val _state = MutableStateFlow(TagState())
    val state: StateFlow<TagState> = _state

    init { refresh() }

    fun refresh() {
        viewModelScope.launch { _state.value = TagState(true, repo.results(tag)) }
    }

    fun done(id: Long) {
        viewModelScope.launch {
            tasks.done(id)
            refresh()
        }
    }
}
