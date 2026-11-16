package app.pocketos.board.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

/**
 * جایگزینی پایگاه‌داده با نسخهٔ بازیابی‌شده. پیش از باز شدن Room (در شروع برنامه) اجرا می‌شود؛
 * داده‌ی فعلی همیشه در `restore/before-restore.db` کنار گذاشته می‌شود.
 */
object RestoreApplier {
    const val DIR = "restore"
    const val STAGED = "staged.db"
    const val PENDING = "pending.db"
    const val BEFORE = "before-restore.db"

    fun dir(context: Context) = File(context.filesDir, DIR)

    /** true اگر جایگزین شد. */
    fun applyIfPending(context: Context, dbName: String = "board.db"): Boolean {
        val pending = File(dir(context), PENDING)
        if (!pending.exists()) return false
        val target = context.getDatabasePath(dbName)
        return try {
            if (target.exists()) {
                // تغییرات نانوشته در -wal هم به فایل اصلی برسد، بعد یک نسخهٔ پیش از بازیابی نگه داریم.
                runCatching {
                    SQLiteDatabase.openDatabase(target.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
                        db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
                    }
                }
                target.copyTo(File(dir(context), BEFORE), overwrite = true)
            }
            target.parentFile?.mkdirs()
            val staged = File(target.path + ".restore")
            pending.copyTo(staged, overwrite = true) // اول کنار؛ فقط اگر کامل شد جایگزین
            listOf(target, File(target.path + "-wal"), File(target.path + "-shm"), File(target.path + "-journal")).forEach { it.delete() }
            if (!staged.renameTo(target)) {
                staged.copyTo(target, overwrite = true)
                staged.delete()
            }
            pending.delete()
            true
        } catch (_: Exception) {
            false
        }
    }
}
