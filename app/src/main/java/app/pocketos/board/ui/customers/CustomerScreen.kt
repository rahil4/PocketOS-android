package app.pocketos.board.ui.customers

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketos.board.domain.CaseStatus
import app.pocketos.board.domain.DayLabel
import app.pocketos.board.domain.Digits
import app.pocketos.board.domain.Phone
import app.pocketos.board.ui.common.Avatar
import app.pocketos.board.ui.common.CaseFormDialog
import app.pocketos.board.ui.common.CustomerCard
import app.pocketos.board.ui.common.CustomerFormDialog
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

@Composable
fun CustomerScreen(
    onBack: () -> Unit,
    onOpenCase: (Long) -> Unit,
    onOpenTag: (String) -> Unit,
    vm: CustomerViewModel = hiltViewModel(),
    stream: StreamViewModel = hiltViewModel(),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val others by vm.others.collectAsStateWithLifecycle()
    val ss by stream.state.collectAsStateWithLifecycle()
    val overlays = rememberOverlayState()
    val context = LocalContext.current
    var menu by rememberSaveable { mutableStateOf(false) }
    var edit by rememberSaveable { mutableStateOf(false) }
    var newCase by rememberSaveable { mutableStateOf(false) }
    var merge by rememberSaveable { mutableStateOf(false) }
    var closedOpen by rememberSaveable { mutableStateOf(false) }

    // مشتری حذف یا ادغام شد ← برگرد
    LaunchedEffect(st.loaded, st.customer == null) { if (st.loaded && st.customer == null) onBack() }
    val c = st.customer ?: return
    LaunchedEffect(c.id) { stream.bind(StreamScope.Customer(c.id)) }

    if (edit) {
        CustomerFormDialog("ویرایش مشتری", c.name, c.phone, "ذخیره", { edit = false }) { n, p -> edit = false; vm.update(n, p) }
    }
    if (newCase) {
        CaseFormDialog("پروندهٔ تازه", vm.today, "", null, "ساخت", { newCase = false }) { t, d ->
            newCase = false
            vm.createCase(t, d) { id -> onOpenCase(id) }
        }
    }
    if (merge) {
        CustomerPickerDialog(
            "ادغام «${c.name}» در…", others.filter { it.id != c.id }.map { PickItem(it.id, it.name, it.phone) },
            { merge = false },
        ) { target -> merge = false; vm.merge(target.id) {} }
    }
    StreamOverlays(stream, overlays)

    val open = st.cases.filter { CaseStatus.of(it.status).open }
    val closed = st.cases.filter { !CaseStatus.of(it.status).open }
    val tasksByCase = ss.tasks.groupBy { it.caseId }
    val nowMillis = System.currentTimeMillis()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
        ) {
            item { TextButton(onBack) { Text("‹ مشتری‌ها") } }
            item {
                CustomerCard(radius = 18.dp) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Avatar(c.name, c.id, 54.dp)
                        Column(Modifier.weight(1f)) {
                            Text((if (c.pinnedAt != null) "★ " else "") + c.name, style = MaterialTheme.typography.titleLarge)
                            if (c.phone.isNotBlank()) Text(Phone.display(c.phone), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.board.muted)
                            if (c.archivedAt != null) Text("بایگانی‌شده", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
                        }
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (c.phone.isNotBlank()) {
                        Pill("تماس") { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.phone}"))) }
                        Pill("پیام") { context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${c.phone}"))) }
                    }
                    Column {
                        Pill("⋯ بیشتر") { menu = true }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("ویرایش نام و تلفن") }, { menu = false; edit = true })
                            DropdownMenuItem(
                                { Text(if (c.pinnedAt != null) "برداشتن ستاره" else "★ ستاره‌دار کردن") },
                                { menu = false; vm.setStar(c.pinnedAt == null) },
                            )
                            DropdownMenuItem({ Text("ادغام با مشتری دیگر…") }, { menu = false; merge = true })
                            DropdownMenuItem(
                                { Text(if (c.archivedAt != null) "برگرداندن از بایگانی" else "بایگانی") },
                                { menu = false; vm.setArchived(c.archivedAt == null) },
                            )
                            DropdownMenuItem({ Text("حذف (به سطل بازیافت)") }, { menu = false; vm.delete(c.name) })
                        }
                    }
                }
            }
            // کارهای بدون پرونده: مستقیم زیر مشتری
            val rootTasks = tasksByCase[null].orEmpty()
            if (rootTasks.isNotEmpty()) {
                item { SectionLabel("کارها") }
                items(rootTasks, key = { "t${it.id}" }) { TaskRow(it, stream, onOpenTag) }
            }
            item { SectionLabel("پرونده‌ها") }
            items(open, key = { "c${it.id}" }) { k ->
                CaseCard(k.id, k.title, k.status, k.dueDay, vm.today, { onOpenCase(k.id) }) {
                    tasksByCase[k.id].orEmpty().forEach { TaskRow(it, stream, onOpenTag) }
                }
            }
            item { Pill("＋ پروندهٔ تازه", primary = true) { newCase = true } }
            if (closed.isNotEmpty()) {
                item {
                    TextButton({ closedOpen = !closedOpen }) {
                        Text((if (closedOpen) "▾" else "▸") + Digits.fa(" نهایی و آرشیو (${closed.size})"))
                    }
                }
                if (closedOpen) items(closed, key = { "x${it.id}" }) { k -> CaseCard(k.id, k.title, k.status, k.dueDay, vm.today, { onOpenCase(k.id) }) {} }
            }
            historySection(ss, stream, nowMillis, onOpenTag) { overlays.viewer = it }
        }
        CaptureBar(stream, onPickTarget = { overlays.picker = true }, onNote = { overlays.openNote(false) }, onTask = { overlays.openNote(true) })
    }
}

@Composable
internal fun CaseCard(
    id: Long,
    title: String,
    status: String,
    dueDay: String?,
    today: LocalDate,
    onClick: () -> Unit,
    tasks: @Composable () -> Unit = {},
) {
    val due = dueDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val late = due != null && !due.isAfter(today)
    StripeCard(stripeColor(id), onClick = onClick) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        val sub = buildList {
            add(CaseStatus.of(status).label)
            if (due != null) add("مهلت " + DayLabel.of(due, today))
        }.joinToString(" · ")
        Text(sub, style = MaterialTheme.typography.bodySmall, color = if (late) MaterialTheme.board.amber else MaterialTheme.board.muted)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) { tasks() }
    }
}
