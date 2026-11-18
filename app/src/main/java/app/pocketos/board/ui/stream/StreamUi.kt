package app.pocketos.board.ui.stream

import android.content.ActivityNotFoundException
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.pocketos.board.data.FileStore
import app.pocketos.board.data.db.ItemEntity
import app.pocketos.board.data.db.TaskEntity
import app.pocketos.board.data.repo.ItemKind
import app.pocketos.board.data.repo.Target
import app.pocketos.board.domain.DayLabel
import app.pocketos.board.domain.Digits
import app.pocketos.board.domain.Stamp
import app.pocketos.board.domain.Tags
import app.pocketos.board.domain.When
import app.pocketos.board.ui.common.Chip
import app.pocketos.board.ui.common.EmptyNote
import app.pocketos.board.ui.common.Pill
import app.pocketos.board.ui.common.SectionLabel
import app.pocketos.board.ui.common.SurfaceCard
import app.pocketos.board.ui.common.TagChip
import app.pocketos.board.ui.theme.board
import coil.compose.AsyncImage
import java.time.LocalDate
import java.time.ZoneId

private val KIND_LABELS = listOf(
    ItemKind.PHOTO to "عکس", ItemKind.TEXT to "یادداشت", ItemKind.AUDIO to "صدا",
    ItemKind.FILE to "فایل", ItemKind.PLACE to "موقعیت", ItemKind.EVENT to "✓ انجام‌شده",
)

private fun toast(ctx: android.content.Context, msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

// ───────────────────────── کارها ─────────────────────────

/** یک کار (مثل وب‌اپ): حلقهٔ «انجام شد»، عنوان با برچسب سبزِ کوچک کنارش، زیرنویس روز/تکرار، منوی ⋯ و خط جداکننده. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskRow(
    task: TaskEntity,
    vm: StreamViewModel,
    onTag: (String) -> Unit,
    done: Boolean = false,
    showDue: Boolean = true,
    extra: (@Composable () -> Unit)? = null,
) {
    var menu by remember { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf("") }
    val today = vm.today
    val due = task.dueDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val late = !done && due != null && !due.isAfter(today)
    val tags = Tags.tagsIn(task.title)
    val title = Tags.strip(task.title).ifEmpty { task.title }

    when (dialog) {
        "edit" -> EditTaskDialog(vm, task) { dialog = "" }
        "day" -> MoveDayDialog(vm, task) { dialog = "" }
        "move" -> TargetPickerDialog(vm, "انتقال کار به…", { dialog = "" }) { vm.moveTask(task, it); dialog = "" }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
            Box(Modifier.size(44.dp).clickable { if (done) vm.undone(task) else vm.done(task) }, contentAlignment = Alignment.Center) {
                val ringColor = if (done) MaterialTheme.board.ok else if (late) MaterialTheme.board.amber else MaterialTheme.board.muted
                Box(
                    Modifier.size(24.dp).clip(CircleShape)
                        .background(if (done) MaterialTheme.board.ok else Color.Transparent)
                        .border(2.dp, ringColor, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    if (done) Text("✓", color = MaterialTheme.board.accentInk, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 13.sp)
                }
            }
            Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        Digits.fa(title), style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.align(Alignment.CenterVertically),
                        textDecoration = if (done) TextDecoration.LineThrough else null,
                        color = if (done) MaterialTheme.board.muted else MaterialTheme.colorScheme.onSurface,
                    )
                    tags.forEach { Box(Modifier.align(Alignment.CenterVertically)) { TagChip(it) { onTag(it) } } }
                }
                val sub = buildList {
                    if (due != null && showDue) add(DayLabel.of(due, today))
                    if (task.repeat != null) add(When.repeatLabel(task.repeat))
                }.joinToString(" · ")
                if (sub.isNotEmpty()) {
                    Text(
                        (if (late && due != null && due.isBefore(today)) "از ${DayLabel.of(due, today)} مانده" else sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (late) MaterialTheme.board.amber else MaterialTheme.board.muted,
                    )
                }
                extra?.invoke()
            }
            Box {
                TextButton({ menu = true }) { Text("⋯", color = MaterialTheme.board.muted) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("تغییر روز…") }, { menu = false; dialog = "day" })
                    DropdownMenuItem({ Text("ویرایش") }, { menu = false; dialog = "edit" })
                    if (task.repeat != null) DropdownMenuItem({ Text("متوقف کردن تکرار") }, { menu = false; vm.stopRepeat(task) })
                    DropdownMenuItem({ Text("انتقال به پروندهٔ دیگر…") }, { menu = false; dialog = "move" })
                    DropdownMenuItem({ Text("حذف") }, { menu = false; vm.deleteTask(task) })
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.board.line)
    }
}

// ───────────────────────── سابقه ─────────────────────────

/** سابقه: چیپ‌های نوع، سنجاق‌شده‌ها، مورد‌ها، «نمایش قدیمی‌تر». */
fun LazyListScope.historySection(
    st: StreamState,
    vm: StreamViewModel,
    nowMillis: Long,
    onTag: (String) -> Unit,
    onViewPhoto: (ItemEntity) -> Unit,
) {
    item("h-label") { SectionLabel("سابقه") }
    val kinds = KIND_LABELS.filter { (k, _) -> (st.kinds[k] ?: 0) > 0 }
    if (kinds.isNotEmpty()) {
        item("h-chips") {
            androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item { Chip("همه", selected = st.kind == null) { vm.setKind(null) } }
                kinds.forEach { (k, label) -> item(k) { Chip(Digits.fa("$label · ${st.kinds[k]}"), selected = st.kind == k) { vm.setKind(k) } } }
            }
        }
    }
    if (st.pinned.isNotEmpty() && st.kind == null) {
        items(st.pinned.size, key = { "p${st.pinned[it].id}" }) { ItemCard(st.pinned[it], vm, nowMillis, onTag, onViewPhoto) }
    }
    if (st.kind == ItemKind.PHOTO) {
        val photos = st.items.filter { it.filePath != null }
        val rows = photos.chunked(3)
        items(rows.size, key = { "g${rows[it].first().id}" }) { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                rows[r].forEach { p ->
                    AsyncImage(
                        model = vm.files.file(p.filePath!!), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)).clickable { onViewPhoto(p) },
                    )
                }
                repeat(3 - rows[r].size) { Box(Modifier.weight(1f)) }
            }
        }
    } else {
        items(st.items.size, key = { "i${st.items[it].id}" }) { ItemCard(st.items[it], vm, nowMillis, onTag, onViewPhoto) }
    }
    if (st.hasMore) item("more") { TextButton({ vm.showMore() }) { Text("نمایش موارد قدیمی‌تر") } }
    if (st.loaded && st.items.isEmpty() && st.pinned.isEmpty()) {
        item("empty") { EmptyNote(if (st.kind == null) "هنوز چیزی ثبت نشده. از پایین صفحه یادداشت، عکس، صدا یا فایل اضافه کن." else "چیزی از این نوع نیست.") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ItemCard(item: ItemEntity, vm: StreamViewModel, nowMillis: Long, onTag: (String) -> Unit, onViewPhoto: (ItemEntity) -> Unit) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf("") }
    val stamp = Stamp.of(item.createdAt, nowMillis, ZoneId.systemDefault())

    when (dialog) {
        "edit" -> EditNoteDialog(vm, item) { dialog = "" }
        "task" -> ConvertToTaskDialog(vm, item) { dialog = "" }
        "move" -> TargetPickerDialog(vm, "انتقال به…", { dialog = "" }) { vm.moveItem(item, it); dialog = "" }
    }

    if (item.kind == ItemKind.EVENT) {
        // رویداد «کار انجام شد»: فقط یک ردیف کم‌رنگ، ولی با منوی ⋯ تا بشود حذفش کرد.
        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "✓ " + Digits.fa(item.text) + " · " + stamp,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.board.muted,
                modifier = Modifier.weight(1f).padding(vertical = 2.dp),
            )
            Box {
                TextButton({ menu = true }) { Text("⋯") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text("حذف") }, { menu = false; vm.deleteItem(item) })
                }
            }
        }
        return
    }

    SurfaceCard {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                (if (item.pinnedAt != null) "▲ سنجاق‌شده · " else "") + stamp,
                style = MaterialTheme.typography.labelSmall, color = if (item.pinnedAt != null) MaterialTheme.board.amber else MaterialTheme.board.muted,
                modifier = Modifier.weight(1f),
            )
            Box {
                TextButton({ menu = true }) { Text("⋯") }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem({ Text(if (item.pinnedAt != null) "برداشتن سنجاق" else "سنجاق") }, { menu = false; vm.pin(item) })
                    if (item.kind == ItemKind.TEXT) {
                        DropdownMenuItem({ Text("ویرایش") }, { menu = false; dialog = "edit" })
                        DropdownMenuItem({ Text("تبدیل به کار با زمان…") }, { menu = false; dialog = "task" })
                    }
                    DropdownMenuItem({ Text("انتقال…") }, { menu = false; dialog = "move" })
                    DropdownMenuItem({ Text("حذف") }, { menu = false; vm.deleteItem(item) })
                }
            }
        }
        when (item.kind) {
            ItemKind.PHOTO -> {
                if (item.filePath != null) {
                    AsyncImage(
                        model = vm.files.file(item.filePath), contentDescription = item.fileName, contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(10.dp)).clickable { onViewPhoto(item) },
                    )
                }
                if (item.text.isNotBlank()) NoteText(item.text, onTag)
            }
            ItemKind.AUDIO -> {
                if (item.filePath != null) AudioRow(vm.files, item.filePath)
                if (item.text.isNotBlank()) NoteText(item.text, onTag)
            }
            ItemKind.FILE -> {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(item.fileName ?: "فایل", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Pill("باز کردن") { openFile(ctx, vm.files, item) }
                }
            }
            ItemKind.PLACE -> {
                if (item.lat != null && item.lng != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text("موقعیت", style = MaterialTheme.typography.bodyMedium)
                            Text(Digits.fa("%.5f, %.5f".format(item.lat, item.lng)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
                        }
                        Pill("نقشه") {
                            try {
                                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${item.lat},${item.lng}?q=${item.lat},${item.lng}")))
                            } catch (_: ActivityNotFoundException) {
                                toast(ctx, "برنامهٔ نقشه پیدا نشد")
                            }
                        }
                    }
                }
            }
            else -> NoteText(item.text, onTag)
        }
    }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NoteText(text: String, onTag: (String) -> Unit) {
    val tags = Tags.tagsIn(text)
    val shown = if (tags.isEmpty()) text else Tags.strip(text).ifEmpty { text }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(Digits.fa(shown), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.align(Alignment.CenterVertically))
        tags.forEach { Box(Modifier.align(Alignment.CenterVertically)) { TagChip(it) { onTag(it) } } }
    }
}

private fun openFile(ctx: android.content.Context, files: FileStore, item: ItemEntity) {
    val path = item.filePath ?: return
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(files.uriFor(path), item.mime ?: "*/*")
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        ctx.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        toast(ctx, "برنامه‌ای برای باز کردن این فایل نیست")
    }
}

@Composable
private fun AudioRow(files: FileStore, path: String) {
    var playing by remember { mutableStateOf(false) }
    val player = remember { MediaPlayer() }
    DisposableEffect(Unit) { onDispose { runCatching { player.release() } } }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Pill(if (playing) "توقف" else "پخش") {
            if (playing) {
                runCatching { player.stop() }
                playing = false
            } else {
                runCatching {
                    player.reset()
                    player.setDataSource(files.file(path).absolutePath)
                    player.setOnCompletionListener { playing = false }
                    player.prepare()
                    player.start()
                    playing = true
                }
            }
        }
        Text("صدای ضبط‌شده", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
    }
}

/** اشتراک یک فایل با برنامه‌های دیگر. */
fun shareFile(ctx: android.content.Context, files: FileStore, item: ItemEntity) {
    val path = item.filePath ?: return
    val send = Intent(Intent.ACTION_SEND).setType(item.mime ?: "*/*")
        .putExtra(Intent.EXTRA_STREAM, files.uriFor(path))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        ctx.startActivity(Intent.createChooser(send, null))
    } catch (_: ActivityNotFoundException) {
        toast(ctx, "اشتراک ممکن نشد")
    }
}

internal fun Color.dim() = copy(alpha = 0.7f)
