package app.pocketos.board.system

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.pocketos.board.data.repo.ReminderService
import app.pocketos.board.widget.TodayWidget
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WorkEntryPoint {
    fun reminders(): ReminderService
}

/** هر ~۱۵ دقیقه: اعلان‌های سررسید را می‌فرستد و ویجت را تازه می‌کند. */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(applicationContext, WorkEntryPoint::class.java)
        runCatching { entry.reminders().run() }
        runCatching { TodayWidget().updateAllWidgets(applicationContext) }
        return Result.success()
    }

    companion object {
        private const val NAME = "reminders"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
