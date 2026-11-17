package app.pocketos.board.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketos.board.data.repo.CalEntry
import app.pocketos.board.domain.CalRange
import app.pocketos.board.domain.CalView
import app.pocketos.board.domain.CalendarLogic
import app.pocketos.board.domain.DayMarks
import app.pocketos.board.domain.Digits
import app.pocketos.board.domain.Dot
import app.pocketos.board.domain.Jalali
import app.pocketos.board.ui.common.Chip
import app.pocketos.board.ui.common.EmptyNote
import app.pocketos.board.ui.common.SectionLabel
import app.pocketos.board.ui.stream.StreamViewModel
import app.pocketos.board.ui.stream.TaskRow
import app.pocketos.board.ui.theme.board
import java.time.LocalDate

private val WEEKDAY_LETTERS = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

private class Nav(val customer: (Long) -> Unit, val case: (Long) -> Unit, val tag: (String) -> Unit)

@Composable
fun CalendarScreen(
    onOpenCustomer: (Long) -> Unit,
    onOpenCase: (Long) -> Unit,
    vm: CalendarViewModel = hiltViewModel(),
    stream: StreamViewModel = hiltViewModel(),
) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val result by vm.search.collectAsStateWithLifecycle()
    val strip by vm.strip.collectAsStateWithLifecycle()
    val today = vm.today
    val nav = Nav(onOpenCustomer, onOpenCase) { vm.setTag(it) }

    Column(Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp)) {
        // انتخاب‌گر نما
        val segShape = RoundedCornerShape(14.dp)
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp).clip(segShape).background(MaterialTheme.colorScheme.surface)
                .border(1.dp, MaterialTheme.board.line, segShape).padding(3.dp),
        ) {
            CalView.entries.forEach { v ->
                val on = ui.view == v
                Text(
                    v.label,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(11.dp))
                        .background(if (on) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { vm.setView(v) }.padding(vertical = 8.dp),
                    textAlign = TextAlign.Center,
                    fontSize = 16.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) MaterialTheme.board.accentInk else MaterialTheme.board.muted,
                )
            }
        }
        // جست‌وجو همیشه بالاست
        OutlinedTextField(
            query, { vm.query.value = it }, singleLine = true,
            placeholder = { Text("جست‌وجو در کارها…", color = MaterialTheme.board.muted) },
            trailingIcon = if (query.isNotEmpty()) ({ Text("✕", color = MaterialTheme.board.muted, modifier = Modifier.clickable { vm.query.value = "" }.padding(12.dp)) }) else null,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )

        if (query.isNotBlank()) {
            SearchResults(result, today, stream, nav) { vm.openDay(it) }
            return@Column
        }

        val entryByKey = remember(ui.entries) { ui.entries.associateBy { it.item.key } }
        val byDay = remember(ui.entries) { CalendarLogic.byDay(ui.entries.map { it.item }) }
        fun rows(day: LocalDate): List<CalEntry> = byDay[day].orEmpty().mapNotNull { entryByKey[it.key] }

        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
        ) {
            ui.tag?.let { t -> item("tagbar") { TagBar(t) { vm.setTag(null) } } }

            // عنوان با فلش‌ها
            item("mh") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton({ vm.go(-1) }) { Icon(Icons.Filled.KeyboardArrowRight, contentDescription = "قبلی", modifier = Modifier.size(30.dp), tint = MaterialTheme.board.muted) }
                    Text(
                        CalRange.title(ui.view, ui.anchor), fontSize = 21.sp, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f).clickable { vm.goToday() },
                    )
                    IconButton({ vm.go(1) }) { Icon(Icons.Filled.KeyboardArrowLeft, contentDescription = "بعدی", modifier = Modifier.size(30.dp), tint = MaterialTheme.board.muted) }
                }
            }
            if (ui.view != CalView.MONTH && today !in ui.from..ui.to) {
                item("today") { Row(Modifier.fillMaxWidth()) { Chip("امروز") { vm.goToday() } } }
            }

            if (ui.truncated) {
                item("trunc") {
                    Text("فقط ۴۰۰ کار اول این بازه نشان داده شد؛ برای دیدن بقیه، نمای «روز» یا یک برچسب را انتخاب کنید.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
                }
            }

            when (ui.view) {
                CalView.MONTH -> monthSection(ui.anchor, today, ui.entries, byDay, ::rows, stream, nav) { vm.select(it) }
                else -> {
                    if (ui.view == CalView.DAY) item("strip") { WeekStrip(ui.anchor, today, strip) { vm.select(it) } }
                    if (ui.overdue.isNotEmpty()) item("late") { OverdueCard(ui.overdue, today, stream, nav) }
                    CalRange.days(ui.from, ui.to).forEach { d -> daySection(d, ui.view, today, rows(d), stream, nav) { vm.setView(CalView.MONTH); vm.select(d) } }
                }
            }
        }
    }
}

// ───────────────────────── اجزا ─────────────────────────

@Composable
private fun TagBar(tag: String, onClear: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.board.accentSoft)
            .border(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f), shape).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("فیلتر:", style = MaterialTheme.typography.bodyMedium)
            Text("#$tag", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
        }
        Text("✕ برداشتن", color = MaterialTheme.board.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.clickable(onClick = onClear))
    }
}

/** کارت روز (`.dy`): عنوان، شمارش، ردیف‌ها؛ امروز = دورخط سبز، مانده = کهربایی. */
@Composable
private fun DayCard(
    title: String,
    count: String,
    today: Boolean = false,
    late: Boolean = false,
    badge: String? = null,
    dim: Boolean = false,
    onTitle: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    val border = when {
        late -> MaterialTheme.board.amber.copy(alpha = 0.45f)
        today -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.board.line
    }
    Column(
        Modifier.fillMaxWidth().alpha(if (dim) 0.85f else 1f).clip(shape)
            .background(if (late) MaterialTheme.board.amberSoft else MaterialTheme.colorScheme.surface)
            .border(1.dp, border, shape).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    title, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    color = if (late) MaterialTheme.board.amber else MaterialTheme.colorScheme.onSurface,
                    modifier = if (onTitle != null) Modifier.clickable(onClick = onTitle) else Modifier,
                )
                if (badge != null) {
                    Text(
                        badge, color = MaterialTheme.board.accentInk, fontSize = 11.sp, fontWeight = FontWeight.Medium,
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 7.dp),
                    )
                }
            }
            Text(count, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
        }
        content()
    }
}

private fun countText(entries: List<CalEntry>): String {
    val tasks = entries.filter { it.task != null }
    val open = tasks.count { !it.item.done }
    val done = tasks.size - open
    val deadlines = entries.count { it.task == null }
    val parts = buildList {
        if (open > 0) add(Digits.fa("$open کار") ) else if (done > 0) add("✓ انجام شد")
        if (deadlines > 0) add(Digits.fa("$deadlines مهلت"))
    }
    return parts.joinToString(" · ")
}

/** یک روز در نمای هفته/۳ روز/روز. */
private fun LazyListScope.daySection(
    day: LocalDate, view: CalView, today: LocalDate, entries: List<CalEntry>,
    stream: StreamViewModel, nav: Nav, onTitle: () -> Unit,
) {
    val isToday = day == today
    val base = Jalali.formatLong(day).substringBeforeLast(' ')
    val title = if (view == CalView.DAY && isToday) "امروز · $base" else base
    item("d$day") {
        DayCard(
            title = title, count = countText(entries), today = isToday,
            badge = if (isToday && view == CalView.WEEK) "امروز" else null,
            dim = Jalali.weekColumn(day) == 6 && view != CalView.DAY, onTitle = onTitle,
        ) {
            if (entries.isEmpty()) {
                Text("کاری نیست", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
            } else entries.forEach { EntryRow(it, today, stream, nav) }
        }
    }
}

/** «مانده از قبل»: کارت کهربایی جمع‌شده. */
@Composable
private fun OverdueCard(entries: List<CalEntry>, today: LocalDate, stream: StreamViewModel, nav: Nav) {
    var open by rememberSaveable { mutableStateOf(false) }
    DayCard(title = "مانده از قبل", count = Digits.fa("${entries.size} کار"), late = true, onTitle = { open = !open }) {
        if (open) entries.forEach { EntryRow(it, today, stream, nav, withDay = true) }
    }
}

// ───────────────────────── ماه ─────────────────────────

private fun LazyListScope.monthSection(
    anchor: LocalDate, today: LocalDate, entries: List<CalEntry>, byDay: Map<LocalDate, List<app.pocketos.board.domain.CalItem>>,
    rows: (LocalDate) -> List<CalEntry>, stream: StreamViewModel, nav: Nav, onSelect: (LocalDate) -> Unit,
) {
    val tasksInMonth = entries.filter { it.task != null && CalRange.sameJalaliMonth(it.item.day, anchor) }
    val left = tasksInMonth.count { !it.item.done }
    item("sum") {
        Text(
            when {
                tasksInMonth.isEmpty() -> "در این ماه کاری ثبت نشده"
                left > 0 -> Digits.fa("${tasksInMonth.size} کار در این ماه · $left مانده")
                else -> Digits.fa("${tasksInMonth.size} کار در این ماه") + " · همه انجام شده"
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted,
        )
    }
    item("grid") {
        val marks = remember(entries, today) { CalendarLogic.marks(entries.map { it.item }, today) }
        MonthGrid(anchor, today, marks, onSelect)
    }
    item("legend") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(MaterialTheme.colorScheme.primary, "کار")
            LegendDot(MaterialTheme.board.info, "مهلت پرونده")
            LegendDot(MaterialTheme.board.amber, "مانده")
        }
    }
    item("sel") {
        val dayRows = rows(anchor)
        DayCard(title = Jalali.formatLong(anchor), count = countText(dayRows), today = anchor == today) {
            if (dayRows.isEmpty()) {
                Text("برای این روز چیزی نیست.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted.copy(alpha = 0.7f), modifier = Modifier.padding(top = 4.dp, bottom = 2.dp))
            } else dayRows.forEach { EntryRow(it, today, stream, nav) }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
    }
}

@Composable
private fun MonthGrid(anchor: LocalDate, today: LocalDate, marks: Map<LocalDate, DayMarks>, onSelect: (LocalDate) -> Unit) {
    val weeks = remember(anchor) { CalRange.monthWeeks(anchor) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            WEEKDAY_LETTERS.forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted) }
        }
        weeks.forEach { week ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                week.forEach { d ->
                    val inMonth = CalRange.sameJalaliMonth(d, anchor)
                    val selected = d == anchor
                    val shape = RoundedCornerShape(12.dp)
                    val m = marks[d]
                    Column(
                        Modifier.weight(1f).height(52.dp).alpha(if (inMonth) 1f else 0.4f).clip(shape)
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                            .border(1.dp, if (d == today && !selected) MaterialTheme.colorScheme.primary else if (selected) Color.Transparent else MaterialTheme.board.line, shape)
                            .clickable { onSelect(d) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            Digits.fa(Jalali.toJalali(d).day), fontSize = 16.sp, fontWeight = FontWeight.Medium,
                            color = if (selected) MaterialTheme.board.accentInk else MaterialTheme.colorScheme.onSurface,
                        )
                        Row(Modifier.height(7.dp).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (m != null) {
                                val on = selected
                                if (m.task) Dot5(if (on) MaterialTheme.board.accentInk else MaterialTheme.colorScheme.primary)
                                if (m.deadline) Dot5(if (on) MaterialTheme.board.accentInk else MaterialTheme.board.info)
                                if (m.late) Dot5(if (on) MaterialTheme.board.accentInk else MaterialTheme.board.amber)
                                if (m.doneOnly) Dot5(if (on) MaterialTheme.board.accentInk.copy(alpha = 0.6f) else MaterialTheme.board.muted.copy(alpha = 0.6f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Dot5(color: Color) = Box(Modifier.size(5.dp).clip(CircleShape).background(color))

/** نوار هفتهٔ نمای «روز». */
@Composable
private fun WeekStrip(anchor: LocalDate, today: LocalDate, dots: Map<LocalDate, Dot>, onSelect: (LocalDate) -> Unit) {
    val (from, to) = CalRange.of(CalView.WEEK, anchor)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        CalRange.days(from, to).forEach { d ->
            val selected = d == anchor
            val shape = RoundedCornerShape(14.dp)
            Column(
                Modifier.weight(1f).clip(shape)
                    .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (selected) Color.Transparent else MaterialTheme.board.line, shape)
                    .clickable { onSelect(d) }.padding(top = 8.dp, bottom = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val letter = if (selected) MaterialTheme.board.accentInk else MaterialTheme.board.muted
                val num = when {
                    selected -> MaterialTheme.board.accentInk
                    d == today -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                }
                Text(WEEKDAY_LETTERS[Jalali.weekColumn(d)], fontSize = 14.sp, color = letter)
                Text(Digits.fa(Jalali.toJalali(d).day), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = num)
                Box(
                    Modifier.padding(top = 2.dp).size(5.dp).clip(CircleShape)
                        .background(if (dots[d] == null) Color.Transparent else if (selected) MaterialTheme.board.accentInk else MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

// ───────────────────────── جست‌وجو ─────────────────────────

@Composable
private fun SearchResults(
    result: app.pocketos.board.data.repo.SearchResult?,
    today: LocalDate,
    stream: StreamViewModel,
    nav: Nav,
    onOpenDay: (LocalDate) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp)) {
        val r = result
        if (r == null) {
            item { EmptyNote("می‌نویسم و می‌گردم…") }
            return@LazyColumn
        }
        val n = r.upcoming.size + r.past.size
        item("found") {
            Text(
                Digits.fa("$n نتیجه") + if (n > 0) " · لمس عنوان روز = رفتن به همان روز" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted,
            )
        }
        if (n == 0) item("none") { EmptyNote("چیزی پیدا نشد") }
        if (r.upcoming.isNotEmpty()) {
            item("up") { SectionLabel("پیش رو") }
            val byDay = r.upcoming.groupBy { it.item.day }.toSortedMap()
            byDay.forEach { (d, list) -> item("u$d") { ResultDay(d, list, today, stream, nav, onOpenDay) } }
        }
        if (r.past.isNotEmpty()) {
            item("pa") { SectionLabel("گذشته") }
            val byDay = r.past.groupBy { it.item.day }.toSortedMap(compareByDescending { it })
            byDay.forEach { (d, list) -> item("p$d") { ResultDay(d, list, today, stream, nav, onOpenDay) } }
        }
    }
}

@Composable
private fun ResultDay(d: LocalDate, list: List<CalEntry>, today: LocalDate, stream: StreamViewModel, nav: Nav, onOpenDay: (LocalDate) -> Unit) {
    DayCard(title = Jalali.formatLong(d), count = countText(list), today = d == today, onTitle = { onOpenDay(d) }) {
        list.forEach { EntryRow(it, today, stream, nav) }
    }
}

// ───────────────────────── ردیف ─────────────────────────

/** نام متقاضی و پرونده: کم‌رنگ با خط‌زیر نقطه‌چین (لینک)، مثل وب‌اپ. */
@Composable
private fun DottedLink(text: String, onClick: () -> Unit) {
    val color = MaterialTheme.board.muted
    Text(
        text, style = MaterialTheme.typography.bodySmall, color = color,
        modifier = Modifier.clickable(onClick = onClick).drawBehind {
            val y = size.height - 1.dp.toPx()
            drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())))
        },
    )
}

@Composable
private fun EntryRow(entry: CalEntry, today: LocalDate, stream: StreamViewModel, nav: Nav, withDay: Boolean = false) {
    val item = entry.item
    val task = entry.task
    if (task == null) {
        // مهلت پرونده: مربع خط‌چین به‌جای حلقه
        val accent = MaterialTheme.colorScheme.primary
        Column {
            Row(
                Modifier.fillMaxWidth().clickable { item.caseId?.let(nav.case) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(20.dp).drawBehind {
                        drawRoundRect(
                            accent, cornerRadius = CornerRadius(6.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))),
                        )
                    })
                }
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        listOfNotNull("پرونده", item.customerName, if (withDay) app.pocketos.board.domain.DayLabel.of(item.day, today) else null).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!item.day.isAfter(today)) MaterialTheme.board.amber else MaterialTheme.board.muted,
                    )
                }
            }
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.board.line)
        }
        return
    }
    val customerName = item.customerName
    val caseTitle = item.caseTitle
    TaskRow(task, stream, onTag = nav.tag, done = item.done, showDue = withDay) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (customerName != null) DottedLink(customerName) { item.customerId?.let(nav.customer) }
            if (customerName != null && caseTitle != null) Text("·", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
            if (caseTitle != null) DottedLink(caseTitle) { item.caseId?.let(nav.case) }
        }
    }
}
