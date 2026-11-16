package app.pocketos.board.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketos.board.domain.DayLabel
import app.pocketos.board.domain.Digits
import app.pocketos.board.domain.Phone
import app.pocketos.board.domain.RelativeTime
import app.pocketos.board.domain.Tags
import app.pocketos.board.ui.common.Avatar
import app.pocketos.board.ui.common.EmptyNote
import app.pocketos.board.ui.common.Pill
import app.pocketos.board.ui.common.SectionLabel
import app.pocketos.board.ui.common.SurfaceCard
import app.pocketos.board.ui.theme.board
import java.time.LocalDate

/** جست‌وجوی سراسری: مشتری، پرونده، کار و متن یادداشت‌ها. `#برچسب` صفحهٔ برچسب را باز می‌کند. */
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenCustomer: (Long) -> Unit,
    onOpenCase: (Long) -> Unit,
    onOpenTag: (String) -> Unit,
    vm: SearchViewModel = hiltViewModel(),
) {
    val q by vm.query.collectAsStateWithLifecycle()
    val r by vm.results.collectAsStateWithLifecycle()
    val today = LocalDate.now()
    val tag = Tags.tagsIn(q).firstOrNull()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
    ) {
        item("top") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("جست‌وجو", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Pill("←", onClick = onBack)
            }
        }
        item("input") {
            OutlinedTextField(
                q, { vm.query.value = it }, singleLine = true, placeholder = { Text("نام، پرونده، کار، یادداشت یا #برچسب", color = MaterialTheme.board.muted) },
                shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth(),
            )
        }
        if (tag != null) {
            item("tag") { Pill("نمایش برچسب #$tag ←", primary = true) { onOpenTag(tag) } }
        }
        val res = r
        if (res == null) {
            item("hint") { EmptyNote("می‌نویسم و در همه‌جا می‌گردم.") }
            return@LazyColumn
        }
        item("count") { Text(Digits.fa("${res.count} نتیجه"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted) }
        if (res.count == 0) item("none") { EmptyNote("چیزی پیدا نشد") }

        if (res.customers.isNotEmpty()) {
            item("hc") { SectionLabel("مشتری‌ها") }
            items(res.customers, key = { "c${it.id}" }) { c ->
                ResultRow(onClick = { onOpenCustomer(c.id) }, lead = { Avatar(c.name, c.id, 34.dp) }) {
                    Text(c.name, style = MaterialTheme.typography.titleMedium)
                    if (c.phone.isNotBlank()) Text(Phone.display(c.phone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
                }
            }
        }
        if (res.cases.isNotEmpty()) {
            item("hk") { SectionLabel("پرونده‌ها") }
            items(res.cases, key = { "k${it.id}" }) { k ->
                ResultRow(onClick = { onOpenCase(k.id) }) {
                    Text(k.title, style = MaterialTheme.typography.titleMedium)
                    Text(k.customerName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
                }
            }
        }
        val tasks = res.tasks.upcoming + res.tasks.past
        if (tasks.isNotEmpty()) {
            item("ht") { SectionLabel("کارها") }
            items(tasks, key = { "t${it.item.key}" }) { e ->
                ResultRow(onClick = { (e.item.caseId?.let(onOpenCase) ?: e.item.customerId?.let(onOpenCustomer)) }) {
                    Text(Digits.fa(Tags.strip(e.item.title).ifEmpty { e.item.title }), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        listOfNotNull(DayLabel.of(e.item.day, today), e.item.customerName).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (!e.item.done && !e.item.day.isAfter(today)) MaterialTheme.board.amber else MaterialTheme.board.muted,
                    )
                }
            }
        }
        if (res.notes.isNotEmpty()) {
            item("hn") { SectionLabel("یادداشت‌ها") }
            items(res.notes, key = { "n${it.item.id}" }) { n ->
                ResultRow(onClick = { (n.item.caseId?.let(onOpenCase) ?: n.item.customerId?.let(onOpenCustomer)) }) {
                    Text(Digits.fa(Tags.strip(n.item.text).ifEmpty { n.item.text.ifEmpty { n.item.fileName.orEmpty() } }), style = MaterialTheme.typography.bodyLarge, maxLines = 3)
                    Text(
                        listOfNotNull(RelativeTime.ago(n.item.createdAt, System.currentTimeMillis()), n.customerName).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted,
                    )
                }
            }
        }
    }
}

@Composable
private fun ResultRow(onClick: () -> Unit, lead: (@Composable () -> Unit)? = null, content: @Composable () -> Unit) {
    SurfaceCard(onClick = onClick) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            lead?.invoke()
            Column(Modifier.weight(1f)) { content() }
        }
    }
}
