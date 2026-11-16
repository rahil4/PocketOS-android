package app.pocketos.board.domain

import java.time.LocalDate

/** CSV قابل‌خواندن برای Excel: UTF-8 با BOM تا فارسی درست باز شود؛ مثل `csv.writer` پایتون (پایان خط CRLF). */
object Csv {
    fun encode(header: List<String>, rows: List<List<String>>): ByteArray {
        val sb = StringBuilder()
        fun line(cells: List<String>) {
            sb.append(cells.joinToString(",") { quote(it) }).append("\r\n")
        }
        line(header)
        rows.forEach(::line)
        return byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun quote(s: String): String =
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
}

object BackupFormat {
    const val DB_ENTRY = "board.db"
    const val README_ENTRY = "README.txt"
    const val INFO_ENTRY = "backup.json"
    const val FILES_PREFIX = ""

    /** `board-data-14050825.zip`؛ تاریخ شمسی فشرده. */
    fun dataName(day: LocalDate) = "board-data-${stamp(day)}.zip"

    fun filesName(day: LocalDate) = "board-files-${stamp(day)}.zip"

    private fun stamp(day: LocalDate): String {
        val j = Jalali.toJalali(day)
        return "%04d%02d%02d".format(j.year, j.month, j.day)
    }

    val README = """
        پشتیبان دفتر

        board.db  ← پایگاه‌داده‌ی کامل (SQLite).
        customers.csv، cases.csv، tasks.csv، notes.csv ← نسخه‌ی قابل‌خواندن (با Excel باز می‌شود).
        عکس‌ها، صداها و فایل‌ها در پشتیبان جدای «فایل‌ها» (board-files-…) هستند؛ هر دو را با هم نگه دارید.

        بازگرداندن: در برنامه‌ی دفتر ← تنظیمات ← «بازیابی از پشتیبان…» و همین فایل ZIP را انتخاب کنید
        (هر کدام از دو ZIP را می‌پذیرد). دادهٔ فعلی پیش از جایگزینی کنار گذاشته می‌شود.
    """.trimIndent() + "\n"
}

/** وضعیت آخرین پشتیبان برای تنظیمات. */
object BackupStatus {
    /** null = هنوز نگرفته‌اید. */
    fun daysSince(lastIso: String?, today: LocalDate): Int? =
        lastIso?.let { runCatching { java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(it), today).toInt().coerceAtLeast(0) }.getOrNull() }

    fun text(days: Int?): String = when {
        days == null -> "هنوز نسخه‌ای نگرفته‌اید."
        days == 0 -> "✓ امروز یک نسخه گرفته‌اید."
        days <= 7 -> Digits.fa("آخرین نسخه: $days روز پیش ✓")
        else -> Digits.fa("آخرین نسخه: $days روز پیش")
    }
}
