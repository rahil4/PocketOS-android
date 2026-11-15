package app.pocketos.board.data.repo

import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.db.PrefEntity
import javax.inject.Inject
import javax.inject.Singleton

/** تنظیمات ساده کلید ← مقدار. */
@Singleton
class PrefRepository @Inject constructor(db: BoardDatabase) {
    private val dao = db.prefDao()

    suspend fun getBool(key: String): Boolean = dao.get(key) == "1"

    suspend fun setBool(key: String, value: Boolean) = dao.put(PrefEntity(key, if (value) "1" else "0"))

    companion object {
        /** «پرونده هم نهایی شود؟» دیگر پرسیده نشود. */
        const val NO_FINISH_ASK = "no_finish_ask"
    }
}
