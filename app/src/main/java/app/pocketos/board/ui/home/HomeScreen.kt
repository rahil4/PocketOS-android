package app.pocketos.board.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketos.board.domain.Digits
import app.pocketos.board.ui.stream.CaptureBar
import app.pocketos.board.ui.stream.StreamOverlays
import app.pocketos.board.ui.stream.StreamScope
import app.pocketos.board.ui.stream.StreamViewModel
import app.pocketos.board.ui.stream.rememberOverlayState
import app.pocketos.board.ui.theme.board

@Composable
fun HomeScreen(
    onOpenCustomer: (Long) -> Unit,
    onOpenCase: (Long) -> Unit,
    onOpenTag: (String) -> Unit,
    onOpenAttention: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenTomorrow: (java.time.LocalDate) -> Unit,
    onOpenInbox: () -> Unit,
    onOpenEvening: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenSettings: () -> Unit,
    vm: HomeViewModel = hiltViewModel(),
    stream: StreamViewModel = hiltViewModel(),
    tree: TreeViewModel = hiltViewModel(),
) {
    val st by vm.state.collectAsStateWithLifecycle()
    val overlays = rememberOverlayState()
    LaunchedEffect(Unit) { stream.bind(StreamScope.Active) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh(); tree.refresh() }
    StreamOverlays(stream, overlays)
    // «＋ یادداشت» ویجت: پنجرهٔ یادداشت باز شود.
    val quick by vm.quickNote.collectAsStateWithLifecycle()
    LaunchedEffect(quick) {
        if (quick) {
            overlays.openNote()
            vm.consumeQuickNote()
        }
    }
    val nav = HomeNav(onOpenCustomer, onOpenCase, onOpenTag)

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(top = 14.dp, bottom = 16.dp),
        ) {
            item("hrow") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(Modifier.weight(1f)) {
                        Text(st.greeting, fontSize = 23.sp, fontWeight = FontWeight.Medium, lineHeight = 34.sp)
                        Text(st.todayLong, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted, modifier = Modifier.clickable(onClick = onOpenSettings))
                    }
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.board.slate).border(1.dp, MaterialTheme.board.line, CircleShape).clickable(onClick = onOpenChat),
                        contentAlignment = Alignment.Center,
                    ) { Text("💬", fontSize = 20.sp) }
                    Box(Modifier.size(6.dp)) {}
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.board.slate).border(1.dp, MaterialTheme.board.line, CircleShape).clickable(onClick = onOpenSearch),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Search, contentDescription = "جست‌وجو", tint = MaterialTheme.colorScheme.onSurface) }
                }
            }
            item("growth") { GrowthCard(tree) }
            item("cal") {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("تقویم و کارهای روز ←", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.board.muted, modifier = Modifier.clickable(onClick = onOpenCalendar))
                }
            }
            if (st.inboxN > 0) {
                item("inbox") { LinkCard(Digits.fa("${st.inboxN} مورد در صندوق"), surface = true, onClick = onOpenInbox) }
            }
            if (st.evening) {
                item("evening") {
                    LinkCard(null, surface = false, onClick = onOpenEvening) {
                        Row {
                            Text("روز را ", style = MaterialTheme.typography.bodyMedium)
                            Text("ببند", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium, style = MaterialTheme.typography.bodyMedium)
                            Text(" و سبک شو", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        CaptureBar(
            stream, onPickTarget = { overlays.picker = true },
            onNote = { overlays.openNote() },
        )
    }
}

@Composable
internal fun MoreLink(text: String, onClick: () -> Unit) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.board.muted, modifier = Modifier.clickable(onClick = onClick).padding(vertical = 6.dp, horizontal = 2.dp))
}

@Composable
private fun LinkCard(text: String?, surface: Boolean, onClick: () -> Unit, content: @Composable (() -> Unit)? = null) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(if (surface) MaterialTheme.colorScheme.surface else MaterialTheme.board.slate)
            .border(1.dp, MaterialTheme.board.line, shape).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { if (content != null) content() else Text(text.orEmpty(), style = MaterialTheme.typography.bodyMedium) }
        Text("←", color = MaterialTheme.board.muted)
    }
}
