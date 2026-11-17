package app.pocketos.board.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pocketos.board.data.repo.HomeRow
import app.pocketos.board.domain.Digits
import app.pocketos.board.ui.common.Avatar
import app.pocketos.board.ui.common.CustomerCard
import app.pocketos.board.ui.common.Pill
import app.pocketos.board.ui.stream.StreamViewModel
import app.pocketos.board.ui.stream.TaskRow
import app.pocketos.board.ui.theme.board

class HomeNav(
    val customer: (Long) -> Unit,
    val case: (Long) -> Unit,
    val tag: (String) -> Unit,
)

/** گروه متقاضی (مثل وب‌اپ): نوار آبی‌خاکستری و کارهای تو رفته با خط ارتباط. */
@Composable
fun CustomerGroupBlock(
    group: HomeGroupUi,
    defaultOpen: Boolean,
    stream: StreamViewModel,
    nav: HomeNav,
    onCaseDone: (HomeRow) -> Unit,
    onCaseKeep: (HomeRow) -> Unit,
) {
    var open by rememberSaveable(group.customerId, group.rows.size) { mutableStateOf(defaultOpen) }
    val name = group.name ?: "بدون متقاضی"
    CustomerCard {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.clickable(enabled = group.customerId != null) { nav.customer(group.customerId!!) }) { Avatar(name, group.customerId ?: 0, 30.dp) }
            Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.weight(1f))
            Text(
                Digits.fa(group.rows.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.board.muted,
                modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(MaterialTheme.board.cu.copy(alpha = 0.14f)).padding(horizontal = 9.dp, vertical = 1.dp),
            )
            Text(if (open) "▾" else "▸", color = MaterialTheme.board.muted)
        }
        if (open) {
            Row(Modifier.padding(start = 26.dp, end = 12.dp, bottom = 4.dp).height(IntrinsicSize.Min)) {
                Box(Modifier.width(2.dp).fillMaxHeight().background(MaterialTheme.board.cuLine))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    group.rows.forEach { row ->
                        val task = row.task
                        if (task != null) {
                            TaskRow(task, stream, onTag = nav.tag, showDue = false) {
                                Text(
                                    row.entry.sub, style = MaterialTheme.typography.bodySmall,
                                    color = if (row.entry.amber) MaterialTheme.board.amber else MaterialTheme.board.muted,
                                )
                            }
                        } else {
                            AttentionRow(row, nav, onCaseDone, onCaseKeep)
                        }
                    }
                }
            }
        }
    }
}

/** پروندهٔ نیازمند توجه: «✓ تمام شد» و (اگر دلیل مهلت نیست) «دیدم، بماند». */
@Composable
private fun AttentionRow(row: HomeRow, nav: HomeNav, onDone: (HomeRow) -> Unit, onKeep: (HomeRow) -> Unit) {
    val e = row.entry
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(44.dp).clickable { onDone(row) }, contentAlignment = Alignment.Center) {
                Box(Modifier.size(24.dp).clip(CircleShape).border(2.dp, if (e.amber) MaterialTheme.board.amber else MaterialTheme.board.muted, CircleShape))
            }
            Column(Modifier.weight(1f).padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    e.title, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.clickable { e.caseId?.let(nav.case) },
                )
                Text(e.sub, style = MaterialTheme.typography.bodySmall, color = if (e.amber) MaterialTheme.board.amber else MaterialTheme.board.muted)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("✓ تمام شد", primary = true) { onDone(row) }
                    if (e.keepOk) Pill("دیدم، بماند") { onKeep(row) }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.board.line)
    }
}
