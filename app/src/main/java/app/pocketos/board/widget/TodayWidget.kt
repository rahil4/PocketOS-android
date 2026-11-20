package app.pocketos.board.widget

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.LocalSize
import app.pocketos.board.MainActivity
import app.pocketos.board.data.repo.HomeRepository
import app.pocketos.board.data.repo.WidgetSettingsRepository
import app.pocketos.board.domain.Digits
import app.pocketos.board.domain.WidgetLogic
import app.pocketos.board.domain.WidgetPrefs
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.time.LocalDate

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun home(): HomeRepository
    fun widgetSettings(): WidgetSettingsRepository
}

data class WidgetTask(val title: String, val customer: String?, val late: Boolean)

data class WidgetData(val today: Int, val late: Int, val tasks: List<WidgetTask>, val date: String, val prefs: WidgetPrefs) {
    val total: Int get() = today + late
}

private val CARD_BASE = Color(0xFF192019)
private val TEXT = ColorProvider(Color(0xFFE8EEE9))
private val MUTED = ColorProvider(Color(0xFF8B9A90))
private val ACCENT = ColorProvider(Color(0xFF8CC9A1))
private val AMBER = ColorProvider(Color(0xFFE2B872))
private val INK = ColorProvider(Color(0xFF0C1710))

/**
 * ویجت «امروز»: تاریخ، «N کار دارید»، کارهای امروز و مانده، و «＋ یادداشت». لمس، برنامه را باز می‌کند.
 * راست‌به‌چپ؛ تنظیمات (نام متقاضی، تعداد، شفافیت، برچسب‌ها) در [WidgetConfigActivity].
 */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp), DpSize(250.dp, 250.dp)),
    )

    suspend fun updateAllWidgets(context: Context) = updateAll(context)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = load(context)
        provideContent { Content(context, data) }
    }

    private suspend fun load(context: Context): WidgetData {
        val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
        val prefs = ep.widgetSettings().load()
        val rows = ep.home().observeToday().first().rows.filter { it.entry.isTask }
        val tasks = rows.map { WidgetTask(WidgetLogic.title(it.entry.title, prefs), it.entry.customerName, it.entry.lateDays > 0) }
        val late = rows.count { it.entry.lateDays > 0 }
        return WidgetData(rows.size - late, late, tasks, WidgetLogic.dateLine(LocalDate.now()), prefs)
    }

    @Composable
    private fun Content(context: Context, d: WidgetData) {
        val size = LocalSize.current
        val openApp = actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val quickNote = actionStartActivity(
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_QUICK_NOTE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val small = size.width < 200.dp
        val rowsShown = if (size.height >= 200.dp) d.prefs.count else minOf(d.prefs.count, 3)
        val rtl = context.resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val card = ColorProvider(CARD_BASE.copy(alpha = d.prefs.opacity / 100f))
        val right = TextAlign.Right    // مطلق (نه start): چیدمان ویجت از جهت لانچر می‌آید، اما متن همیشه راست‌چین است
        val full = GlanceModifier.fillMaxWidth()

        Column(
            GlanceModifier.fillMaxSize().background(card).cornerRadius(22.dp).padding(12.dp).clickable(openApp),
            horizontalAlignment = if (small) Alignment.Horizontal.CenterHorizontally else Alignment.Horizontal.Start,
        ) {
            if (small) {
                Text(d.date, style = TextStyle(color = MUTED, fontSize = 11.sp, textAlign = TextAlign.Center), maxLines = 1)
                Text(Digits.fa(d.total), style = TextStyle(color = ACCENT, fontSize = 30.sp, fontWeight = FontWeight.Bold))
                Text(if (d.late > 0) Digits.fa("کار · ${d.late} مانده") else "کار", style = TextStyle(color = if (d.late > 0) AMBER else MUTED, fontSize = 11.sp))
                Spacer(GlanceModifier.height(6.dp))
                NoteButton(quickNote)
            } else {
                Text(d.date, full, style = TextStyle(color = TEXT, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = right), maxLines = 1)
                Text(
                    WidgetLogic.countLine(d.total, d.late), full,
                    style = TextStyle(color = if (d.late > 0) AMBER else MUTED, fontSize = 12.sp, textAlign = right), maxLines = 1,
                )
                if (d.tasks.isEmpty()) {
                    Spacer(GlanceModifier.height(6.dp))
                    Text("✓ همه‌چیز مرتب است", full, style = TextStyle(color = MUTED, fontSize = 12.sp, textAlign = right))
                }
                d.tasks.take(rowsShown).forEach { t ->
                    Column(full.padding(top = 6.dp)) {
                        Text(Digits.fa(t.title.take(70)), full, style = TextStyle(color = if (t.late) AMBER else TEXT, fontSize = 12.sp, textAlign = right), maxLines = 1)
                        if (d.prefs.showCustomer && !t.customer.isNullOrBlank()) {
                            Text(t.customer, full, style = TextStyle(color = MUTED, fontSize = 10.sp, textAlign = right), maxLines = 1)
                        }
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                // دکمه سمت راست بصری: در چیدمان چپ‌به‌راست آخر، در راست‌به‌چپ اول.
                Row(full) {
                    if (!rtl) Spacer(GlanceModifier.defaultWeight())
                    NoteButton(quickNote)
                    if (rtl) Spacer(GlanceModifier.defaultWeight())
                }
            }
        }
    }

    @Composable
    private fun NoteButton(action: androidx.glance.action.Action) {
        Box(GlanceModifier.background(ACCENT).cornerRadius(99.dp).padding(horizontal = 14.dp, vertical = 5.dp).clickable(action)) {
            Text("＋ یادداشت", style = TextStyle(color = INK, fontSize = 11.sp, fontWeight = FontWeight.Bold))
        }
    }
}
