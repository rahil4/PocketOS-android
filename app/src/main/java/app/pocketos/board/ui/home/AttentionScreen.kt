package app.pocketos.board.ui.home

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketos.board.domain.Digits
import app.pocketos.board.ui.common.Pill
import app.pocketos.board.ui.common.SurfaceCard
import app.pocketos.board.ui.stream.StreamViewModel
import app.pocketos.board.ui.theme.board
import androidx.compose.ui.Alignment as Al

/** «امروز» کامل (از «N مورد دیگر ←»): همهٔ مانده‌ها و پرونده‌های نیازمند توجه. */
@Composable
fun AttentionScreen(
    onBack: () -> Unit,
    onOpenCustomer: (Long) -> Unit,
    onOpenCase: (Long) -> Unit,
    onOpenTag: (String) -> Unit,
    vm: AttentionViewModel = hiltViewModel(),
    stream: StreamViewModel = hiltViewModel(),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    var confirmDrop by rememberSaveable { mutableStateOf(false) }
    val nav = HomeNav(onOpenCustomer, onOpenCase, onOpenTag)

    if (confirmDrop) {
        AlertDialog(
            onDismissRequest = { confirmDrop = false },
            title = { Text(Digits.fa("${st.oldN} کار قدیمی رها شود؟")) },
            text = { Text("در سطل بازیافت می‌ماند و تا ۳۰ روز برمی‌گردد.") },
            confirmButton = { TextButton({ confirmDrop = false; vm.bulk(drop = true) }) { Text("رها کن") } },
            dismissButton = { TextButton({ confirmDrop = false }) { Text("بیخیال") } },
        )
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 24.dp),
    ) {
        item("top") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Al.CenterVertically) {
                Text("امروز", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Pill("←", onClick = onBack)
            }
        }
        if (st.oldN > 0) {
            item("old") {
                SurfaceCard {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(Digits.fa("${st.oldN} کار از ۲ روز پیش یا قدیمی‌تر مانده. می‌توانید یک‌جا تصمیم بگیرید:"), style = MaterialTheme.typography.bodyMedium)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Pill("همه را به فردا ببر", primary = true) { vm.bulk(drop = false) }
                            Pill("رها کن") { confirmDrop = true }
                        }
                    }
                }
            }
        }
        if (st.loaded && st.groups.isEmpty()) {
            item("calm") {
                Column(Modifier.fillMaxWidth().padding(vertical = 36.dp), horizontalAlignment = Al.CenterHorizontally) {
                    Text("✓", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                    Text("همه‌چیز آرام است", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.board.muted)
                }
            }
        }
        items(st.groups.size, key = { "g${st.groups[it].customerId}" }) { i ->
            CustomerGroupBlock(
                st.groups[i], defaultOpen = i == 0 || st.groups.size <= 2, stream = stream, nav = nav,
                onCaseDone = { vm.caseDone(it.entry.title, it.entry.caseId ?: return@CustomerGroupBlock) },
                onCaseKeep = { vm.caseKeep(it.entry.caseId ?: return@CustomerGroupBlock) },
            )
        }
        if (st.hiddenOld > 0) {
            item("hidden") {
                Text(
                    Digits.fa("${st.hiddenOld} مانده‌ی قدیمی‌تر هم هست؛ با دکمه‌های بالا یک‌جا تصمیم بگیرید."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted,
                )
            }
        }
    }
}
