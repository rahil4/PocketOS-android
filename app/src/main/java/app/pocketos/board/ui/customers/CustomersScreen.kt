package app.pocketos.board.ui.customers

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketos.board.domain.CustomerSummary
import app.pocketos.board.domain.DayLabel
import app.pocketos.board.domain.Digits
import app.pocketos.board.domain.RelativeTime
import app.pocketos.board.ui.common.Avatar
import app.pocketos.board.ui.common.Chip
import app.pocketos.board.ui.common.CustomerFormDialog
import app.pocketos.board.ui.common.Pill
import app.pocketos.board.ui.common.SectionLabel
import app.pocketos.board.ui.common.faCount
import app.pocketos.board.ui.common.stripeColor
import app.pocketos.board.ui.theme.board
import java.time.Clock
import java.time.LocalDate

private const val PAGE = 120

@Composable
fun CustomersScreen(
    onOpenCustomer: (Long) -> Unit,
    onOpenCase: (Long) -> Unit,
    onOpenTrash: () -> Unit,
    vm: CustomersViewModel = hiltViewModel(),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val q by vm.query.collectAsStateWithLifecycle()
    val alpha by vm.alpha.collectAsStateWithLifecycle()
    var showNew by rememberSaveable { mutableStateOf(false) }
    var newName by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableIntStateOf(PAGE) }
    var oldOpen by rememberSaveable { mutableStateOf(false) }
    var archOpen by rememberSaveable { mutableStateOf(false) }
    val today = remember { LocalDate.now(Clock.systemDefaultZone()) }
    val nowMillis = remember(st) { System.currentTimeMillis() }

    if (showNew) {
        CustomerFormDialog(
            title = "مشتری تازه", initialName = newName, initialPhone = "", confirmLabel = "ساخت",
            onDismiss = { showNew = false },
            onConfirm = { n, p ->
                showNew = false
                vm.create(n, p) { id -> onOpenCustomer(id) }
            },
        )
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("مشتری‌ها", style = MaterialTheme.typography.headlineMedium)
                if (st.loaded) {
                    Text(
                        Digits.fa("${st.total} مشتری · ${st.withTasks} کار دارند"),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted,
                    )
                }
            }
            Pill("＋ جدید", primary = true) { newName = ""; showNew = true }
        }
        OutlinedTextField(
            value = q,
            onValueChange = { vm.query.value = it; limit = PAGE },
            placeholder = { Text("جست‌وجو (نام یا تلفن)") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(99.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            val sec = st.sections
            val results = sec.results
            if (results != null) {
                if (!st.exactMatch && q.isNotBlank()) {
                    item("create") {
                        Text(
                            "＋ ساخت مشتری «${q.trim()}»",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                .clickable { newName = q.trim(); showNew = true }
                                .padding(12.dp),
                        )
                    }
                }
                items(results.take(limit), key = { "r${it.id}" }) { s ->
                    CustomerRow(s, today, nowMillis, onOpenCustomer, onOpenCase)
                }
                if (results.size > limit) item("more") { TextButton({ limit += PAGE }) { Text("نمایش بیشتر") } }
                if (results.isEmpty()) item("none") { Text("نتیجه‌ای نیست", color = MaterialTheme.board.muted, modifier = Modifier.padding(8.dp)) }
            } else {
                // مرتب‌سازی: شبیه هم‌اند / مشتری خالی
                st.similar?.let { (a, b) ->
                    item("similar") {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.board.slate).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("شبیه هم‌اند؟", style = MaterialTheme.typography.labelLarge)
                            Text("«${a.name}» و «${b.name}»", style = MaterialTheme.typography.bodyMedium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Pill("ادغام در «${a.name}»", primary = true) { vm.merge(b.id, a.id) }
                                Pill("جدا هستند") { vm.notSimilar(a.id, b.id) }
                            }
                        }
                    }
                }
                if (st.empties.isNotEmpty()) {
                    item("empties") {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.board.amberSoft).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(Digits.fa("${st.empties.size} مشتری خالی"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.board.amber)
                            Text("بدون پرونده، یادداشت و کار", style = MaterialTheme.typography.bodySmall)
                            Pill("بایگانی همه") { vm.archiveAll(st.empties.map { it.id }) }
                        }
                    }
                }
                if (st.loaded && st.total == 0 && sec.archived.isEmpty()) {
                    item("empty") {
                        Text(
                            "هنوز مشتری‌ای ثبت نشده. با «＋ جدید» اولین مشتری را بساز.",
                            color = MaterialTheme.board.muted,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        )
                    }
                }
                if (sec.starred.isNotEmpty()) {
                    item("h-star") { SectionLabel("★ ستاره‌دار") }
                    items(sec.starred, key = { "s${it.id}" }) { CustomerRow(it, today, nowMillis, onOpenCustomer, onOpenCase) }
                }
                if (sec.withTasks.isNotEmpty()) {
                    item("h-tasks") { SectionLabel("کار دارند") }
                    items(sec.withTasks, key = { "t${it.id}" }) { CustomerRow(it, today, nowMillis, onOpenCustomer, onOpenCase) }
                }
                if (sec.others.isNotEmpty()) {
                    item("h-others") {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            SectionLabel("بقیه", Modifier.weight(1f))
                            Chip(if (alpha) "الفبایی" else "آخرین فعالیت", selected = true) { vm.alpha.value = !alpha }
                        }
                    }
                    items(sec.others.take(limit), key = { "o${it.id}" }) { CustomerRow(it, today, nowMillis, onOpenCustomer, onOpenCase, dim = true) }
                    if (sec.others.size > limit) item("more") { TextButton({ limit += PAGE }) { Text("نمایش بیشتر") } }
                }
                if (sec.old.isNotEmpty()) {
                    item("h-old") { TextButton({ oldOpen = !oldOpen }) { Text((if (oldOpen) "▾" else "▸") + Digits.fa(" قدیمی‌ها (${sec.old.size})")) } }
                    if (oldOpen) items(sec.old, key = { "d${it.id}" }) { CustomerRow(it, today, nowMillis, onOpenCustomer, onOpenCase, dim = true) }
                }
                if (sec.archived.isNotEmpty()) {
                    item("h-arch") { TextButton({ archOpen = !archOpen }) { Text((if (archOpen) "▾" else "▸") + Digits.fa(" بایگانی‌شده (${sec.archived.size})")) } }
                    if (archOpen) items(sec.archived, key = { "a${it.id}" }) { CustomerRow(it, today, nowMillis, onOpenCustomer, onOpenCase, dim = true) }
                }
                item("trash") { TextButton(onOpenTrash) { Text("سطل بازیافت") } }
            }
        }
    }
}

@Composable
private fun CustomerRow(
    s: CustomerSummary,
    today: LocalDate,
    nowMillis: Long,
    onOpen: (Long) -> Unit,
    onOpenCase: (Long) -> Unit,
    dim: Boolean = false,
) {
    val context = LocalContext.current
    val late = s.nextDue != null && !s.nextDue!!.isAfter(today)
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.board.slate)
            .clickable { onOpen(s.id) }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Avatar(s.name, s.id)
            Column(Modifier.weight(1f)) {
                Text(
                    (if (s.archived) "بایگانی · " else "") + s.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (dim) MaterialTheme.board.muted else MaterialTheme.colorScheme.onSurface,
                )
                val sub = buildList {
                    if (s.openCases > 0) add(faCount(s.openCases, "پرونده"))
                    add(RelativeTime.ago(s.lastActivity, nowMillis))
                    if (s.nextDue != null) add("مهلت " + DayLabel.of(s.nextDue!!, today))
                }.joinToString(" · ")
                Text(sub, style = MaterialTheme.typography.bodySmall, color = if (late) MaterialTheme.board.amber else MaterialTheme.board.muted)
            }
            if (s.openTasks > 0) Chip(Digits.fa(s.openTasks), selected = late, amber = late)
            if (s.phone.isNotBlank()) {
                androidx.compose.foundation.layout.Box(
                    Modifier.size(36.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface)
                        .clickable { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${s.phone}"))) },
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Phone, contentDescription = "تماس", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
            }
        }
        if (s.cases.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                s.cases.forEach { c ->
                    Row(
                        Modifier.clip(RoundedCornerShape(99.dp)).background(MaterialTheme.colorScheme.surface)
                            .clickable { onOpenCase(c.id) }.padding(end = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.foundation.layout.Box(Modifier.size(width = 4.dp, height = 22.dp).background(stripeColor(c.id)))
                        Text(c.title, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
            }
        }
    }
}
