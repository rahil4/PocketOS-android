package app.pocketos.board.domain

/** مقصدی که لمسِ یک ردیف پاسخ باز می‌کند. */
sealed interface NavTarget {
    data class Case(val id: Long) : NavTarget
    data class Customer(val id: Long) : NavTarget
    data object Inbox : NavTarget
    data class Tag(val tag: String) : NavTarget
    data class Search(val q: String) : NavTarget
}

data class ListItem(val label: String, val sub: String, val nav: NavTarget)

/** عملی که با «ثبت» انجام می‌شود. */
sealed interface ConfirmAction {
    data class Task(val customerId: Long?, val title: String, val due: java.time.LocalDate, val repeat: String?) : ConfirmAction
    data class Note(val customerId: Long, val text: String) : ConfirmAction
}

/** چیزی که «بازگردانی» حذف می‌کند. */
sealed interface Undo {
    data class Task(val id: Long) : Undo
    data class Item(val id: Long) : Undo
}

/** یک حباب پاسخ دستیار. */
sealed interface Block {
    data class Text(val text: String) : Block
    data class Help(val text: String, val examples: List<String>) : Block
    data class Items(val title: String, val items: List<ListItem>) : Block
    data class CustomerCard(val name: String, val phone: String, val sub: String, val customerId: Long) : Block
    data class Choices(val text: String, val options: List<Option>) : Block {
        data class Option(val label: String, val say: String)
    }
    data class Confirm(val text: String, val lines: List<String>, val action: ConfirmAction) : Block
    data class Done(val text: String, val undo: Undo?) : Block
    data class Link(val text: String, val nav: NavTarget) : Block
}

object AssistantTexts {
    const val GREETING = "سلام! بپرسید («کارهای امروز»، «رنجبر») یا با یک جمله ثبت کنید («فردا برای رنجبر تحویل نقشه»)."
    val SUGGESTIONS = listOf("کارهای امروز", "کارهای فردا", "کی کار دارد؟", "پرونده‌های باز", "راهنما")
    val EXAMPLES = listOf("کارهای امروز", "کارهای فردا", "این هفته", "کی کار دارد؟", "پرونده‌های باز", "کارهای عقب‌افتاده") +
        listOf("رنجبر", "تلفن رنجبر", "فردا برای رنجبر تحویل نقشه", "بنویس برای رنجبر: شماره سند ۱۲۳۴۵")
    const val MAX_LENGTH = 500
}
