package app.pocketos.board.ui.cases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketos.board.domain.CaseStatus
import app.pocketos.board.domain.DayLabel
import app.pocketos.board.ui.common.CaseFormDialog
import app.pocketos.board.ui.common.Crumb
import app.pocketos.board.ui.common.Chip
import app.pocketos.board.ui.common.CustomerPickerDialog
import app.pocketos.board.ui.common.PickItem
import app.pocketos.board.ui.common.Pill
import app.pocketos.board.ui.common.SectionLabel
import app.pocketos.board.ui.common.StripeCard
import app.pocketos.board.ui.common.stripeColor
import app.pocketos.board.ui.stream.CaptureBar
import app.pocketos.board.ui.stream.StreamOverlays
import app.pocketos.board.ui.stream.StreamScope
import app.pocketos.board.ui.stream.StreamViewModel
import app.pocketos.board.ui.stream.TaskRow
import app.pocketos.board.ui.stream.historySection
import app.pocketos.board.ui.stream.rememberOverlayState
import app.pocketos.board.ui.theme.board
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaseScreen(
    onBack: () -> Unit,
    onOpenCustomer: (Long) -> Unit,
    onOpenTag: (String) -> Unit,
    vm: CaseViewModel = hiltViewModel(),
    stream: StreamViewModel = hiltViewModel(),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val others by vm.others.collectAsStateWithLifecycle()
    val ss by stream.state.collectAsStateWithLifecycle()
    val overlays = rememberOverlayState()
    var edit by rememberSaveable { mutableStateOf(false) }
    var move by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(st.loaded, st.case == null) { if (st.loaded && st.case == null) onBack() }
    val k = st.case ?: return
    LaunchedEffect(k.id) { stream.bind(StreamScope.Case(k.id)) }
    // مثل وب‌اپ: بعد از ۱٫۵ ثانیه ماندن روی پروندهٔ «جدید»، «در حال انجام» می‌شود.
    LaunchedEffect(k.id, k.status) {
        if (k.status == CaseStatus.NEW.code) {
            kotlinx.coroutines.delay(1500)
            vm.markOpened()
        }
    }
    val due = k.dueDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val late = due != null && !due.isAfter(vm.today) && CaseStatus.of(k.status).open

    if (edit) {
        CaseFormDialog("ویرایش پرونده", vm.today, k.title, due, "ذخیره", { edit = false }) { t, d -> edit = false; vm.update(t, d) }
    }
    if (move) {
        CustomerPickerDialog(
            "انتقال پرونده به…", others.filter { it.id != k.customerId }.map { PickItem(it.id, it.name, it.phone) },
            { move = false },
        ) { target -> move = false; vm.moveTo(target.id) }
    }
    StreamOverlays(stream, overlays)
    val nowMillis = System.currentTimeMillis()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
        ) {
            item {
                Row(Modifier.padding(top = 4.dp)) {
                    st.customer?.let { Crumb(it.name, it.id) { onOpenCustomer(k.customerId) } }
                        ?: TextButton({ onOpenCustomer(k.customerId) }) { Text("‹ مشتری") }
                }
            }
            item {
                StripeCard(stripeColor(k.id)) {
                    Text(k.title, style = MaterialTheme.typography.titleLarge)
                    Text(
                        if (due != null) "مهلت: " + DayLabel.of(due, vm.today) else "بدون مهلت",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (late) MaterialTheme.board.amber else MaterialTheme.board.muted,
                    )
                }
            }
            item { SectionLabel("وضعیت") }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CaseStatus.entries.forEach { s -> Chip(s.label, selected = k.status == s.code) { vm.setStatus(s) } }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("ویرایش") { edit = true }
                    Pill("انتقال") { move = true }
                    Pill("حذف") { vm.delete(k.title) }
                }
            }
            if (ss.tasks.isNotEmpty()) {
                item { SectionLabel("کارها") }
                items(ss.tasks, key = { "t${it.id}" }) { TaskRow(it, stream, onOpenTag) }
            }
            historySection(ss, stream, nowMillis, onOpenTag) { overlays.viewer = it }
        }
        CaptureBar(stream, onPickTarget = { overlays.picker = true }, onNote = { overlays.openNote(false) }, onTask = { overlays.openNote(true) })
    }
}
