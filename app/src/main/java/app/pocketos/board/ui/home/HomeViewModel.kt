package app.pocketos.board.ui.home

import androidx.lifecycle.ViewModel
import app.pocketos.board.domain.Jalali
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(clock: Clock) : ViewModel() {
    val today: LocalDate = LocalDate.now(clock)
    val todayText: String = Jalali.formatLong(today)
}
