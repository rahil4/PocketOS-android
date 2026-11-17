package app.pocketos.board.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
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

    val seasonIndex: Int get() = Growth.seasonIndex(LocalDate.now(clock))

    /** با بازگشت به برنامه (شاید هفته یا ماه عوض شده) دوباره حساب می‌شود. */
    fun refresh() { tick.value++ }
}

private const val MAX_ACTIVE = 6

/** کارت درخت در خانه: شاخه‌های فعال، شمارندهٔ هفته و مقایسهٔ ماه و فصل. لمس شاخه ← متقاضی؛ جای دیگر ← درخت کامل. */
@Composable
fun TreeCard(onOpenCustomer: (Long) -> Unit, onOpenTree: () -> Unit, vm: TreeViewModel) {
    val s by vm.state.collectAsStateWithLifecycle()
    val sum = s ?: return
    val pal = rememberTreePalette(vm.seasonIndex)
    val active = sum.branches.filter { it.archivedAt == null }.take(MAX_ACTIVE)
    val rest = sum.branches.size - active.size
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.board.line, shape).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("درخت دفتر", fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            Text("${sum.seasonName} · همه ←", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted, modifier = Modifier.clickable(onClick = onOpenTree))
        }
        Box(Modifier.clip(RoundedCornerShape(14.dp)).background(pal.sky)) {
            TreeCanvas(active.map { it.spec() }, pal, full = false, height = 280.dp, onBranch = onOpenCustomer, onTree = onOpenTree)
        }
        Legend(pal)
        val left = (sum.weekPlanned - sum.week.cur).coerceAtLeast(0)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(Digits.fa("${sum.week.cur} از ${sum.weekPlanned} کار این هفته"), fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
            if (left > 0) Text(Digits.fa("$left مانده"), color = MaterialTheme.board.amber, style = MaterialTheme.typography.bodySmall)
        }
        if (rest > 0) Text(Digits.fa("$rest متقاضی دیگر روی شاخه‌های قدیمی‌اند."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CounterBox("ماه", sum.month, "ماه قبل", Modifier.weight(1f))
            CounterBox(sum.seasonName, sum.season, "فصل قبل", Modifier.weight(1f))
        }
    }
}

@Composable
private fun CounterBox(title: String, c: Count, prevLabel: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(14.dp)
    Column(modifier.clip(shape).border(1.dp, MaterialTheme.board.line, shape).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.board.muted)
        Text(Digits.fa("${c.cur}"), fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(Growth.compare(c.cur, c.prev, prevLabel), style = MaterialTheme.typography.labelSmall, color = if (c.cur > c.prev) MaterialTheme.colorScheme.primary else MaterialTheme.board.muted)
    }
}

@Composable
private fun Legend(pal: TreePalette) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        LegendDot(pal.leaf, "انجام‌شده")
        LegendDot(pal.fresh, "این هفته")
        LegendDot(pal.budLine, "مانده")
        LegendDot(pal.fruit, "پرونده نهایی")
    }
}

@Composable
private fun LegendDot(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(9.dp).clip(CircleShape).background(color))
        Box(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.board.muted)
    }
}

/** درخت کامل: همهٔ متقاضی‌ها، شمارنده‌ها با جزئیات و رنگ فصل‌ها. */
@Composable
fun TreeScreen(onBack: () -> Unit, onOpenCustomer: (Long) -> Unit, vm: TreeViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val pal = rememberTreePalette(vm.seasonIndex)
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
        ) {
            item { TextButton(onBack) { Text("‹ بازگشت") } }
            item { Text("درخت دفتر", style = MaterialTheme.typography.titleLarge) }
            val sum = s
            if (sum == null) return@LazyColumn
            item {
                val all = sum.branches.map { it.spec(faded = it.archivedAt != null || (it.open == 0 && it.doneWeek == 0 && it.done < 3)) }
                    .sortedBy { it.faded }
                Box(Modifier.clip(RoundedCornerShape(14.dp)).background(pal.sky)) {
                    TreeCanvas(all, pal, full = true, height = 380.dp, onBranch = onOpenCustomer, onTree = {})
                }
            }
            item {
                Text(
                    Digits.fa("${sum.branches.size} شاخه · ${sum.total} کار انجام‌شده · ${sum.fruits} میوه"),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            item { Legend(pal) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CounterRow("این هفته", Digits.fa("${sum.week.cur} از ${sum.weekPlanned}"), Growth.compare(sum.week.cur, sum.week.prev, "هفته قبل"))
                    CounterRow("این ماه", Digits.fa("${sum.month.cur}"), Growth.compare(sum.month.cur, sum.month.prev, "ماه قبل"))
                    CounterRow(sum.seasonName, Digits.fa("${sum.season.cur}"), Growth.compare(sum.season.cur, sum.season.prev, "فصل قبل"))
                }
            }
            item { SeasonRow(vm.seasonIndex, pal) }
            item {
                Text(
                    "رنگ درخت از فصل جلالی می‌آید؛ برگی نمی‌ریزد. هر کار انجام‌شده یک برگ است و هر پروندهٔ نهایی یک میوه.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted,
                )
            }
        }
    }
}

@Composable
private fun CounterRow(title: String, value: String, compare: String) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).border(1.dp, MaterialTheme.board.line, shape).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.board.muted)
            Text(compare, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
        }
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun SeasonRow(current: Int, pal: TreePalette) {
    val names = listOf("بهار", "تابستان", "پاییز", "زمستان")
    val dark = MaterialTheme.colorScheme.background.let { 0.2126f * it.red + 0.7152f * it.green + 0.0722f * it.blue } < 0.5f
    val cols = listOf(
        if (dark) Color(0xFF6FB181) else Color(0xFF8CBF93), if (dark) Color(0xFF7BD89A) else Color(0xFF2F7D50),
        if (dark) Color(0xFFD9A24E) else Color(0xFFC9923F), if (dark) Color(0xFF6F7D74) else Color(0xFF9AA89E),
    )
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        names.forEachIndexed { i, n ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(if (i == current) 34.dp else 26.dp).clip(CircleShape).background(cols[i]))
                Text(n, style = MaterialTheme.typography.labelSmall, fontWeight = if (i == current) FontWeight.Bold else FontWeight.Normal, color = if (i == current) MaterialTheme.colorScheme.onSurface else MaterialTheme.board.muted)
            }
        }
    }
}
