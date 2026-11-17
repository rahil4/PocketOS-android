package app.pocketos.board.ui.inbox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketos.board.data.repo.ItemKind
import app.pocketos.board.data.repo.Target
import app.pocketos.board.domain.Names
import app.pocketos.board.ui.common.EmptyNote
import app.pocketos.board.ui.stream.CaptureBar
import app.pocketos.board.ui.stream.ItemCard
import app.pocketos.board.ui.stream.StreamOverlays
import app.pocketos.board.ui.stream.StreamScope
import app.pocketos.board.ui.stream.StreamViewModel
import app.pocketos.board.ui.stream.rememberOverlayState
import app.pocketos.board.ui.theme.board

/** صندوق ورودی: هر چیزی که بدون متقاضی ثبت شده؛ با «انتقال…» به متقاضی یا پرونده می‌رود. */
@Composable
fun InboxScreen(onBack: () -> Unit, onOpenTag: (String) -> Unit, vm: StreamViewModel = hiltViewModel()) {
    val st by vm.state.collectAsStateWithLifecycle()
    val customers by vm.activeCustomers.collectAsStateWithLifecycle()
    val overlays = rememberOverlayState()
    LaunchedEffect(Unit) { vm.bind(StreamScope.Inbox) }
    StreamOverlays(vm, overlays)
    val now = System.currentTimeMillis()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 16.dp),
        ) {
            item { TextButton(onBack) { Text("‹ بازگشت") } }
            item { Text("صندوق ورودی", style = MaterialTheme.typography.titleLarge) }
            item { Text("هر چیزی را خام ثبت کن؛ بعداً با «⋯ ← انتقال» جایش را مشخص کن.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted) }
            if (st.loaded && st.items.isEmpty()) item { EmptyNote("صندوق ورودی خالی است.") }
            items(st.items, key = { it.id }) { item ->
                ItemCard(item, vm, now, onOpenTag) { overlays.viewer = item }
                // فقط پیشنهاد: اگر نام یک متقاضی در متن بود، با یک لمس منتقل می‌شود.
                if (item.kind == ItemKind.TEXT) {
                    val byId = customers.associateBy { it.id }
                    val hits = Names.matching(item.text, customers.map { it.id to it.name }).mapNotNull { byId[it] }.take(2)
                    if (hits.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        hits.forEach { c -> TextButton({ vm.moveItem(item, Target(c.id, null)) }) { Text("← ${c.name}؟") } }
                    }
                }
            }
        }
        CaptureBar(vm, onPickTarget = { overlays.picker = true }, onNote = { overlays.openNote() })
    }
}
