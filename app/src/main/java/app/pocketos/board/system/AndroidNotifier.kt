package app.pocketos.board.system

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.pocketos.board.MainActivity
import app.pocketos.board.R
import app.pocketos.board.data.repo.ReminderSink
import app.pocketos.board.domain.Payload

/** اعلان محلی اندروید. لمس اعلان، [Payload.route] را در برنامه باز می‌کند. */
class AndroidNotifier(private val context: Context) : ReminderSink {
    init {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "یادآوری‌ها", NotificationManager.IMPORTANCE_DEFAULT))
    }

    override fun canNotify(): Boolean {
        val granted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    override fun show(payload: Payload) {
        if (!canNotify()) return
        val open = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_ROUTE, payload.route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pi = PendingIntent.getActivity(context, payload.tag.hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_note)
            .setContentTitle(payload.title)
            .setContentText(payload.body.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(payload.body))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(pi)
            .build()
        NotificationManagerCompat.from(context).notify(payload.tag.hashCode(), n)
    }

    private companion object {
        const val CHANNEL = "reminders"
    }
}
