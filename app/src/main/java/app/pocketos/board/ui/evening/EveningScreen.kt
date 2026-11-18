package app.pocketos.board.ui.evening

import androidx.compose.foundation.border
import app.pocketos.board.ui.stream.TaskRow
import app.pocketos.board.ui.stream.StreamViewModel
import app.pocketos.board.ui.common.Avatar
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.key
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketos.board.domain.Digits
import app.pocketos.board.domain.Tags
import app.pocketos.board.ui.common.Chip
import app.pocketos.board.ui.common.Pill
import app.pocketos.board.ui.theme.board

private val CHOICES = listOf("tomorrow" to "فردا", "after" to "پس‌فردا", "week" to "هفتهٔ بعد", "drop" to "رها کن")

/** «امروز را ببندیم»: هر کار مانده کجا برود؟ */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EveningScreen(onBack: () -> Unit, vm: EveningViewModel = hiltViewModel(), stream: StreamViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
    ) {
        item("top") { Row(Modifier.fillMaxWidth()) { Pill("←", onClick = onBack) } }
        item("big") { Text("امروز را ببندیم", fontSize = 24.sp, fontWeight = FontWeight.SemiBold) }
        item("mini") {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(Digits.fa("${st.made} ثبت"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.board.muted)
                Text(Digits.fa("${st.done} کار انجام شد ✓"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.board.muted)
            }
        }
        if (st.loaded && st.tasks.isNotEmpty()) {
            item("q") {
                Text(
                    if (st.tasks.size == 1) "این کار مانده؛ کجا برود؟" else Digits.fa("${st.tasks.size} کار مانده؛ هر کدام کجا برود؟"),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.board.muted,
                )
            }
            // مثل تقویم: کارت برای هر متقاضی، ردیف کار با حلقه و منوی ⋯، و زیرش انتخاب «کجا برود؟»
            val groups = st.tasks.groupBy { it.task.customerId }
            groups.forEach { (cid, rows) ->
                item("g${cid ?: 0}") {
                    val shape = RoundedCornerShape(14.dp)
                    Column(
                        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)
                            .border(1.dp, MaterialTheme.board.line, shape).padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        val name = rows.first().customerName
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (name != null && cid != null) Avatar(name, cid, 26.dp)
                            Text(name ?: "بدون متقاضی", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            Text(Digits.fa("${rows.size} کار"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
                        }
                        rows.forEach { r ->
                            key(r.task.id) {
                                TaskRow(r.task, stream, onTag = {}, done = false, showDue = true) {
                                    r.caseTitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted) }
                                    FlowRow(
                                        Modifier.padding(top = 4.dp, bottom = 4.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        CHOICES.forEach { (code, label) -> Chip(label, selected = st.choices[r.task.id] == code) { vm.choose(r.task.id, code) } }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else if (st.loaded) {
            item("none") { Text("کار مانده‌ای نیست؛ همه‌چیز جایش است.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted) }
        }
        item("btn") {
            Column(Modifier.fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth()) { Pill("روز بسته شد", primary = true) { vm.close(onBack) } }
                Text("همه‌چیز جایش است و دیگر لازم نیست یادت بماند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
            }
        }
    }
}
