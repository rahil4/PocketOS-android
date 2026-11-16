package app.pocketos.board.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketos.board.data.repo.CalEntry
import app.pocketos.board.domain.CalGroup
import app.pocketos.board.domain.CalRange
import app.pocketos.board.domain.CalView
import app.pocketos.board.domain.CalendarLogic
import app.pocketos.board.domain.Digits
import app.pocketos.board.domain.Dot
import app.pocketos.board.domain.Jalali
import app.pocketos.board.domain.Tags
import app.pocketos.board.ui.common.Avatar
import app.pocketos.board.ui.common.Chip
import app.pocketos.board.ui.common.EmptyNote
import app.pocketos.board.ui.common.SectionLabel
import app.pocketos.board.ui.stream.StreamViewModel
import app.pocketos.board.ui.stream.TaskRow
import app.pocketos.board.ui.theme.board
import java.time.LocalDate

private val WEEKDAY_LETTERS = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

@Composable
fun CalendarScreen(
    onOpenCustomer: (Long) -> Unit,
    onOpenCase: (Long) -> Unit,
    vm: CalendarViewModel = hiltViewModel(),
    stream: StreamViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val searching by vm.searching.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val result by vm.search.collectAsStateWithLifecycle()
    val strip by vm.strip.collectAsStateWithLifecycle()
    val today = vm.today
    val nav = Nav(onOpenCustomer, onOpenCase) { vm.setTag(it) }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 16.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("تقویم", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton({
                vm.searching.value = !searching
                if (searching) vm.query.value = ""
            }) { Text(if (searching) "بستن جست‌وجو" else "جست‌وجو") }
        }

        if (searching) {
            OutlinedTextField(
                query, { vm.query.value = it }, singleLine = true, placeholder = { Text("عنوان، #برچسب، مشتری یا پرونده") },
                shape = RoundedCornerShape(99.dp), modifier = Modifier.fillMaxWidth(),
            )
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
                val r = result
                if (r == null) item { EmptyNote("بنویس تا در کارها، برچسب‌ها و مهلت پرونده‌ها بگردم.") }
                else {
                    if (r.upcoming.isEmpty() && r.past.isEmpty()) item { EmptyNote("نتیجه‌ای نیست.") }
                    if (r.upcoming.isNotEmpty()) {
                        item { SectionLabel("پیش رو") }
                        items(r.upcoming, key = { "u${it.item.key}" }) { EntryRow(it, today, stream, nav, withDay = true) }
                    }
                    if (r.past.isNotEmpty()) {
                        item { SectionLabel("گذشته") }
                        items(r.past, key = { "p${it.item.key}" }) { EntryRow(it, today, stream, nav, withDay = true) }
                    }
                }
            }
            return@Column
        }

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CalView.entries.forEach { v -> Chip(v.label, selected = ui.view == v) { vm.setView(v) } }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton({ vm.go(-1) }) { Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "قبلی") }
            Text(
                CalRange.title(ui.view, ui.anchor), style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
            )
            IconButton({ vm.go(1) }) { Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "بعدی") }
            TextButton({ vm.goToday() }) { Text("امروز") }
        }
        ui.tag?.let { t ->
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("#$t ✕", selected = true) { vm.setTag(null) }
                Text("فقط این برچسب", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
            }
        }

        val entryByKey = remember(ui.entries) { ui.entries.associateBy { it.item.key } }
        val byDay = remember(ui.entries) { CalendarLogic.byDay(ui.entries.map { it.item }) }
        fun rows(day: LocalDate): List<CalEntry> = byDay[day].orEmpty().mapNotNull { entryByKey[it.key] }

        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            if (ui.truncated) {
                item("trunc") {
                    Text("در این بازه بیش از ۴۰۰ کار هست؛ فقط ۴۰۰ کار نخست نشان داده می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.amber)
                }
            }
            when (ui.view) {
                CalView.MONTH -> {
                    item("grid") {
                        val dots = remember(ui.entries, today) { CalendarLogic.dots(ui.entries.map { it.item }, today) }
                        MonthGrid(ui.anchor, today, dots) { vm.select(it) }
                    }
                    item("sel-head") { SectionLabel(Jalali.formatLong(ui.anchor) + " · " + CalendarLogic.summary(byDay[ui.anchor].orEmpty())) }
                    groupedDay(rows(ui.anchor), today, stream, nav, "m")
                }
                CalView.WEEK, CalView.THREE -> {
                    overdueSection(ui.overdue, today, stream, nav)
                    CalRange.days(ui.from, ui.to).forEach { d -> dayCard(d, rows(d), today, stream, nav) }
                }
                CalView.DAY -> {
                    item("strip") { WeekStrip(ui.anchor, today, strip) { vm.select(it) } }
                    item("sum") { SectionLabel(CalendarLogic.summary(byDay[ui.anchor].orEmpty())) }
                    groupedDay(rows(ui.anchor), today, stream, nav, "d")
                    overdueSection(ui.overdue, today, stream, nav)
                }
            }
        }
    }
}

private class Nav(val customer: (Long) -> Unit, val case: (Long) -> Unit, val tag: (String) -> Unit)

// ───────────────────────── ماه ─────────────────────────

@Composable
private fun MonthGrid(anchor: LocalDate, today: LocalDate, dots: Map<LocalDate, Dot>, onSelect: (LocalDate) -> Unit) {
    val weeks = remember(anchor) { CalRange.monthWeeks(anchor) }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            WEEKDAY_LETTERS.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.board.muted) }
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                week.forEach { d ->
                    val inMonth = CalRange.sameJalaliMonth(d, anchor)
                    val selected = d == anchor
                    val shape = RoundedCornerShape(10.dp)
                    Column(
                        Modifier.weight(1f).aspectRatio(1f).clip(shape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .then(if (d == today && !selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, shape) else Modifier)
                            .clickable { onSelect(d) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            Digits.fa(Jalali.toJalali(d).day), style = MaterialTheme.typography.labelLarge,
                            color = when {
                                selected -> MaterialTheme.colorScheme.onPrimary
                                inMonth -> MaterialTheme.colorScheme.onSurface
                                else -> MaterialTheme.board.muted.copy(alpha = 0.5f)
                            },
                        )
                        DotMark(dots[d] ?: Dot.NONE, selected)
                    }
                }
            }
        }
    }
}

@Composable
private fun DotMark(dot: Dot, onSelected: Boolean) {
    val color = when {
        dot == Dot.NONE -> Color.Transparent
        onSelected -> MaterialTheme.colorScheme.onPrimary
        dot == Dot.ALERT -> MaterialTheme.board.amber
        dot == Dot.OPEN -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.board.muted.copy(alpha = 0.6f)
    }
    Box(Modifier.padding(top = 2.dp).size(5.dp).clip(CircleShape).background(color))
}

@Composable
private fun WeekStrip(anchor: LocalDate, today: LocalDate, dots: Map<LocalDate, Dot>, onSelect: (LocalDate) -> Unit) {
    val (from, to) = CalRange.of(CalView.WEEK, anchor)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        CalRange.days(from, to).forEach { d ->
            val selected = d == anchor
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp))
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.board.slate)
                    .then(if (d == today && !selected) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
                    .clickable { onSelect(d) }.padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val fg = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                Text(WEEKDAY_LETTERS[Jalali.weekColumn(d)], style = MaterialTheme.typography.labelSmall, color = fg.copy(alpha = 0.7f))
                Text(Digits.fa(Jalali.toJalali(d).day), style = MaterialTheme.typography.labelLarge, color = fg)
                DotMark(dots[d] ?: Dot.NONE, selected)
            }
        }
    }
}

// ───────────────────────── فهرست روز ─────────────────────────

/** کارهای یک روز به‌تفکیک مشتری. */
private fun LazyListScope.groupedDay(entries: List<CalEntry>, today: LocalDate, stream: StreamViewModel, nav: Nav, prefix: String) {
    if (entries.isEmpty()) {
        item("$prefix-empty") { EmptyNote("برای این روز کاری نیست.") }
        return
    }
    val byKey = entries.associateBy { it.item.key }
    val groups = CalendarLogic.groupByCustomer(entries.map { it.item })
    items(groups.size, key = { "$prefix-g${groups[it].customerId}" }) { i ->
        GroupCard(groups[i], byKey, today, stream, nav, defaultOpen = groups.size <= 2 || i == 0)
    }
}

@Composable
private fun GroupCard(group: CalGroup, byKey: Map<String, CalEntry>, today: LocalDate, stream: StreamViewModel, nav: Nav, defaultOpen: Boolean) {
    var open by rememberSaveable(group.customerId, group.items.size) { mutableStateOf(defaultOpen) }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.board.slate).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth().clickable { open = !open }.padding(4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Avatar(group.name, group.customerId ?: 0, 28.dp)
            Text(group.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).clickable(enabled = group.customerId != null) { nav.customer(group.customerId!!) })
            Chip(Digits.fa(group.items.size), selected = group.openCount > 0)
            Text(if (open) "▾" else "▸", color = MaterialTheme.board.muted)
        }
        if (open) group.items.forEach { item -> byKey[item.key]?.let { EntryRow(it, today, stream, nav, withCustomer = false) } }
    }
}

/** هفته و ۳ روز: هر روز یک کارت؛ روز بدون کار یک خط کم‌رنگ. */
private fun LazyListScope.dayCard(day: LocalDate, entries: List<CalEntry>, today: LocalDate, stream: StreamViewModel, nav: Nav) {
    val title = Jalali.formatLong(day).substringBeforeLast(' ') + if (day == today) " · امروز" else ""
    if (entries.isEmpty()) {
        item("e${day}") { Text("$title · کاری نیست", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)) }
        return
    }
    item("h${day}") {
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(CalendarLogic.summary(entries.map { it.item }), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
        }
    }
    items(entries, key = { "${day}${it.item.key}" }) { EntryRow(it, today, stream, nav) }
}

/** «مانده از قبل» (فقط وقتی بازه امروز را دارد): جمع‌شده. */
private fun LazyListScope.overdueSection(entries: List<CalEntry>, today: LocalDate, stream: StreamViewModel, nav: Nav) {
    if (entries.isEmpty()) return
    item("overdue") {
        var open by rememberSaveable { mutableStateOf(false) }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton({ open = !open }) { Text((if (open) "▾" else "▸") + Digits.fa(" مانده از قبل (${entries.size})")) }
            if (open) entries.forEach { EntryRow(it, today, stream, nav, withDay = true) }
        }
    }
}

// ───────────────────────── ردیف ─────────────────────────

/** یک ردیف: کار (حلقه، منو) یا مهلت پرونده. */
@Composable
private fun EntryRow(
    entry: CalEntry,
    today: LocalDate,
    stream: StreamViewModel,
    nav: Nav,
    withCustomer: Boolean = true,
    withDay: Boolean = false,
) {
    val item = entry.item
    val task = entry.task
    if (task == null) {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface)
                .clickable { item.caseId?.let(nav.case) }.padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(item.title, style = MaterialTheme.typography.bodyMedium)
            Text(
                listOfNotNull(item.customerName, if (withDay) app.pocketos.board.domain.DayLabel.of(item.day, today) else null).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (!item.day.isAfter(today)) MaterialTheme.board.amber else MaterialTheme.board.muted,
            )
        }
        return
    }
    TaskRow(task, stream, onTag = nav.tag, done = item.done, showDue = withDay) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val link = MaterialTheme.colorScheme.primary
            if (withCustomer && item.customerName != null) {
                Text(
                    item.customerName, style = MaterialTheme.typography.bodySmall, color = link,
                    modifier = Modifier.clickable { item.customerId?.let(nav.customer) },
                )
            }
            if (withCustomer && item.customerName != null && item.caseTitle != null) {
                Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
            }
            if (item.caseTitle != null) {
                Text(
                    item.caseTitle, style = MaterialTheme.typography.bodySmall, color = link,
                    modifier = Modifier.clickable { item.caseId?.let(nav.case) },
                )
            }
        }
    }
}

internal fun stripTags(s: String) = Tags.strip(s)
