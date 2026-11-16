package app.pocketos.board.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhoneFeaturesTest {
    private val today = LocalDate.of(2026, 11, 15)

    /** رمزگذاری QUOTED-PRINTABLE مثل اندروید. */
    private fun qp(s: String, charset: String = "UTF-8"): String =
        s.toByteArray(java.nio.charset.Charset.forName(charset)).joinToString("") { b ->
            val v = b.toInt() and 0xFF
            if (v in 0x21..0x7E && v != '='.code) v.toChar().toString() else "=%02X".format(v)
        }

    @Test
    fun simpleVcard30() {
        val v = "BEGIN:VCARD\nVERSION:3.0\nFN:آقای رنجبر\nN:رنجبر;علی;;;\nTEL;TYPE=CELL:+98 912 123 4567\nEND:VCARD\n"
        assertEquals(listOf(Contact("آقای رنجبر", "09121234567")), Vcard.parse(v))
    }

    @Test
    fun usesNWhenNoFnGivenThenFamily() {
        val v = "BEGIN:VCARD\r\nVERSION:2.1\r\nN:رنجبر;علی;;;\r\nTEL:09351112233\r\nEND:VCARD\r\n"
        assertEquals(listOf(Contact("علی رنجبر", "09351112233")), Vcard.parse(v))
    }

    @Test
    fun androidQuotedPrintableUtf8() {
        val v = "BEGIN:VCARD\r\nVERSION:2.1\r\nN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:${qp("رنجبر")};${qp("علی")};;;\r\n" +
            "FN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:${qp("علی رنجبر")}\r\nTEL;CELL:0912-123-4567\r\nEND:VCARD\r\n"
        assertEquals(listOf(Contact("علی رنجبر", "09121234567")), Vcard.parse(v))
    }

    @Test
    fun quotedPrintableSoftLineBreak() {
        val name = qp("محمدرضا کریمی")
        val half = name.length / 2
        val v = "BEGIN:VCARD\nVERSION:2.1\nFN;CHARSET=UTF-8;ENCODING=QUOTED-PRINTABLE:${name.substring(0, half)}=\n${name.substring(half)}\nTEL:09120000000\nEND:VCARD"
        assertEquals("محمدرضا کریمی", Vcard.parse(v).single().name)
    }

    @Test
    fun windows1256Charset() {
        val v = "BEGIN:VCARD\nVERSION:2.1\nFN;CHARSET=windows-1256;ENCODING=QUOTED-PRINTABLE:${qp("علي كريمي", "windows-1256")}\nTEL:09120000000\nEND:VCARD"
        assertEquals("علی کریمی", Vcard.parse(v).single().name)
    }

    @Test
    fun foldedLinesMultipleCardsAndNoNameSkipped() {
        val v = """
            BEGIN:VCARD
            FN:اول
             ادامه
            TEL:09121111111
            END:VCARD
            BEGIN:VCARD
            TEL:09122222222
            END:VCARD
            begin:vcard
            FN:سوم
            end:vcard
        """.trimIndent()
        val l = Vcard.parse(v)
        assertEquals(listOf("اولادامه", "سوم"), l.map { it.name }.let { it }) // خط ادامه‌دار بدون فاصله می‌چسبد
        assertEquals("", l[1].phone)
        assertTrue(Vcard.looksLikeVcard("x\nBEGIN:VCARD"))
        assertFalse(Vcard.looksLikeVcard("متن عادی"))
    }

    @Test
    fun capsAtTwoHundred() {
        val v = (1..250).joinToString("\n") { "BEGIN:VCARD\nFN:م$it\nEND:VCARD" }
        assertEquals(200, Vcard.parse(v).size)
    }

    @Test
    fun morningAndEveningWindows() {
        assertTrue(Reminders.morningDue(true, 8, 8, null, today))
        assertTrue(Reminders.morningDue(true, 8, 12, null, today))
        assertFalse(Reminders.morningDue(true, 8, 7, null, today))
        assertFalse(Reminders.morningDue(true, 8, 13, null, today))
        assertFalse(Reminders.morningDue(true, 8, 9, today.toString(), today)) // امروز فرستاده شد
        assertFalse(Reminders.morningDue(false, 8, 9, null, today))

        assertTrue(Reminders.eveningDue(true, 19, 19, null, null, today))
        assertTrue(Reminders.eveningDue(true, 19, 22, null, null, today))
        assertFalse(Reminders.eveningDue(true, 19, 23, null, null, today))
        assertFalse(Reminders.eveningDue(true, 19, 20, null, today.toString(), today)) // روز بسته شده
        assertFalse(Reminders.eveningDue(true, 0, 20, null, null, today)) // خاموش
        assertFalse(Reminders.eveningDue(false, 19, 20, null, null, today))
    }

    @Test
    fun morningPayloadTexts() {
        val rows = listOf(
            ReminderTask("تحویل نقشه #اداره", today, "آقای رنجبر"),
            ReminderTask("تماس دربارهٔ سند", today, "آقای رنجبر"),
            ReminderTask("پیگیری", today, "خانم محمدی"),
            ReminderTask("قدیمی", today.minusDays(2), "خانم محمدی"),
        )
        val p = Reminders.morning(rows, today, names = true)!!
        assertEquals("امروز ۳ کار داری · ۱ مانده", p.title)
        assertEquals("آقای رنجبر: تحویل نقشه · تماس دربارهٔ سند\nخانم محمدی: پیگیری", p.body)
        assertEquals("home", p.route)

        val anon = Reminders.morning(rows, today, names = false)!!
        assertEquals("برای دیدن، برنامه را باز کنید", anon.body)

        val onlyLate = Reminders.morning(listOf(ReminderTask("قدیمی", today.minusDays(1), null)), today, true)!!
        assertEquals("۱ کار مانده داری", onlyLate.title)
        assertEquals("برای دیدن، برنامه را باز کنید", onlyLate.body) // امروزی نیست ← متن کلی

        assertNull(Reminders.morning(emptyList(), today, true))
    }

    @Test
    fun morningBodyCapsAtThreeCustomersAndTwoTitles() {
        val rows = (1..5).flatMap { c -> (1..3).map { t -> ReminderTask("کار$t", today, "مشتری$c") } }
        val lines = Reminders.morning(rows, today, true)!!.body.lines()
        assertEquals(3, lines.size)
        assertEquals("مشتری۱: کار۱ · کار۲", lines[0].replace("1", "۱"))
    }

    @Test
    fun eveningPayloadAndSettings() {
        val e = Reminders.evening(2)
        assertEquals("روز را ببند و سبک شو", e.title)
        assertEquals("۲ کار هنوز باز است؛ یک لمس برای تصمیم.", e.body)
        assertEquals("evening", e.route)
        assertEquals(0.94f, AppSettings(fsize = "sm").scale)
        assertEquals(1f, AppSettings().scale)
        assertEquals(1.25f, AppSettings(fsize = "xl").scale)
        assertEquals(19, AppSettings().eveningHour)
    }
}
