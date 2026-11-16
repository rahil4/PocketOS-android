package app.pocketos.board.data.repo

import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.domain.BackupStatus
import app.pocketos.board.domain.Payload
import app.pocketos.board.domain.ReminderTask
import app.pocketos.board.domain.Reminders
import java.time.Clock
import java.time.LocalDate
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** جایی که اعلان نمایش داده می‌شود (در آزمون جعلی است). */
interface ReminderSink {
    fun canNotify(): Boolean
    fun show(payload: Payload)
}

/**
 * اعلان‌های صبحگاهی و عصرانه (همان `run_reminders` وب‌اپ، ولی محلی): از زمان‌بند هر ~۱۵ دقیقه صدا زده می‌شود؛
 * هر اعلان روزی یک‌بار. بدون کار، اعلانی نمی‌رود.
 */
@Singleton
class ReminderService @Inject constructor(
    private val db: BoardDatabase,
    private val prefs: PrefRepository,
    private val settings: SettingsRepository,
    private val sink: ReminderSink,
    private val clock: Clock,
) {
    private suspend fun rowsUpTo(today: LocalDate): List<ReminderTask> =
        db.taskDao().openUpTo(today.toString()).mapNotNull { r ->
            val due = r.task.dueDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return@mapNotNull null
            ReminderTask(r.task.title, due, r.customerName)
        }

    suspend fun run(): List<Payload> {
        // بدون مجوز اعلان چیزی «فرستاده‌شده» ثبت نمی‌شود؛ بعد از دادن مجوز، همان روز هنوز می‌رسد.
        if (!sink.canNotify()) return emptyList()
        val now = ZonedDateTime.now(clock)
        val today = now.toLocalDate()
        val s = settings.load()
        val sent = ArrayList<Payload>()

        if (Reminders.morningDue(s.morningOn, s.morningHour, now.hour, prefs.getString(PrefRepository.LAST_MORNING), today)) {
            Reminders.morning(rowsUpTo(today), today, s.pushNames)?.let { sink.show(it); sent += it }
            prefs.setString(PrefRepository.LAST_MORNING, today.toString())
        }
        if (Reminders.eveningDue(
                s.eveningPush, s.eveningHour, now.hour, prefs.getString(PrefRepository.LAST_EVENING),
                prefs.getString(PrefRepository.CLOSED_ON), today,
            )
        ) {
            val n = rowsUpTo(today).size
            if (n > 0) Reminders.evening(n).let { sink.show(it); sent += it }
            prefs.setString(PrefRepository.LAST_EVENING, today.toString())
        }
        // جمعه صبح: «یک نسخهٔ پشتیبان بگیر» (اگر ۷ روز نگرفته باشید)
        val ago = BackupStatus.daysSince(prefs.getString(PrefRepository.LAST_BACKUP), today)
        if (Reminders.backupDue(s.backupRemind, today, now.hour, s.morningHour, prefs.getString(PrefRepository.LAST_BACKUP_REMIND), ago)) {
            Reminders.backup(ago).let { sink.show(it); sent += it }
            prefs.setString(PrefRepository.LAST_BACKUP_REMIND, today.toString())
        }
        return sent
    }

    /** «ارسال اعلان آزمایشی»: همین حالا، فارغ از ساعت. */
    suspend fun sendTest(): Boolean {
        if (!sink.canNotify()) return false
        val today = LocalDate.now(clock)
        val payload = Reminders.morning(rowsUpTo(today), today, settings.load().pushNames)
            ?: Payload("اعلان آزمایشی", "اگر این را می‌بینید، اعلان‌ها کار می‌کنند.", "home", "test")
        sink.show(payload)
        return true
    }
}
