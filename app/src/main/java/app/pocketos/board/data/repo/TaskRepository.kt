package app.pocketos.board.data.repo

import androidx.room.withTransaction
import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.db.ItemEntity
import app.pocketos.board.data.db.TaskEntity
import app.pocketos.board.domain.CaseStatus
import app.pocketos.board.domain.Normalize
import app.pocketos.board.domain.Recurrence
import app.pocketos.board.domain.Tags
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** نتیجهٔ «انجام شد»: نوبت بعدیِ ساخته‌شده، و پروندهٔ پیشنهادی برای «نهایی شود؟». */
data class DoneResult(val nextTaskId: Long? = null, val askFinishCaseId: Long? = null)

@Singleton
class TaskRepository @Inject constructor(
    private val db: BoardDatabase,
    private val clock: Clock,
    private val itemRepo: ItemRepository,
) {
    private val tasks = db.taskDao()
    private val items = db.itemDao()
    private val cases = db.caseDao()

    fun observeOpenByCustomer(customerId: Long): Flow<List<TaskEntity>> = tasks.observeOpenByCustomer(customerId)

    fun observeOpenByCase(caseId: Long): Flow<List<TaskEntity>> = tasks.observeOpenByCase(caseId)

    private fun today(): LocalDate = LocalDate.now(clock)

    private fun parse(day: String?): LocalDate? = day?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    suspend fun create(target: Target, title: String, due: LocalDate?, repeat: String? = null): Long = db.withTransaction {
        val t = itemRepo.resolve(target)
        val now = clock.millis()
        val clean = Normalize.clean(title)
        require(clean.isNotEmpty()) { "عنوان خالی است" }
        val id = tasks.insert(
            TaskEntity(
                customerId = t.customerId, caseId = t.caseId, title = clean, dueDay = due?.toString(),
                repeat = repeat, searchText = Normalize.key(clean), createdAt = now,
            ),
        )
        itemRepo.touch(t, now)
        id
    }

    /** «انجام شد»: رویداد سابقه، و برای کار تکراری نوبت بعدی روی برنامهٔ ثابت. تکراری‌بودنِ فراخوانی بی‌اثر است. */
    suspend fun done(id: Long): DoneResult = db.withTransaction {
        val t = tasks.get(id) ?: return@withTransaction DoneResult()
        if (t.doneAt != null || t.deletedAt != null) return@withTransaction DoneResult()
        val now = clock.millis()
        tasks.update(t.copy(doneAt = now))
        items.insert(
            ItemEntity(
                kind = ItemKind.EVENT, customerId = t.customerId, caseId = t.caseId,
                text = "کار انجام شد: ${t.title}", searchText = Normalize.key("کار انجام شد: ${t.title}"),
                createdAt = now, clientId = "done:$id",
            ),
        )
        var next: Long? = null
        val due = parse(t.dueDay)
        if (t.repeat != null && due != null) {
            Recurrence.nextAfterDone(due, parse(t.anchor), t.repeat, today())?.let { nd ->
                next = tasks.insert(t.copy(id = 0, dueDay = nd.toString(), anchor = null, doneAt = null, spawnedBy = id, createdAt = now))
            }
        }
        itemRepo.touch(Target(t.customerId, t.caseId), now)
        var ask: Long? = null
        if (t.caseId != null && tasks.countOpenInCase(t.caseId) == 0) {
            val c = cases.get(t.caseId)
            if (c != null && c.deletedAt == null && CaseStatus.of(c.status).open) ask = c.id
        }
        DoneResult(next, ask)
    }

    /** برداشتن «انجام شد»: رویداد و نوبتِ دست‌نخوردهٔ ساخته‌شده حذف می‌شود. */
    suspend fun undone(id: Long) = db.withTransaction {
        val t = tasks.get(id) ?: return@withTransaction
        tasks.update(t.copy(doneAt = null))
        items.deleteByClientId("done:$id")
        tasks.deleteSpawnedUntouched(id)
    }

    /** فقط همین نوبت به [day] می‌رود (برای تکراری، برنامهٔ اصلی در anchor می‌ماند). روز قبلی را برمی‌گرداند. */
    suspend fun setDay(id: Long, day: LocalDate): LocalDate? = db.withTransaction {
        val t = tasks.get(id) ?: return@withTransaction null
        val old = parse(t.dueDay)
        val anchor = Recurrence.anchorAfterMove(old, parse(t.anchor), day, t.repeat)
        tasks.update(t.copy(dueDay = day.toString(), anchor = anchor?.toString()))
        itemRepo.touch(Target(t.customerId, t.caseId), clock.millis())
        old
    }

    suspend fun edit(id: Long, title: String, due: LocalDate?, repeat: String?) = db.withTransaction {
        val t = tasks.get(id) ?: return@withTransaction
        val clean = Normalize.clean(title).ifEmpty { t.title }
        val changedSchedule = t.dueDay != due?.toString() || t.repeat != repeat
        tasks.update(
            t.copy(
                title = clean, dueDay = due?.toString(), repeat = repeat, searchText = Normalize.key(clean),
                anchor = if (changedSchedule) null else t.anchor,
            ),
        )
    }

    suspend fun stopRepeat(id: Long) = db.withTransaction {
        val t = tasks.get(id) ?: return@withTransaction
        tasks.update(t.copy(repeat = null, anchor = null))
    }

    suspend fun delete(id: Long): Long {
        val ts = clock.millis()
        tasks.setDeleted(id, ts)
        return ts
    }

    suspend fun restore(id: Long) = tasks.setDeleted(id, null)

    suspend fun move(id: Long, target: Target) = db.withTransaction {
        val t = itemRepo.resolve(target)
        tasks.setTarget(id, t.customerId, t.caseId)
        itemRepo.touch(t, clock.millis())
    }

    /** تبدیل یادداشت به کار با همان مشتری/پرونده؛ یادداشت نرم حذف می‌شود. */
    suspend fun fromNote(itemId: Long, title: String, due: LocalDate?, repeat: String?): Long = db.withTransaction {
        val item = items.get(itemId) ?: error("یادداشت پیدا نشد")
        val id = create(Target(item.customerId, item.caseId), title, due, repeat)
        itemRepo.delete(itemId)
        id
    }

    suspend fun undoFromNote(taskId: Long, itemId: Long) = db.withTransaction {
        tasks.setDeleted(taskId, clock.millis())
        itemRepo.restore(itemId)
    }

    /** تگ‌های کار (برای تست و صفحهٔ برچسب). */
    fun tagsOf(t: TaskEntity): List<String> = Tags.tagsIn(t.title)
}
