package app.pocketos.board.ui.cases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
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
import app.pocketos.board.ui.common.Chip
import app.pocketos.board.ui.common.CustomerPickerDialog
import app.pocketos.board.ui.common.EmptyNote
import app.pocketos.board.ui.common.PickItem
import app.pocketos.board.ui.common.Pill
import app.pocketos.board.ui.common.SectionLabel
import app.pocketos.board.ui.common.StripeCard
import app.pocketos.board.ui.common.stripeColor
import app.pocketos.board.ui.theme.board
import java.time.LocalDate

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CaseScreen(onBack: () -> Unit, onOpenCustomer: (Long) -> Unit, vm: CaseViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val others by vm.others.collectAsStateWithLifecycle()
    var edit by rememberSaveable { mutableStateOf(false) }
    var move by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(st.loaded, st.case == null) { if (st.loaded && st.case == null) onBack() }
    val k = st.case ?: return
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

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 24.dp),
    ) {
        item {
            TextButton({ onOpenCustomer(k.customerId) }) { Text("‹ " + (st.customer?.name ?: "مشتری")) }
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
                CaseStatus.entries.forEach { s ->
                    Chip(s.label, selected = k.status == s.code) { vm.setStatus(s) }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("ویرایش") { edit = true }
                Pill("انتقال به مشتری دیگر") { move = true }
                Pill("حذف") { vm.delete(k.title) }
            }
        }
        item { SectionLabel("سابقه") }
        item { EmptyNote("هنوز چیزی ثبت نشده. یادداشت، عکس و کار از مرحلهٔ بعد می‌آید.") }
    }
}
