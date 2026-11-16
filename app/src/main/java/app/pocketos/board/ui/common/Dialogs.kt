package app.pocketos.board.ui.common

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.pocketos.board.domain.DayLabel
import app.pocketos.board.domain.Digits
import app.pocketos.board.domain.Normalize
import app.pocketos.board.ui.theme.board
import java.time.LocalDate

/** گفت‌وگوی «نام + تلفن» برای مشتری. */
@Composable
fun CustomerFormDialog(
    title: String,
    initialName: String,
    initialPhone: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    contactPicker: Boolean = false,
    onConfirm: (name: String, phone: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var phone by rememberSaveable { mutableStateOf(initialPhone) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // انتخاب‌گر خود اندروید: بدون مجوز مخاطبین؛ هر بار یک مخاطب.
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data
        if (r.resultCode == android.app.Activity.RESULT_OK && uri != null) {
            runCatching {
                ctx.contentResolver.query(
                    uri,
                    arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null, null, null,
                )?.use { c ->
                    if (c.moveToFirst()) {
                        name = c.getString(0).orEmpty()
                        phone = c.getString(1).orEmpty()
                    }
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("نام") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    phone, { phone = it }, label = { Text("تلفن (اختیاری)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth(),
                )
                if (contactPicker) {
                    Pill("از مخاطبین…") {
                        runCatching {
                            pick.launch(android.content.Intent(android.content.Intent.ACTION_PICK, android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onConfirm(name, phone) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بیخیال") } },
    )
}

/** مهلتِ سریع: چیپ‌های روز؛ null یعنی بدون مهلت. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DueChips(today: LocalDate, selected: LocalDate?, onSelect: (LocalDate?) -> Unit) {
    val options = listOf(1L, 3L, 7L, 14L, 30L).map { today.plusDays(it) }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Chip("بدون مهلت", selected = selected == null, onClick = { onSelect(null) })
        options.forEach { d -> Chip(DayLabel.of(d, today), selected = selected == d, onClick = { onSelect(d) }) }
        if (selected != null && selected !in options) Chip(DayLabel.of(selected, today), selected = true)
    }
}

/** گفت‌وگوی «عنوان + مهلت» برای پرونده. */
@Composable
fun CaseFormDialog(
    title: String,
    today: LocalDate,
    initialTitle: String,
    initialDue: LocalDate?,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (title: String, due: LocalDate?) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initialTitle) }
    var due by remember { mutableStateOf(initialDue) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text("عنوان پرونده") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("مهلت", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.board.muted)
                DueChips(today, due) { due = it }
            }
        },
        confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { onConfirm(text, due) }) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("بیخیال") } },
    )
}

data class PickItem(val id: Long, val name: String, val phone: String)

/** انتخاب یک مشتری با جست‌وجو (ادغام، انتقال پرونده). */
@Composable
fun CustomerPickerDialog(
    title: String,
    items: List<PickItem>,
    onDismiss: () -> Unit,
    onPick: (PickItem) -> Unit,
) {
    var q by rememberSaveable { mutableStateOf("") }
    val shown = items.filter { p -> Normalize.words(q).all { it in Normalize.key("${p.name} ${p.phone}") } }.take(60)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(q, { q = it }, label = { Text("جست‌وجو") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(shown, key = { it.id }) { p ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Avatar(p.name, p.id, 30.dp)
                            Text(p.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (shown.isEmpty()) item { Text("نتیجه‌ای نیست", color = MaterialTheme.board.muted, modifier = Modifier.padding(8.dp)) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("بیخیال") } },
    )
}

/** «۲ پرونده · دیروز» و مانند آن. */
fun faCount(n: Int, unit: String): String = Digits.fa("$n $unit")
