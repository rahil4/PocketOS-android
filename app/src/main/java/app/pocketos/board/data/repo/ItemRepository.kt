package app.pocketos.board.data.repo

import androidx.room.withTransaction
import app.pocketos.board.data.FileStore
import app.pocketos.board.data.StoredFile
import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.db.ItemEntity
import app.pocketos.board.data.db.KindCount
import app.pocketos.board.domain.Normalize
import kotlinx.coroutines.flow.Flow
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

object ItemKind {
    const val TEXT = "text"
    const val PHOTO = "photo"
    const val AUDIO = "audio"
    const val FILE = "file"
    const val PLACE = "place"
    const val EVENT = "event"
}

@Singleton
class ItemRepository @Inject constructor(
    private val db: BoardDatabase,
    private val clock: Clock,
    private val files: FileStore,
) {
    private val items = db.itemDao()
    private val cases = db.caseDao()
    private val customers = db.customerDao()

    fun observePinned(customerId: Long?, caseId: Long?): Flow<List<ItemEntity>> = items.observePinned(customerId, caseId)

    fun observeStream(customerId: Long?, caseId: Long?, kind: String?, limit: Int): Flow<List<ItemEntity>> =
        items.observeStream(customerId, caseId, kind, limit)

    fun observeKindCounts(customerId: Long?, caseId: Long?): Flow<List<KindCount>> = items.observeKindCounts(customerId, caseId)

    fun observeInbox(): Flow<List<ItemEntity>> = items.observeInbox()

    fun observeInboxCount(): Flow<Int> = items.observeInboxCount()

    /** پرونده متقاضی را تعیین می‌کند: caseId پر ⇒ customerId همان متقاضیِ پرونده. */
    internal suspend fun resolve(target: Target): Target {
        val caseId = target.caseId ?: return target
        val c = cases.get(caseId) ?: return Target(target.customerId, null)
        return Target(c.customerId, caseId)
    }

    internal suspend fun touch(target: Target, now: Long) {
        target.customerId?.let { customers.touch(it, now) }
        target.caseId?.let { cases.touch(it, now) }
    }

    suspend fun add(
        target: Target,
        kind: String,
        text: String = "",
        file: StoredFile? = null,
        lat: Double? = null,
        lng: Double? = null,
    ): Long = db.withTransaction {
        val t = resolve(target)
        val now = clock.millis()
        val clean = text.trim()
        val id = items.insert(
            ItemEntity(
                kind = kind, customerId = t.customerId, caseId = t.caseId, text = clean,
                fileName = file?.name, filePath = file?.path, mime = file?.mime, lat = lat, lng = lng,
                searchText = Normalize.key("$clean ${file?.name.orEmpty()}"), createdAt = now,
            ),
        )
        touch(t, now)
        id
    }

    suspend fun updateText(id: Long, text: String) = db.withTransaction {
        val i = items.get(id) ?: return@withTransaction
        val clean = text.trim()
        items.update(i.copy(text = clean, searchText = Normalize.key("$clean ${i.fileName.orEmpty()}")))
    }

    suspend fun setPinned(id: Long, on: Boolean) = items.setPinned(id, if (on) clock.millis() else null)

    suspend fun delete(id: Long): Long {
        val ts = clock.millis()
        items.setDeleted(id, ts)
        return ts
    }

    suspend fun restore(id: Long) = items.setDeleted(id, null)

    suspend fun move(id: Long, target: Target) = db.withTransaction {
        val t = resolve(target)
        items.setTarget(id, t.customerId, t.caseId)
        touch(t, clock.millis())
    }

    /** فایلی که بعد از ثبت لازم نشد (مثلاً عکس لغوشدهٔ دوربین). */
    fun discard(file: StoredFile) = files.delete(file.path)
}
