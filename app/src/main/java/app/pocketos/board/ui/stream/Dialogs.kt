package app.pocketos.board.ui.stream

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketos.board.data.db.ItemEntity
import app.pocketos.board.data.db.TaskEntity
import app.pocketos.board.data.repo.Target
import app.pocketos.board.domain.DayLabel
import app.pocketos.board.domain.Digits
import app.pocketos.board.domain.Normalize
import app.pocketos.board.domain.When
import app.pocketos.board.ui.common.Avatar
import app.pocketos.board.ui.common.Chip
import app.pocketos.board.ui.common.DueChips
import app.pocketos.board.ui.common.Pill
import app.pocketos.board.ui.common.TagChip
import app.pocketos.board.ui.theme.board
import java.time.LocalDate

private val TAG_AT_END = Regex("#([\\p{L}\\p{N}_]*)$")

/** یادداشت جدید: روز و تکرار از متن تشخیص داده می‌شود؛ هیچ فیلد اجباری نیست. */
@Composable
fun NoteDialog(vm: StreamViewModel, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val label by vm.targetLabel.collectAsStateWithLifecycle()
    val allTags by produceState(emptyList<String>()) { value = vm.tagSuggestions() }
    val cap = remember(text) { When.analyze(text, vm.today) }
    val tgt by vm.target.collectAsStateWithLifecycle()
    var chosen by rememberSaveable { mutableStateOf<Long?>(null) }
    // فقط پیشنهاد: در صندوق، متقاضی‌های همنام متن (و برای کار زماندار، اخیرها) نشان داده می‌شود.
    val sugg by produceState(emptyList<app.pocketos.board.data.db.CustomerEntity>(), text, tgt, cap != null) {
        value = if (tgt.isInbox) vm.customerSuggestions(text, withRecent = cap != null) else emptyList()
    }
    val prefix = TAG_AT_END.find(text)?.groupValues?.get(1)
    val suggestions = if (prefix == null) emptyList() else allTags.filter { it.startsWith(Normalize.key(prefix)) && it != Normalize.key(prefix) }.take(6)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("یادداشت · $label") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    text, { text = it },
                    placeholder = { Text("بنویس… («فردا تحویل نقشه» کار می‌شود)") },
                    minLines = 3, modifier = Modifier.fillMaxWidth(),
                )
                if (suggestions.isNotEmpty()) {
                    TagRow(suggestions) { tag -> text = text.replace(TAG_AT_END, "#$tag ") }
                }
                if (cap != null) {
                    Text(
                        "روز تشخیص داده شد: " + DayLabel.of(cap.due, vm.today) +
                            (if (cap.repeat != null) " · " + When.repeatLabel(cap.repeat) else ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                    )
                }
                if (sugg.isNotEmpty()) {
                    Text(
                        if (cap != null) "این کار برای کدام متقاضی است؟ (پیشنهاد؛ می‌توانی بی‌متقاضی هم ثبت کنی)" else "برای کدام متقاضی است؟ (پیشنهاد)",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted,
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        sugg.forEach { c -> Chip(c.name, selected = chosen == c.id) { chosen = if (chosen == c.id) null else c.id } }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (cap != null) Pill("ثبت کار برای ${DayLabel.of(cap.due, vm.today)}", primary = true) { vm.saveTaskFromText(text, chosen); onDismiss() }
                    Pill("ثبت یادداشت", primary = cap == null) { vm.saveNote(text, chosen); onDismiss() }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("بیخیال") } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagRow(tags: List<String>, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEach { TagChip(it) { onPick(it) } }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RepeatChips(selected: String?, onSelect: (String?) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip("بدون تکرار", selected = selected == null) { onSelect(null) }
        listOf("daily", "weekly", "monthly").forEach { c -> Chip(When.repeatLabel(c), selected = selected == c) { onSelect(c) } }
        if (selected != null && selected.startsWith("every:")) Chip(When.repeatLabel(selected), selected = true)
    }
}

/** ویرایش کار: عنوان، مهلت، تکرار. */
@Composable
fun EditTaskDialog(vm: StreamViewModel, task: TaskEntity, onDismiss: () -> Unit) {
    var title by rememberSaveable { mutableStateOf(task.title) }
    var due by remember { mutableStateOf(task.dueDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }) }
    var repeat by remember { mutableStateOf(task.repeat) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ویرایش کار") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Text("مهلت", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.board.muted)
                DueChips(vm.today, due) { due = it; if (it == null) repeat = null }
                if (due != null) {
                    Text("تکرار", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.board.muted)
                    RepeatChips(repeat) { repeat = it }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { vm.editTask(task, title, due, repeat); onDismiss() }) { Text("ذخیره") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بیخیال") } },
    )
}

/** تغییر روزِ همین نوبت: چیپ‌های هفتهٔ آینده، یک روز عقب/جلو، یا نوشتن روز («۲۸ مهر»، «+۱۰»). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoveDayDialog(vm: StreamViewModel, task: TaskEntity, onDismiss: () -> Unit) {
    val today = vm.today
    val current = task.dueDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    var typed by rememberSaveable { mutableStateOf("") }
    val parsed = remember(typed) { When.parse(typed, today)?.takeIf { it.second.isEmpty() }?.first }
    fun apply(d: LocalDate) { vm.setDay(task, d); onDismiss() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تغییر روز") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0L..6L).map { today.plusDays(it) }.forEach { d ->
                        Chip(DayLabel.of(d, today), selected = d == current) { if (d != current) apply(d) }
                    }
                }
                if (current != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("یک روز عقب") { apply(current.minusDays(1)) }
                        Chip("یک روز جلو") { apply(current.plusDays(1)) }
                        Chip("یک هفته دیگر") { apply(current.plusDays(7)) }
                    }
                }
                OutlinedTextField(
                    typed, { typed = it }, singleLine = true, label = { Text("روز دلخواه: «۲۸ مهر» یا «+۱۰»") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (parsed != null) Text(DayLabel.of(parsed, today), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(enabled = parsed != null, onClick = { apply(parsed!!) }) { Text("ببر") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بیخیال") } },
    )
}

/** تبدیل یادداشت به کار: روز حدس‌زده‌شده از متن، چیپ‌های روز و تکرار. */
@Composable
fun ConvertToTaskDialog(vm: StreamViewModel, item: ItemEntity, onDismiss: () -> Unit) {
    val guess = remember(item.id) { When.analyze(item.text, vm.today) }
    var title by rememberSaveable { mutableStateOf(guess?.title ?: item.text) }
    var due by remember { mutableStateOf(guess?.due) }
    var repeat by remember { mutableStateOf(guess?.repeat) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تبدیل به کار با زمان") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                DueChips(vm.today, due) { due = it; if (it == null) repeat = null }
                if (due != null) RepeatChips(repeat) { repeat = it }
            }
        },
        confirmButton = {
            TextButton(enabled = title.isNotBlank(), onClick = { vm.noteToTask(item, title, due, repeat); onDismiss() }) { Text("تبدیل کن") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بیخیال") } },
    )
}

/** ویرایش متن یادداشت (فقط یادداشت متنی). */
@Composable
fun EditNoteDialog(vm: StreamViewModel, item: ItemEntity, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf(item.text) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ویرایش یادداشت") },
        text = { OutlinedTextField(text, { text = it }, minLines = 3, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { vm.editNote(item, text); onDismiss() }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بیخیال") } },
    )
}

/** «ثبت در» و «انتقال به»: صندوق ورودی، ۶ پروندهٔ اخیر، یا هر متقاضی. */
@Composable
fun TargetPickerDialog(vm: StreamViewModel, title: String, onDismiss: () -> Unit, onPick: (Target) -> Unit) {
    val recent by vm.recentCases.collectAsStateWithLifecycle()
    val customers by vm.customers.collectAsStateWithLifecycle()
    var q by rememberSaveable { mutableStateOf("") }
    val words = Normalize.words(q)
    val shownCustomers = customers.filter { c -> words.all { it in c.searchText } }.take(50)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(q, { q = it }, singleLine = true, label = { Text("جست‌وجوی متقاضی") }, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 340.dp)) {
                    if (q.isBlank()) {
                        item("inbox") { PickRow("صندوق ورودی", null) { onPick(Target.Inbox) } }
                        items(recent, key = { "k${it.id}" }) { r ->
                            PickRow(r.customerName + " › " + r.title, r.customerId) { onPick(Target(r.customerId, r.id)) }
                        }
                    }
                    items(shownCustomers, key = { "c${it.id}" }) { c ->
                        PickRow(c.name + (if (q.isBlank()) " · بدون پرونده" else ""), c.id) { onPick(Target(c.id, null)) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("بیخیال") } },
    )
}

@Composable
private fun PickRow(text: String, avatarId: Long?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (avatarId != null) Avatar(text, avatarId, 28.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/** «پرونده هم نهایی شود؟» */
@Composable
fun FinishCaseDialog(onYes: () -> Unit, onNo: () -> Unit, onNever: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        title = { Text("کارهای این پرونده تمام شد") },
        text = { Text("پرونده هم نهایی شود؟") },
        confirmButton = { TextButton(onClick = onYes) { Text("نهایی شود") } },
        dismissButton = {
            Row {
                TextButton(onClick = onNo) { Text("نه، باز بماند") }
                TextButton(onClick = onNever) { Text("دیگر نپرس") }
            }
        },
    )
}

internal fun fa(n: Int) = Digits.fa(n)
