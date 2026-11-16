package app.pocketos.board.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pocketos.board.data.repo.ReminderService
import app.pocketos.board.data.repo.SettingsRepository
import app.pocketos.board.domain.AppSettings
import app.pocketos.board.ui.UiMessenger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val reminders: ReminderService,
    private val ui: UiMessenger,
) : ViewModel() {
    val state: StateFlow<AppSettings> = repo.state

    fun update(change: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repo.update(change) }
    }

    fun sendTest() {
        viewModelScope.launch {
            ui.show(if (reminders.sendTest()) "اعلان آزمایشی فرستاده شد" else "اعلان‌ها خاموش است؛ اول مجوز اعلان را بدهید")
        }
    }
}
