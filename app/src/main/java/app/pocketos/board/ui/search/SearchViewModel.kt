package app.pocketos.board.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pocketos.board.data.repo.GlobalResults
import app.pocketos.board.data.repo.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    savedState: androidx.lifecycle.SavedStateHandle,
    private val repo: SearchRepository,
) : ViewModel() {
    val query = MutableStateFlow(savedState.get<String>("q").orEmpty())

    /** null = هنوز چیزی نوشته نشده. */
    val results: StateFlow<GlobalResults?> = query.debounce(200)
        .flatMapLatest { q -> flow { emit(if (q.isBlank()) null else repo.search(q)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
