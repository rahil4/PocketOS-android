package app.pocketos.board.ui.tags

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketos.board.domain.DayLabel
import app.pocketos.board.domain.Digits
import app.pocketos.board.domain.Tags
import app.pocketos.board.domain.When
import app.pocketos.board.ui.common.EmptyNote
import app.pocketos.board.ui.common.SectionLabel
import app.pocketos.board.ui.theme.board
import java.time.LocalDate

/** نتیجهٔ جست‌وجوی دقیق یک برچسب: کارهای باز، یادداشت‌ها، «✓ انجام‌شده». */
@Composable
fun TagScreen(onBack: () -> Unit, vm: TagViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    var doneOpen by rememberSaveable { mutableStateOf(false) }
    val r = st.results

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 24.dp),
    ) {
        item { TextButton(onBack) { Text("‹ بازگشت") } }
        item { Text("#${vm.tag}", style = MaterialTheme.typography.headlineMedium) }
        if (r != null && r.openTasks.isEmpty() && r.notes.isEmpty() && r.doneTasks.isEmpty()) {
            item { EmptyNote("چیزی با این برچسب نیست.") }
        }
        if (r != null && r.openTasks.isNotEmpty()) {
            item { SectionLabel("کارهای باز") }
            items(r.openTasks, key = { "t${it.id}" }) { t ->
                val due = t.dueDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                val late = due != null && !due.isAfter(vm.today)
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.board.slate).padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clickable { vm.done(t.id) }, contentAlignment = Alignment.Center) {
                        Box(Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(Digits.fa(Tags.strip(t.title).ifEmpty { t.title }), style = MaterialTheme.typography.bodyMedium)
                        val sub = buildList {
                            if (due != null) add(DayLabel.of(due, vm.today))
                            if (t.repeat != null) add(When.repeatLabel(t.repeat))
                        }.joinToString(" · ")
                        if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = if (late) MaterialTheme.board.amber else MaterialTheme.board.muted)
                    }
                }
            }
        }
        if (r != null && r.notes.isNotEmpty()) {
            item { SectionLabel("یادداشت‌ها") }
            items(r.notes, key = { "n${it.id}" }) { n ->
                Text(
                    Digits.fa(Tags.strip(n.text).ifEmpty { n.text }), style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.board.slate).padding(12.dp),
                )
            }
        }
        if (r != null && r.doneTasks.isNotEmpty()) {
            item { TextButton({ doneOpen = !doneOpen }) { Text((if (doneOpen) "▾" else "▸") + Digits.fa(" ✓ انجام‌شده (${r.doneTasks.size})")) } }
            if (doneOpen) items(r.doneTasks, key = { "d${it.id}" }) { t ->
                Text(
                    "✓ " + Digits.fa(Tags.strip(t.title).ifEmpty { t.title }), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.board.muted, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}
