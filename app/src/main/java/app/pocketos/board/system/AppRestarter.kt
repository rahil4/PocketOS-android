package app.pocketos.board.system

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/** برنامه را کامل می‌بندد و چند لحظه بعد دوباره باز می‌کند (برای اعمال بازیابی). */
object AppRestarter {
    fun restart(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK) ?: return
        val pi = PendingIntent.getActivity(context, 77, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_CANCEL_CURRENT)
        context.getSystemService(AlarmManager::class.java).set(AlarmManager.RTC, System.currentTimeMillis() + 500, pi)
        Runtime.getRuntime().exit(0)
    }
}
