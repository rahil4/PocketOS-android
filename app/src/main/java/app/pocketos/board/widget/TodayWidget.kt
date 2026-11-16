package app.pocketos.board.widget

import android.content.Context
import android.content.Intent
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
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.LocalSize
import app.pocketos.board.MainActivity
import app.pocketos.board.data.repo.HomeRepository
import app.pocketos.board.domain.Digits
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun home(): HomeRepository
}

data class WidgetTask(val title: String, val customer: String?, val late: Boolean)

data class WidgetData(val today: Int, val late: Int, val tasks: List<WidgetTask>) {
    val total: Int get() = today + late
}

private val CARD = ColorProvider(Color(0xFF192019))
private val TEXT = ColorProvider(Color(0xFFE8EEE9))
private val MUTED = ColorProvider(Color(0xFF8B9A90))
private val ACCENT = ColorProvider(Color(0xFF8CC9A1))
private val AMBER = ColorProvider(Color(0xFFE2B872))
private val INK = ColorProvider(Color(0xFF0C1710))

/** ویجت «دفتر · امروز»: کارهای امروز و مانده، و «＋ یادداشت». لمس کار، خانه را باز می‌کند. */
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
        val home = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java).home()
        val rows = home.observeToday().first().rows.filter { it.entry.isTask }
        val tasks = rows.map { WidgetTask(it.entry.title, it.entry.customerName, it.entry.lateDays > 0) }
        val late = rows.count { it.entry.lateDays > 0 }
        return WidgetData(rows.size - late, late, tasks)
    }

    @Composable
    private fun Content(context: Context, d: WidgetData) {
        val size = LocalSize.current
        val openApp = actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val quickNote = actionStartActivity(
            Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_QUICK_NOTE).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val small = size.width < 200.dp
        val rowsShown = if (size.height >= 200.dp) 6 else 3

        Column(
            GlanceModifier.fillMaxSize().background(CARD).cornerRadius(22.dp).padding(12.dp).clickable(openApp),
            horizontalAlignment = if (small) Alignment.Horizontal.CenterHorizontally else Alignment.Horizontal.Start,
        ) {
            if (small) {
                Text("دفتر · امروز", style = TextStyle(color = MUTED, fontSize = 11.sp))
                Text(Digits.fa(d.total), style = TextStyle(color = ACCENT, fontSize = 30.sp, fontWeight = FontWeight.Bold))
                Text(if (d.late > 0) Digits.fa("کار · ${d.late} مانده") else "کار", style = TextStyle(color = if (d.late > 0) AMBER else MUTED, fontSize = 11.sp))
                Spacer(GlanceModifier.height(6.dp))
                NoteButton(quickNote)
            } else {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                    Text("دفتر · امروز", style = TextStyle(color = TEXT, fontSize = 13.sp, fontWeight = FontWeight.Bold))
                    Spacer(GlanceModifier.defaultWeight())
                    Text(
                        if (d.total == 0) "" else Digits.fa("${d.total} کار" + if (d.late > 0) " · ${d.late} مانده" else ""),
                        style = TextStyle(color = MUTED, fontSize = 11.sp),
                    )
                }
                if (d.tasks.isEmpty()) {
                    Spacer(GlanceModifier.height(6.dp))
                    Text("✓ برای امروز کاری نیست", style = TextStyle(color = MUTED, fontSize = 12.sp))
                }
                d.tasks.take(rowsShown).forEach { t ->
                    Column(GlanceModifier.fillMaxWidth().padding(top = 5.dp)) {
                        Text(Digits.fa(t.title.take(60)), style = TextStyle(color = if (t.late) AMBER else TEXT, fontSize = 12.sp), maxLines = 1)
                        if (!t.customer.isNullOrBlank()) Text(t.customer, style = TextStyle(color = MUTED, fontSize = 10.sp), maxLines = 1)
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                NoteButton(quickNote)
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
