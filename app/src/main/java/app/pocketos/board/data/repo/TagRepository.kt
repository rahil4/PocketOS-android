package app.pocketos.board.data.repo

import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.db.ItemEntity
import app.pocketos.board.data.db.TaskEntity
import app.pocketos.board.domain.Normalize
import app.pocketos.board.domain.Tags
import javax.inject.Inject
import javax.inject.Singleton

data class TagResults(
    val openTasks: List<TaskEntity>,
    val notes: List<ItemEntity>,
    val doneTasks: List<TaskEntity>,
)

@Singleton
class TagRepository @Inject constructor(db: BoardDatabase) {
    private val items = db.itemDao()
    private val tasks = db.taskDao()

    /** همهٔ برچسب‌های استفاده‌شده با تعداد، پرتکرارتر اول (برای پیشنهاد هنگام تایپ). */
    suspend fun all(): List<Pair<String, Int>> {
        val counts = HashMap<String, Int>()
        (items.tagTexts() + tasks.tagTexts()).forEach { text ->
            Tags.tagsIn(text).forEach { counts[it] = (counts[it] ?: 0) + 1 }
        }
        return counts.entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }
    }

    /** جست‌وجوی دقیق برچسب (#اداره، نه #ادارهکل). */
    suspend fun results(tag: String): TagResults {
        val key = Normalize.key(tag).trimStart('#')
        val pattern = "%#$key%"
        val ts = tasks.searchLike(pattern).filter { Tags.hasTag(it.title, key) }
        val notes = items.searchLike(pattern).filter { it.kind != ItemKind.EVENT && Tags.hasTag(it.text, key) }
        return TagResults(ts.filter { it.doneAt == null }, notes, ts.filter { it.doneAt != null })
    }
}
