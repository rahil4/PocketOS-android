package app.pocketos.board.ui.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketos.board.domain.AppSettings
import app.pocketos.board.domain.Digits
import app.pocketos.board.ui.common.Pill
import app.pocketos.board.ui.common.SurfaceCard
import app.pocketos.board.ui.theme.board

@Composable
fun SettingsScreen(onBack: () -> Unit, onOpenTrash: () -> Unit, vm: SettingsViewModel = hiltViewModel()) {
    val s by vm.state.collectAsStateWithLifecycle()
    val b by vm.backupState.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val createData = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { vm.createData(it) }
    val createFiles = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { vm.createFiles(it) }
    val openBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.inspect(it) }
    val pickWebFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.pickWebFiles(it) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshBackup() }

    b.busy?.let { msg ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text(msg) },
            text = { CircularProgressIndicator() },
            confirmButton = {},
        )
    }
    b.web?.let { w ->
        WebImportDialog(
            w, b.webFiles,
            onPickFiles = { pickWebFiles.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
            onConfirm = { vm.confirmWebImport() }, onDismiss = { vm.dismissPreview() },
        )
    }
    b.preview?.let { p -> RestoreDialog(p, onConfirm = { vm.confirmRestore() }, onDismiss = { vm.dismissPreview() }) }
    var notifOn by remember { mutableStateOf(NotificationManagerCompat.from(ctx).areNotificationsEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { notifOn = NotificationManagerCompat.from(ctx).areNotificationsEnabled() }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notifOn = NotificationManagerCompat.from(ctx).areNotificationsEnabled()
        // اگر کاربر پیش‌تر رد کرده بود، پنجرهٔ مجوز دیگر نمی‌آید؛ تنظیمات اعلان خود اندروید باز می‌شود.
        if (!notifOn) openNotificationSettings(ctx)
    }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(top = 14.dp, bottom = 32.dp),
    ) {
        item("top") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("تنظیمات", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Pill("←", onClick = onBack)
            }
        }
        item("trash") {
            Row(Modifier.fillMaxWidth().clickable(onClick = onOpenTrash).padding(vertical = 12.dp, horizontal = 2.dp)) {
                Text("سطل بازیافت", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text("←", color = MaterialTheme.board.muted)
            }
            HorizontalDivider(color = MaterialTheme.board.line)
        }
        item("look") {
            SurfaceCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SelectField("ظاهر", AppSettings.THEMES, s.theme) { c -> vm.update { it.copy(theme = c) } }
                    SelectField("اندازهٔ نوشته", AppSettings.SIZES, s.fsize) { c -> vm.update { it.copy(fsize = c) } }
                    SelectField(
                        "یادآوری «بستن روز» از ساعت",
                        AppSettings.EVENING_HOURS.map { it.toString() to if (it == 0) "خاموش" else Digits.fa(it) },
                        s.eveningHour.toString(),
                    ) { c -> vm.update { it.copy(eveningHour = c.toInt()) } }
                }
            }
        }
        item("push") {
            SurfaceCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("اعلان‌ها روی این گوشی", style = MaterialTheme.typography.titleMedium)
                    if (notifOn) {
                        Text("✓ مجوز اعلان داده شده", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    } else {
                        Text("برای دریافت اعلان باید مجوز بدهید.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
                        Pill("فعال‌سازی اعلان‌ها روی این گوشی", primary = true) {
                            if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) else openNotificationSettings(ctx)
                        }
                    }
                    CheckRow("اعلان صبحگاهی «کارهای امروز»", s.morningOn) { c -> vm.update { it.copy(morningOn = c) } }
                    SelectField(
                        "ساعت ارسال",
                        AppSettings.MORNING_HOURS.map { it.toString() to Digits.fa("$it:00") },
                        s.morningHour.toString(),
                    ) { c -> vm.update { it.copy(morningHour = c.toInt()) } }
                    CheckRow("یادآوری «روز را ببند» در عصر (ساعتش همان «بستن روز» بالاست)", s.eveningPush) { c -> vm.update { it.copy(eveningPush = c) } }
                    CheckRow("نام متقاضی و عنوان کارها در اعلان نمایش داده شود", s.pushNames) { c -> vm.update { it.copy(pushNames = c) } }
                    Pill("ارسال اعلان آزمایشی") { vm.sendTest() }
                    Text(
                        "اگر اعلانی نیامد: مجوز اعلان و «بهینه‌سازی باتری» را برای برنامه بررسی کنید. اعلان‌ها حدود ۱۵ تا ۳۰ دقیقه تأخیر دارند.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted,
                    )
                }
            }
        }
        item("backup") {
            SurfaceCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("پشتیبان", style = MaterialTheme.typography.titleMedium)
                    Text(b.status, style = MaterialTheme.typography.bodyMedium, color = if (b.status.startsWith("✓")) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    Text(
                        "پشتیبان داخل اپ نمی‌ماند؛ هر جا انتخاب کنید ذخیره می‌شود (گوشی، Google Drive، …). فایل را جایی امن نگه دارید.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted,
                    )
                    Pill(Digits.fa("پشتیبان داده‌ها (ZIP · حدود ${b.dataMb} مگابایت)"), primary = true) { createData.launch(vm.dataFileName()) }
                    Pill(Digits.fa("پشتیبان عکس‌ها و فایل‌ها (ZIP · حدود ${b.filesMb} مگابایت)")) { createFiles.launch(vm.filesFileName()) }
                    Text(
                        "پشتیبانِ عکس‌ها بدون «داده‌ها» به کار نمی‌آید؛ هر دو را با هم نگه دارید. «داده‌ها» کوچک است؛ هفته‌ای یک‌بار کافی است، و «عکس‌ها و فایل‌ها» هر چند وقت یک‌بار.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted,
                    )
                    CheckRow("هر جمعه صبح، اعلان «یک نسخه‌ی پشتیبان بگیر» (اگر ۷ روز نگرفته باشید)", s.backupRemind) { c -> vm.update { it.copy(backupRemind = c) } }
                    Pill("بازیابی از پشتیبان (خود اپ یا وب‌اپ)…") { openBackup.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) }
                }
            }
        }
        item("about") {
            SurfaceCard {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("دربارهٔ دفتر", style = MaterialTheme.typography.titleMedium)
                    Text(
                        Digits.fa("نسخهٔ ${b.version} · بدون اینترنت، بدون سرور و بدون هوش مصنوعی. دادهٔ شما فقط روی همین گوشی است."),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted,
                    )
                    Text(
                        "داده روی حافظه رمزگذاری نشده؛ قفل گوشی را فعال نگه دارید.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted,
                    )
                }
            }
        }
    }
}

private fun openNotificationSettings(ctx: Context) {
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

@Composable
private fun CheckRow(text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.Top) {
        Checkbox(checked, onCheckedChange = onChange)
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(top = 12.dp))
    }
}

/** فهرست کشویی ساده: برچسب بالا، مقدار فعلی داخل کادر. */
@Composable
private fun SelectField(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(12.dp)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
        Box {
            Row(
                Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.background).border(1.dp, MaterialTheme.board.line, shape)
                    .clickable { open = true }.padding(horizontal = 12.dp, vertical = 11.dp),
            ) {
                Text(options.firstOrNull { it.first == selected }?.second ?: selected, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text("▾", color = MaterialTheme.board.muted)
            }
            DropdownMenu(open, { open = false }) {
                options.forEach { (code, text) -> DropdownMenuItem({ Text(text) }, { open = false; onSelect(code) }) }
            }
        }
    }
}

/** خلاصهٔ ZIP پیش از بازیابی؛ جایگزینی فقط با تأیید. */
@Composable
private fun RestoreDialog(p: app.pocketos.board.data.repo.RestorePreview, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val invalid = p.kind == app.pocketos.board.data.repo.RestoreKind.INVALID
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (invalid) "بازیابی ممکن نیست" else "بازیابی از پشتیبان؟") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (p.kind) {
                    app.pocketos.board.data.repo.RestoreKind.INVALID -> Text(p.error ?: "فایل معتبر نیست.")
                    app.pocketos.board.data.repo.RestoreKind.DATA -> {
                        p.createdAt?.let { Text(Digits.fa(app.pocketos.board.domain.Jalali.formatLong(java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneId.systemDefault()).toLocalDate())), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted) }
                        Text(Digits.fa("${p.customers} متقاضی · ${p.cases} پرونده · ${p.tasks} کار · ${p.notes} یادداشت"))
                        Text(
                            "داده‌های فعلی این گوشی با این نسخه جایگزین می‌شود و برنامه دوباره باز می‌شود. یک نسخهٔ پیش از بازیابی در حافظهٔ خود اپ نگه داشته می‌شود.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.amber,
                        )
                    }
                    app.pocketos.board.data.repo.RestoreKind.FILES -> {
                        Text(Digits.fa("${p.files} عکس، صدا و فایل"))
                        Text(
                            "فایل‌ها به حافظهٔ اپ اضافه می‌شود (اگر پیش‌تر هست، رونویسی). این پشتیبان فقط با «داده‌ها» معنی دارد.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted,
                        )
                    }
                }
            }
        },
        confirmButton = { if (!invalid) TextButton(onConfirm) { Text(if (p.kind == app.pocketos.board.data.repo.RestoreKind.DATA) "جایگزین کن" else "بازیابی کن") } },
        dismissButton = { TextButton(onDismiss) { Text(if (invalid) "باشه" else "بیخیال") } },
    )
}

/** پشتیبان وب‌اپ: خلاصهٔ تبدیل، انتخاب اختیاری ZIP فایل‌ها، و تأیید جایگزینی. */
@Composable
private fun WebImportDialog(w: app.pocketos.board.data.repo.WebPreview, filesInfo: String?, onPickFiles: () -> Unit, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val bad = w.error != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (bad) "وارد کردن ممکن نیست" else "پشتیبان وب‌اپ دفتر") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (bad) Text(w.error ?: "")
                else {
                    Text(Digits.fa("${w.customers} متقاضی · ${w.cases} پرونده · ${w.tasks} کار · ${w.notes} یادداشت"))
                    if (w.orphanCases > 0) {
                        Text(Digits.fa("${w.orphanCases} پرونده در وب‌اپ متقاضی نداشت؛ زیر متقاضیِ «پرونده‌های بدون متقاضی» می‌آید."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
                    }
                    if (w.users > 1) {
                        Text(Digits.fa("پشتیبان ${w.users} کاربر دارد؛ داده‌ی همه یک‌جا وارد می‌شود و تنظیمات از کاربر اول است."), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
                    }
                    if (w.files > 0) {
                        Text(Digits.fa("${w.files} عکس، صدا و فایل در داده‌ها ثبت شده. برای آوردنشان، ZIP «فایل‌ها» (board-files-…) را هم انتخاب کنید:"), style = MaterialTheme.typography.bodySmall)
                        Pill(if (filesInfo == null) "انتخاب ZIP فایل‌ها…" else "عوض کردن ZIP فایل‌ها…", onClick = onPickFiles)
                        filesInfo?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
                    }
                    Text(
                        "داده‌های فعلی این گوشی با این پشتیبان جایگزین می‌شود و برنامه دوباره باز می‌شود. یک نسخهٔ پیش از آن در حافظهٔ خود اپ نگه داشته می‌شود.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.amber,
                    )
                }
            }
        },
        confirmButton = { if (!bad) TextButton(onConfirm) { Text("جایگزین کن") } },
        dismissButton = { TextButton(onDismiss) { Text(if (bad) "باشه" else "بیخیال") } },
    )
}
