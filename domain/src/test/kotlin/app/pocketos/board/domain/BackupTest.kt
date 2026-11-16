package app.pocketos.board.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupTest {
    @Test
    fun csvHasBomCrlfAndQuoting() {
        val bytes = Csv.encode(listOf("شناسه", "نام"), listOf(listOf("1", "علی, رضا"), listOf("2", "او \"گفت\""), listOf("3", "خط۱\nخط۲")))
        assertContentEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), bytes.copyOfRange(0, 3))
        val text = String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        assertEquals("شناسه,نام\r\n1,\"علی, رضا\"\r\n2,\"او \"\"گفت\"\"\"\r\n3,\"خط۱\nخط۲\"\r\n", text)
        assertEquals("\r\n", String(Csv.encode(emptyList(), emptyList()), 3, 2, Charsets.UTF_8)) // فقط یک خط خالی
    }

    @Test
    fun fileNamesUseCompactJalaliDate() {
        val d = LocalDate.of(2026, 11, 16) // ۲۵ آبان ۱۴۰۵
        assertEquals("board-data-14050825.zip", BackupFormat.dataName(d))
        assertEquals("board-files-14050825.zip", BackupFormat.filesName(d))
        assertEquals("board-data-14050102.zip", BackupFormat.dataName(Jalali.fromJalali(1405, 1, 2)))
    }

    @Test
    fun statusTexts() {
        val today = LocalDate.of(2026, 11, 16)
        assertNull(BackupStatus.daysSince(null, today))
        assertNull(BackupStatus.daysSince("خراب", today))
        assertEquals(0, BackupStatus.daysSince(today.toString(), today))
        assertEquals(3, BackupStatus.daysSince(today.minusDays(3).toString(), today))
        assertEquals("هنوز نسخه‌ای نگرفته‌اید.", BackupStatus.text(null))
        assertEquals("✓ امروز یک نسخه گرفته‌اید.", BackupStatus.text(0))
        assertEquals("آخرین نسخه: ۳ روز پیش ✓", BackupStatus.text(3))
        assertEquals("آخرین نسخه: ۷ روز پیش ✓", BackupStatus.text(7))
        assertEquals("آخرین نسخه: ۹ روز پیش", BackupStatus.text(9))
    }

    @Test
    fun fridayBackupReminder() {
        val fri = LocalDate.of(2026, 11, 20) // جمعه
        assertEquals(java.time.DayOfWeek.FRIDAY, fri.dayOfWeek)
        assertTrue(Reminders.backupDue(true, fri, 8, 8, null, null))
        assertTrue(Reminders.backupDue(true, fri, 12, 8, null, 7))
        assertFalse(Reminders.backupDue(true, fri, 13, 8, null, 30)) // خارج از پنجره
        assertFalse(Reminders.backupDue(true, fri, 9, 8, null, 6)) // تازه گرفته
        assertFalse(Reminders.backupDue(true, fri, 9, 8, fri.toString(), 30)) // امروز یادآوری شد
        assertFalse(Reminders.backupDue(false, fri, 9, 8, null, 30))
        assertFalse(Reminders.backupDue(true, fri.plusDays(1), 9, 8, null, 30)) // شنبه
        assertEquals("هنوز نسخه‌ای نگرفته‌اید", Reminders.backup(null).body)
        assertEquals("آخرین نسخه ۱۲ روز پیش بود", Reminders.backup(12).body)
        assertEquals("settings", Reminders.backup(12).route)
        assertTrue(AppSettings().backupRemind)
    }
}
