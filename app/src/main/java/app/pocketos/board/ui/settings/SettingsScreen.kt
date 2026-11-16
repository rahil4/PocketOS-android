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
import androidx.compose.material3.Checkbox
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
    val ctx = LocalContext.current
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
                    CheckRow("نام مشتری و عنوان کارها در اعلان نمایش داده شود", s.pushNames) { c -> vm.update { it.copy(pushNames = c) } }
                    Pill("ارسال اعلان آزمایشی") { vm.sendTest() }
                    Text(
                        "اگر اعلانی نیامد: مجوز اعلان و «بهینه‌سازی باتری» را برای برنامه بررسی کنید. اعلان‌ها حدود ۱۵ تا ۳۰ دقیقه تأخیر دارند.",
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
