package app.pocketos.board.ui.chat

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketos.board.domain.AssistantTexts
import app.pocketos.board.domain.Block
import app.pocketos.board.domain.Digits
import app.pocketos.board.domain.NavTarget
import app.pocketos.board.domain.Phone
import app.pocketos.board.ui.common.Chip
import app.pocketos.board.ui.common.Pill
import app.pocketos.board.ui.theme.board

/** «💬 دستیار»: پرسش و ثبت با جمله؛ بدون هوش مصنوعی و کاملاً روی داده‌ی همین گوشی. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatScreen(onBack: () -> Unit, onNavigate: (NavTarget) -> Unit, vm: ChatViewModel = hiltViewModel()) {
    val msgs by vm.messages.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(msgs.size) { if (msgs.isNotEmpty()) list.animateScrollToItem(msgs.size) }

    fun send() {
        if (input.isNotBlank()) {
            vm.say(input)
            input = ""
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("💬 دستیار", style = MaterialTheme.typography.titleLarge)
                Text("بپرسید یا با یک جمله ثبت کنید؛ بدون هوش مصنوعی، روی داده‌ی خودتان.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
            }
            Pill("←", onClick = onBack)
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp),
            state = list,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(msgs, key = { _, m -> m.id }) { _, m ->
                Bubble(m, vm, onSay = { vm.say(it) }, onNavigate = onNavigate)
            }
            item("sug") {
                FlowRow(Modifier.padding(top = 4.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistantTexts.SUGGESTIONS.forEach { s -> Chip(s) { vm.say(s) } }
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.board.line)
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                input, { if (it.length <= AssistantTexts.MAX_LENGTH) input = it }, singleLine = true,
                placeholder = { Text("بنویسید…", color = MaterialTheme.board.muted) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send() }),
                shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f),
            )
            Pill("ارسال", primary = true) { send() }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Bubble(m: Msg, vm: ChatViewModel, onSay: (String) -> Unit, onNavigate: (NavTarget) -> Unit) {
    val ctx = LocalContext.current
    val user = m.fromUser
    // کاربر سمت شروع (راست)، دستیار سمت دیگر؛ گوشهٔ تیز نزدیک به فرستنده.
    val shape = if (user) RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp)
    else RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (user) Alignment.Start else Alignment.End) {
        Column(
            Modifier.fillMaxWidth(if (user) 0.8f else 0.94f).clip(shape)
                .background(if (user) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                .then(if (user) Modifier else Modifier.border(1.dp, MaterialTheme.board.line, shape))
                .padding(horizontal = 13.dp, vertical = 9.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val fg = if (user) MaterialTheme.board.accentInk else MaterialTheme.colorScheme.onSurface
            m.text?.let { Text(Digits.fa(it), style = MaterialTheme.typography.bodyMedium, color = fg) }
            when (val b = m.block) {
                null -> Unit
                is Block.Text -> Text(Digits.fa(b.text), style = MaterialTheme.typography.bodyMedium)
                is Block.Help -> {
                    Text(b.text, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        b.examples.forEach { x -> Chip(x) { onSay(x) } }
                    }
                }
                is Block.Items -> {
                    Text(b.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    b.items.forEachIndexed { i, it ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.board.line)
                        Column(Modifier.fillMaxWidth().clickable { onNavigate(it.nav) }.padding(vertical = 6.dp)) {
                            Text(it.label, style = MaterialTheme.typography.bodyMedium)
                            if (it.sub.isNotEmpty()) Text(it.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
                        }
                    }
                }
                is Block.CustomerCard -> {
                    Text(b.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    if (b.sub.isNotEmpty()) Text(b.sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
                    if (b.phone.isNotBlank()) Text(Phone.display(b.phone), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("باز کردن") { onNavigate(NavTarget.Customer(b.customerId)) }
                        if (b.phone.isNotBlank()) Pill("📞 تماس") {
                            try {
                                ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${b.phone}")))
                            } catch (_: ActivityNotFoundException) {
                            }
                        }
                    }
                }
                is Block.Choices -> {
                    Text(b.text, style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        b.options.forEach { o -> Chip(o.label) { onSay(o.say) } }
                    }
                }
                is Block.Confirm -> {
                    Text(b.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    b.lines.forEach { Text(Digits.fa(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill("ثبت", primary = true) { vm.confirm(m.id, b.action) }
                        Pill("لغو") { vm.cancel(m.id) }
                    }
                }
                is Block.Done -> {
                    Text(b.text, style = MaterialTheme.typography.bodyMedium)
                    b.undo?.let { u -> Row { Pill("بازگردانی") { vm.undo(m.id, u) } } }
                }
                is Block.Link -> Text(
                    b.text, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.clickable { onNavigate(b.nav) },
                )
            }
        }
    }
}
