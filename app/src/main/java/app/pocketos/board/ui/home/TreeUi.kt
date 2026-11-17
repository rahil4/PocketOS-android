package app.pocketos.board.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.pocketos.board.data.repo.Count
import app.pocketos.board.data.repo.TreeRepository
import app.pocketos.board.data.repo.TreeSummary
import app.pocketos.board.domain.Digits
import app.pocketos.board.domain.Growth
import app.pocketos.board.ui.theme.board
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TreeViewModel @Inject constructor(private val repo: TreeRepository, private val clock: Clock) : ViewModel() {
    private val tick = MutableStateFlow(0)
    val state: StateFlow<TreeSummary?> = tick.flatMapLatest { repo.observe(LocalDate.now(clock)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** با بازگشت به برنامه (شاید هفته یا ماه عوض شده) دوباره حساب می‌شود. */
    fun refresh() { tick.value++ }
}

/** شمارندهٔ خانه: «۸ از ۱۴ کار این هفته» درشت، و ماه و فصل با مقایسهٔ آرام با دورهٔ قبل. */
@Composable
fun GrowthCard(vm: TreeViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val sum = s ?: return
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.board.line, shape).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val planned = sum.weekPlanned
        if (planned == 0) {
            Text("برای این هفته کاری نیست", color = MaterialTheme.board.muted)
        } else {
            val left = (planned - sum.week.cur).coerceAtLeast(0)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(Digits.fa("${sum.week.cur}"), fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp)
                Text(Digits.fa("از $planned کار این هفته"), color = MaterialTheme.board.muted, modifier = Modifier.weight(1f).padding(bottom = 4.dp))
                if (left > 0) Text(Digits.fa("$left مانده"), color = MaterialTheme.board.amber, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 4.dp))
            }
            Box(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.board.slate)) {
                Box(Modifier.fillMaxWidth((sum.week.cur.toFloat() / planned).coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.primary))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            PeriodStat("ماه", sum.month, "ماه قبل", Modifier.weight(1f))
            PeriodStat(sum.seasonName, sum.season, "فصل قبل", Modifier.weight(1f))
        }
    }
}

@Composable
private fun PeriodStat(title: String, c: Count, prevLabel: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
            Text(Digits.fa("${c.cur}"), fontWeight = FontWeight.Bold, fontSize = 17.sp)
        }
        Text(
            Growth.compare(c.cur, c.prev, prevLabel), style = MaterialTheme.typography.labelSmall,
            color = if (c.cur > c.prev) MaterialTheme.colorScheme.primary else MaterialTheme.board.muted,
        )
    }
}
