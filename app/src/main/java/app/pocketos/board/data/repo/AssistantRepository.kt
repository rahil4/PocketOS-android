package app.pocketos.board.data.repo

import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.db.CalTaskRow
import app.pocketos.board.domain.AssistCustomer
import app.pocketos.board.domain.AssistIntent
import app.pocketos.board.domain.Assistant
import app.pocketos.board.domain.AssistantTexts
import app.pocketos.board.domain.Block
import app.pocketos.board.domain.CaseStatus
import app.pocketos.board.domain.ConfirmAction
import app.pocketos.board.domain.Digits
import app.pocketos.board.domain.HomeLogic
import app.pocketos.board.domain.ListItem
import app.pocketos.board.domain.NavTarget
import app.pocketos.board.domain.Normalize
import app.pocketos.board.domain.Undo
import app.pocketos.board.domain.When
import kotlinx.coroutines.flow.first
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * اجرای «قصد»های دستیار روی داده‌ی گوشی و ساخت پاسخ (همان `chat_blocks` وب‌اپ).
 * خودش فقط می‌خواند؛ ثبت فقط با [confirm] (که کاربر تأیید کرده) و با [undo] قابل برگشت است.
 */
@Singleton
class AssistantRepository @Inject constructor(
    private val db: BoardDatabase,
    private val calendar: CalendarRepository,
    private val tags: TagRepository,
    private val tasks: TaskRepository,
    private val items: ItemRepository,
    private val clock: Clock,
) {
    private val taskDao = db.taskDao()

    private suspend fun customers(): List<AssistCustomer> =
        db.customerDao().allActive().sortedByDescending { it.lastActivity }.take(2000)
            .map { AssistCustomer.of(it.id, it.name, it.phone, it.archivedAt != null) }

    suspend fun reply(text: String): List<Block> {
        val today = LocalDate.now(clock)
        val intent = Assistant.interpret(text.take(AssistantTexts.MAX_LENGTH), today, customers())
        return blocks(intent, text, today)
    }

    private fun nav(caseId: Long?, customerId: Long?): NavTarget =
        caseId?.let { NavTarget.Case(it) } ?: customerId?.let { NavTarget.Customer(it) } ?: NavTarget.Inbox

    private fun taskItems(rows: List<CalTaskRow>, today: LocalDate): List<ListItem> = rows.map { r ->
        val due = r.task.dueDay?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ListItem(
            Digits.fa(r.task.title),
            listOfNotNull(r.customerName, r.caseTitle, due?.let { HomeLogic.taskNote(it, today) }).joinToString(" · "),
            nav(r.task.caseId, r.task.customerId),
        )
    }

    private suspend fun blocks(intent: AssistIntent, text: String, today: LocalDate): List<Block> = when (intent) {
        AssistIntent.Help, AssistIntent.Unknown ->
            listOf(Block.Help("می‌توانید بپرسید یا با یک جمله ثبت کنید:", AssistantTexts.EXAMPLES))

        is AssistIntent.TasksDay -> {
            val rows = taskDao.observeOpenRange(intent.day.toString(), intent.day.toString(), 30).first()
            if (rows.isEmpty()) listOf(Block.Text("برای ${intent.label} کاری نیست ✓"))
            else listOf(Block.Items(Digits.fa("${rows.size} کار برای ${intent.label}"), taskItems(rows, today)))
        }
        is AssistIntent.TasksRange -> {
            val rows = taskDao.observeOpenRange(intent.start.toString(), intent.end.toString(), 40).first()
            if (rows.isEmpty()) listOf(Block.Text("برای ${intent.label} کاری نیست ✓"))
            else listOf(Block.Items(Digits.fa("${rows.size} کار ${intent.label}"), taskItems(rows, today)))
        }
        AssistIntent.TasksLate -> {
            val rows = taskDao.observeOverdue(today.toString(), null).first().take(20)
            if (rows.isEmpty()) listOf(Block.Text("کار مانده‌ای نیست ✓"))
            else listOf(Block.Items(Digits.fa("${rows.size} کار مانده"), taskItems(rows, today)))
        }
        AssistIntent.WhoHasTasks -> {
            val rows = taskDao.openCountsByCustomer(12)
            if (rows.isEmpty()) listOf(Block.Text("هیچ متقاضی‌ای کار باز ندارد ✓"))
            else listOf(Block.Items("متقاضی‌هایی که کار دارند", rows.map { ListItem(it.name, Digits.fa("${it.n} کار"), NavTarget.Customer(it.id)) }))
        }
        AssistIntent.OpenCases -> {
            val rows = db.caseDao().openBrief(12)
            if (rows.isEmpty()) listOf(Block.Text("پرونده‌ی بازی نیست."))
            else listOf(
                Block.Items(
                    "پرونده‌های باز (تازه‌ترین)",
                    rows.map { ListItem(it.title, listOfNotNull(it.customerName, CaseStatus.of(it.status).label).joinToString(" · "), NavTarget.Case(it.id)) },
                ),
            )
        }
        AssistIntent.RecentNotes -> {
            val rows = db.itemDao().recentTextNotes(6)
            if (rows.isEmpty()) listOf(Block.Text("یادداشتی نیست."))
            else listOf(
                Block.Items(
                    "تازه‌ترین یادداشت‌ها",
                    rows.map { ListItem(Digits.fa(it.item.text.take(80)), it.customerName ?: "صندوق ورودی", nav(it.item.caseId, it.item.customerId)) },
                ),
            )
        }
        is AssistIntent.CustomerInfo -> customerBlock(intent.customerId, phoneOnly = false)
        is AssistIntent.Phone -> customerBlock(intent.customerId, phoneOnly = true)
        is AssistIntent.Choices -> {
            val tpl = intent.say
            val opts = intent.ids.mapNotNull { id -> db.customerDao().get(id)?.takeIf { it.deletedAt == null } }
                .map { Block.Choices.Option(it.name, tpl.replace("{name}", it.name)) }
            listOf(Block.Choices(intent.ask ?: "کدام متقاضی؟", opts))
        }
        is AssistIntent.Tag -> tagBlocks(intent.tag, today)
        is AssistIntent.NoCustomer -> listOf(Block.Text("متقاضی‌ای با «${intent.q}» پیدا نشد."))
        AssistIntent.NeedTitle -> listOf(Block.Text("چه کاری؟ مثلاً «فردا برای رنجبر تحویل نقشه»"))
        is AssistIntent.AddTask -> listOf(
            Block.Confirm(
                "این را ثبت کنم؟",
                listOf(
                    "کار · ${intent.label}" + (intent.repeat?.let { " · 🔁 ${When.repeatLabel(it)}" } ?: ""),
                    intent.customer?.let { "متقاضی: $it" } ?: "بدون متقاضی (صندوق ورودی)",
                    "«${intent.title}»",
                ),
                ConfirmAction.Task(intent.customerId, intent.title, intent.due, intent.repeat),
            ),
        )
        is AssistIntent.AddNote -> listOf(
            Block.Confirm(
                "این یادداشت را ثبت کنم؟",
                listOf("متقاضی: ${intent.customer}", "«${intent.text.take(300)}»"),
                ConfirmAction.Note(intent.customerId, intent.text.take(5000)),
            ),
        )
        is AssistIntent.Search -> {
            val key = Normalize.key(intent.q)
            val near = if (key.length >= 2) db.customerDao().allActive().filter { key in it.searchText }.sortedByDescending { it.lastActivity }.take(6) else emptyList()
            buildList {
                if (near.isNotEmpty()) add(Block.Choices("متقاضی‌های نزدیک:", near.map { Block.Choices.Option(it.name, it.name) }))
                add(Block.Link("جست‌وجوی «${intent.q}» در یادداشت‌ها و کارها ←", NavTarget.Search(intent.q)))
            }
        }
    }

    private suspend fun customerBlock(id: Long, phoneOnly: Boolean): List<Block> {
        val c = db.customerDao().get(id)?.takeIf { it.deletedAt == null } ?: return listOf(Block.Text("متقاضی پیدا نشد."))
        if (phoneOnly) {
            if (c.phone.isBlank()) return listOf(Block.Text("برای «${c.name}» شماره‌ای ثبت نشده."))
            return listOf(Block.CustomerCard(c.name, c.phone, "", c.id))
        }
        val nCases = db.caseDao().countOpenOfCustomer(id)
        val nTasks = taskDao.countOpenOfCustomer(id)
        return listOf(Block.CustomerCard(c.name, c.phone, Digits.fa("$nCases پرونده‌ی باز · $nTasks کار باز"), c.id))
    }

    private suspend fun tagBlocks(tag: String, today: LocalDate): List<Block> {
        val found = calendar.search("#$tag", today)
        val all = found.upcoming + found.past
        val open = all.filter { !it.item.done }
        val doneN = all.count { it.item.done }
        val notesN = tags.results(tag).notes.size
        val head = "#$tag · " + Digits.fa("${open.size} کار باز · $doneN انجام‌شده · $notesN یادداشت")
        val out = ArrayList<Block>()
        if (open.isNotEmpty()) {
            out += Block.Items(
                head,
                open.take(15).map { e ->
                    ListItem(
                        Digits.fa(e.item.title),
                        listOfNotNull(e.item.customerName, e.item.caseTitle, HomeLogic.taskNote(e.item.day, today)).joinToString(" · "),
                        nav(e.item.caseId, e.item.customerId),
                    )
                },
            )
        } else {
            out += Block.Text(if (all.isNotEmpty() || notesN > 0) head else "برچسب #$tag چیزی ندارد.")
        }
        if (all.isNotEmpty() || notesN > 0) out += Block.Link("همه‌ی نتایج #$tag ←", NavTarget.Tag(tag))
        return out
    }

    // ── ثبت و بازگردانی ──
    suspend fun confirm(action: ConfirmAction): Block.Done = when (action) {
        is ConfirmAction.Task -> {
            val target = action.customerId?.let { Target(it) } ?: Target.Inbox
            Block.Done("✓ ثبت شد", Undo.Task(tasks.create(target, action.title, action.due, action.repeat)))
        }
        is ConfirmAction.Note ->
            Block.Done("✓ ثبت شد", Undo.Item(items.add(Target(action.customerId), ItemKind.TEXT, action.text)))
    }

    suspend fun undo(u: Undo) {
        when (u) {
            is Undo.Task -> tasks.delete(u.id)
            is Undo.Item -> items.delete(u.id)
        }
    }
}
