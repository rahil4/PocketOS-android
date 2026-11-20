package app.pocketos.board.widget

import android.content.Context
import app.pocketos.board.data.repo.HomeRepository
import app.pocketos.board.data.repo.WidgetSettingsRepository
import app.pocketos.board.domain.WidgetLogic
import app.pocketos.board.domain.WidgetPrefs
import androidx.glance.appwidget.updateAll
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

data class WidgetTask(val title: String, val customer: String?)

/** داده‌ی مشترک دو ویجت: کارهای امروز و عقب‌مانده جدا. */
data class WidgetData(
    val today: List<WidgetTask>,
    val late: List<WidgetTask>,
    val date: String,
    val prefs: WidgetPrefs,
)

suspend fun loadWidgetData(context: Context): WidgetData {
    val ep = EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
    val prefs = ep.widgetSettings().load()
    val rows = ep.home().observeToday().first().rows.filter { it.entry.isTask }
    fun task(r: app.pocketos.board.data.repo.HomeRow) = WidgetTask(WidgetLogic.title(r.entry.title, prefs), r.entry.customerName)
    val (late, today) = rows.partition { it.entry.lateDays > 0 }
    return WidgetData(today.map(::task), late.map(::task), WidgetLogic.dateLine(LocalDate.now()), prefs)
}

suspend fun updateAllBoardWidgets(context: Context) {
    TodayWidget().updateAllWidgets(context)
    CounterWidget().updateAll(context)
}
