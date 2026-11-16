package app.pocketos.board.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AssistantTest {
    private val today = LocalDate.of(2026, 11, 2)
    private val cs = listOf(
        AssistCustomer.of(1, "ابراهیم رنجبر", "0912"),
        AssistCustomer.of(2, "خانم محمدی"),
        AssistCustomer.of(3, "سارا کریمی"),
        AssistCustomer.of(4, "مریم کریمی"),
    )
    private fun f(s: String) = Assistant.interpret(s, today, cs)

    @Test
    fun queries() {
        assertEquals(AssistIntent.TasksDay(LocalDate.of(2026, 11, 3), "فردا"), f("کارهای فردا"))
        assertEquals(AssistIntent.TasksDay(today, "امروز"), f("کارهای امروز"))
        assertEquals(AssistIntent.TasksDay(LocalDate.of(2026, 11, 4), "پس‌فردا"), f("چه کاری دارم پس‌فردا"))
        assertEquals(AssistIntent.TasksRange(today, today.plusDays(6), "این هفته"), f("این هفته"))
        assertEquals(AssistIntent.WhoHasTasks, f("کی کار دارد؟"))
        assertEquals(AssistIntent.OpenCases, f("پرونده‌های باز"))
        assertEquals(AssistIntent.TasksLate, f("کارهای عقب‌افتاده"))
        assertEquals(AssistIntent.TasksLate, f("مانده‌ها"))
        assertEquals(AssistIntent.RecentNotes, f("یادداشت‌های جدید"))
        assertEquals(AssistIntent.Help, f("سلام"))
        assertEquals(AssistIntent.Help, f("   "))
        assertEquals(AssistIntent.Help, f("راهنما"))
    }

    @Test
    fun weekdayAndJalaliDayQueries() {
        val t = f("کارهای سه‌شنبه") as AssistIntent.TasksDay
        assertEquals(LocalDate.of(2026, 11, 3), t.day) // امروز دوشنبه است ← فردا سه‌شنبه
        val d = f("۲۸ آبان") as AssistIntent.TasksDay
        assertEquals(LocalDate.of(2026, 11, 19), d.day)
    }

    @Test
    fun customerAndPhone() {
        assertEquals(AssistIntent.CustomerInfo(1), f("رنجبر"))
        assertEquals(AssistIntent.CustomerInfo(1), f("آقای رنجبر"))
        assertEquals(AssistIntent.CustomerInfo(2), f("محمدی"))
        assertEquals(AssistIntent.Phone(1), f("تلفن رنجبر"))
        assertEquals(AssistIntent.Phone(2), f("شماره خانم محمدی"))
        val ch = f("تلفن کریمی") as AssistIntent.Choices
        assertEquals(setOf(3L, 4L), ch.ids.toSet())
        assertEquals("تلفن {name}", ch.say)
        assertEquals(AssistIntent.NoCustomer("ناشناس"), f("تلفن ناشناس"))
    }

    @Test
    fun addTaskWithCustomer() {
        val x = f("فردا برای رنجبر تحویل نقشه") as AssistIntent.AddTask
        assertEquals(1L, x.customerId)
        assertEquals("ابراهیم رنجبر", x.customer)
        assertEquals("تحویل نقشه", x.title)
        assertEquals(LocalDate.of(2026, 11, 3), x.due)
        assertEquals("فردا", x.label)
        assertEquals(null, x.repeat)
    }

    @Test
    fun addTaskRepeatAndNoCustomer() {
        val r = f("هر هفته برای خانم محمدی تماس") as AssistIntent.AddTask
        assertEquals("weekly", r.repeat)
        assertEquals(2L, r.customerId)
        assertEquals("تماس", r.title)
        val n = f("فردا تماس با دفتر") as AssistIntent.AddTask
        assertEquals(null, n.customerId) // مشتری نیافت ← بدون مشتری، ولی با تأیید
        assertEquals("تماس با دفتر", n.title)
        val tag = f("فردا تحویل #اداره") as AssistIntent.AddTask
        assertEquals("تحویل #اداره", tag.title)
    }

    @Test
    fun titleKeepsHalfSpaceAndPersianDigits() {
        val x = f("فردا برای رنجبر می‌رود ۱۲۳ بار") as AssistIntent.AddTask
        assertEquals("می‌رود ۱۲۳ بار", x.title)
    }

    @Test
    fun ambiguousCustomerAsksInsteadOfGuessing() {
        val ch = f("فردا برای کریمی تماس") as AssistIntent.Choices
        assertEquals(setOf(3L, 4L), ch.ids.toSet())
        assertTrue("{name}" in ch.say)
        assertEquals("فردا برای {name} تماس", ch.say)
        assertEquals("کدام مشتری؟", ch.ask)
        // با نام کامل دیگر مبهم نیست
        val full = f("فردا برای سارا کریمی تماس") as AssistIntent.AddTask
        assertEquals(3L, full.customerId)
        assertEquals("تماس", full.title)
    }

    @Test
    fun needTitle() {
        assertEquals(AssistIntent.NeedTitle, f("فردا برای رنجبر"))
    }

    @Test
    fun addNoteKeepsOriginalText() {
        val n = f("بنویس برای رنجبر: شماره سند ۱۲۳۴۵") as AssistIntent.AddNote
        assertEquals(1L, n.customerId)
        assertEquals("شماره سند ۱۲۳۴۵", n.text)
        val k = f("یادداشت کن برای کریمی: سلام") as AssistIntent.Choices
        assertEquals("بنویس برای {name}: سلام", k.say)
        assertEquals(AssistIntent.NoCustomer("ناشناس"), f("بنویس برای ناشناس: متن"))
        val multi = f("بنویس برای رنجبر: خط اول\nخط دوم") as AssistIntent.AddNote
        assertEquals("خط اول\nخط دوم", multi.text)
    }

    @Test
    fun tagsAndSearchFallbacks() {
        assertEquals(AssistIntent.Tag("اداره"), f("#اداره"))
        assertEquals(AssistIntent.Tag("اداره"), f("کارهای #اداره"))
        assertEquals(AssistIntent.Search("نقشه باغ"), f("نقشه باغ"))
        assertEquals(AssistIntent.Unknown, f("؟"))
        assertIs<AssistIntent.Search>(f("یک جملهٔ خیلی طولانی که هیچ‌چیز از آن نمی‌فهمد و باید جست‌وجو شود"))
    }

    @Test
    fun partialNameMatchesOnlyAfterPrepositionOrHonorific() {
        // «ابراهیم» نام کامل نیست ولی بعد از «برای» می‌آید ← همان مشتری
        val x = f("فردا برای ابراهیم تماس") as AssistIntent.AddTask
        assertEquals(1L, x.customerId)
        // بدون حرف اضافه، «کریمی» وسط عنوان نام مشتری حساب نمی‌شود
        val y = f("فردا تماس کریمی") as AssistIntent.AddTask
        assertEquals(null, y.customerId)
    }

    @Test
    fun tagsMatchTheWebRules() {
        assertEquals(listOf("اداره", "برداشت_زمین", "فوری"), Tags.tagsIn("تحویل نامه #اداره و #برداشت_زمین #اداره #12 (#فوری) a#b"))
        assertEquals("تحویل نامه به ثبت", Tags.strip("تحویل نامه #اداره  به ثبت"))
    }
}
