package app.pocketos.board

import android.app.Application
import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.repo.TrashRepository
import app.pocketos.board.system.ReminderWorker
import app.pocketos.board.widget.TodayWidget
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BoardApp : Application() {
    @Inject lateinit var trash: TrashRepository
    @Inject lateinit var db: BoardDatabase

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        // هر چیزی که بیش از ۳۰ روز در سطل بوده برای همیشه پاک می‌شود.
        scope.launch { runCatching { trash.purge() } }
        // اعلان‌های صبحگاهی/عصرانه (هر ~۱۵ دقیقه) و تازه‌سازی ویجت.
        ReminderWorker.schedule(this)
        // با هر تغییر در کارها، ویجت (تا وقتی برنامه زنده است) تازه می‌شود.
        scope.launch {
            db.taskDao().observeVersion().debounce(2_000).collect { runCatching { TodayWidget().updateAllWidgets(applicationContext) } }
        }
    }
}
