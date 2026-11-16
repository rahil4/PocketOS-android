package app.pocketos.board.ui.trash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketos.board.data.repo.TrashRepository
import app.pocketos.board.domain.Digits
import app.pocketos.board.ui.common.EmptyNote
import app.pocketos.board.ui.common.Pill
import app.pocketos.board.ui.common.SectionLabel
import app.pocketos.board.ui.theme.board

@Composable
fun TrashScreen(onBack: () -> Unit, vm: TrashViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val now = vm.clock.millis()
    val deletedIds = st.customers.map { it.id }.toSet()
    // پرونده‌هایی که همراه مشتری حذف شده‌اند جدا نمایش داده نمی‌شوند.
    val cases = st.cases.filter { it.customerId !in deletedIds }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 12.dp, bottom = 24.dp),
    ) {
        item { TextButton(onBack) { Text("‹ بازگشت") } }
        item { Text("سطل بازیافت", style = MaterialTheme.typography.titleLarge) }
        item { Text("هر چیز ۳۰ روز اینجا می‌ماند و بعد پاک می‌شود.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted) }
        if (st.loaded && st.customers.isEmpty() && cases.isEmpty()) item { EmptyNote("سطل خالی است.") }
        if (st.customers.isNotEmpty()) item { SectionLabel("مشتری‌ها") }
        items(st.customers, key = { "c${it.id}" }) { c ->
            TrashRow(c.name, TrashRepository.daysLeft(c.deletedAt ?: now, now)) { vm.restoreCustomer(c.id) }
        }
        if (cases.isNotEmpty()) item { SectionLabel("پرونده‌ها") }
        items(cases, key = { "k${it.id}" }) { k ->
            TrashRow(k.title, TrashRepository.daysLeft(k.deletedAt ?: now, now)) { vm.restoreCase(k.id) }
        }
    }
}

@Composable
private fun TrashRow(title: String, daysLeft: Long, onRestore: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(Digits.fa("$daysLeft روز دیگر پاک می‌شود"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
        }
        Pill("بازگردانی", onClick = onRestore)
    }
}
