package app.pocketos.board.widget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import app.pocketos.board.data.repo.SettingsRepository
import app.pocketos.board.data.repo.TagRepository
import app.pocketos.board.data.repo.WidgetSettingsRepository
import app.pocketos.board.domain.Digits
import app.pocketos.board.domain.TagMode
import app.pocketos.board.domain.WidgetPrefs
import app.pocketos.board.domain.WidgetTone
import app.pocketos.board.ui.common.Chip
import app.pocketos.board.ui.common.Pill
import app.pocketos.board.ui.theme.BoardTheme
import app.pocketos.board.ui.theme.board
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * تنظیمات ویجت «امروز»: هنگام افزودن ویجت و با نگه‌داشتن انگشت روی آن («تنظیمات») باز می‌شود.
 * هر تغییر همان لحظه ذخیره و روی ویجت اعمال می‌شود؛ «ذخیره» فقط صفحه را می‌بندد.
 */
@AndroidEntryPoint
class WidgetConfigActivity : ComponentActivity() {
    @Inject lateinit var store: WidgetSettingsRepository
    @Inject lateinit var tagRepo: TagRepository
    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        // حتی اگر کاربر با «بازگشت» بیرون برود، ویجت افزوده شود (تنظیمات پیش‌فرض/ذخیره‌شده).
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        } else setResult(Activity.RESULT_CANCELED)

        setContent {
            val theme = settings.state.value.theme
            val dark = when (theme) { "dark" -> true; "light" -> false; else -> isSystemInDarkTheme() }
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                BoardTheme(dark) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        Content(onDone = { finish() })
                    }
                }
            }
        }
    }

    private fun apply(p: WidgetPrefs) {
        lifecycleScope.launch {
            store.save(p)
            runCatching { updateAllBoardWidgets(applicationContext) }
        }
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable
    private fun Content(onDone: () -> Unit) {
        var p by remember { mutableStateOf<WidgetPrefs?>(null) }
        var sliding by remember { mutableIntStateOf(100) }
        LaunchedEffect(Unit) { store.load().also { p = it; sliding = it.opacity } }
        val allTags by produceState(emptyList<String>()) { value = tagRepo.all().map { it.first } }
        val cur = p ?: return
        fun change(n: WidgetPrefs) { p = n; apply(n) }

        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("تنظیمات ویجت", style = MaterialTheme.typography.titleLarge)

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("نام متقاضی زیر هر کار", Modifier.weight(1f))
                Switch(cur.showCustomer, { change(cur.copy(showCustomer = it)) })
            }

            Text("تعداد کار", color = MaterialTheme.board.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WidgetPrefs.COUNTS.forEach { n -> Chip(Digits.fa(n), selected = cur.count == n) { change(cur.copy(count = n)) } }
            }

            Text(Digits.fa("شفافیت پس‌زمینه: ${sliding}٪"), color = MaterialTheme.board.muted)
            Slider(
                value = sliding.toFloat(), onValueChange = { sliding = it.toInt() },
                onValueChangeFinished = { change(cur.copy(opacity = sliding)) },
                valueRange = 0f..100f,
            )

            Text("رنگ متن", color = MaterialTheme.board.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                WidgetTone.entries.forEach { tone ->
                    val on = cur.tone == tone
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(Color(tone.argb))
                            .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.board.line, CircleShape)
                            .clickable(onClickLabel = tone.label) { change(cur.copy(tone = tone)) },
                    )
                }
            }
            Text("«مانده‌ها» همچنان کهربایی می‌مانند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)

            Text("برچسب‌های نمایش‌داده‌شده", color = MaterialTheme.board.muted)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Chip("هیچ‌کدام", selected = cur.tagMode == TagMode.NONE) { change(cur.copy(tagMode = TagMode.NONE)) }
                Chip("انتخابی", selected = cur.tagMode == TagMode.SELECTED) { change(cur.copy(tagMode = TagMode.SELECTED)) }
                Chip("همه", selected = cur.tagMode == TagMode.ALL) { change(cur.copy(tagMode = TagMode.ALL)) }
            }
            if (cur.tagMode == TagMode.SELECTED) {
                if (allTags.isEmpty()) {
                    Text("هنوز برچسبی ندارید.", color = MaterialTheme.board.muted)
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        allTags.forEach { t ->
                            val on = t in cur.tags
                            Chip("#$t", selected = on) { change(cur.copy(tags = if (on) cur.tags - t else cur.tags + t)) }
                        }
                    }
                    Text("فقط برچسب‌های انتخاب‌شده کنار کار نوشته می‌شوند؛ بقیه پنهان‌اند.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.board.muted)
                }
            }

            Spacer(Modifier.height(6.dp))
            Pill("ذخیره", primary = true, modifier = Modifier.fillMaxWidth()) { onDone() }
        }
    }
}
