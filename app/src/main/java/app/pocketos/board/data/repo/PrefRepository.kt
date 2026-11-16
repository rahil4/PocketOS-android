package app.pocketos.board.data.repo

import app.pocketos.board.data.db.BoardDatabase
import app.pocketos.board.data.db.PrefEntity
import javax.inject.Inject
import javax.inject.Singleton

/** تنظیمات ساده کلید ← مقدار. */
@Singleton
class PrefRepository @Inject constructor(db: BoardDatabase) {
    private val dao = db.prefDao()

    suspend fun getString(key: String): String? = dao.get(key)

    suspend fun setString(key: String, value: String) = dao.put(PrefEntity(key, value))

    suspend fun getBool(key: String): Boolean = dao.get(key) == "1"

    suspend fun setBool(key: String, value: Boolean) = dao.put(PrefEntity(key, if (value) "1" else "0"))

    companion object {
        /** «پرونده هم نهایی شود؟» دیگر پرسیده نشود. */
        const val NO_FINISH_ASK = "no_finish_ask"

        /** پروندهٔ/مشتریِ فعال برای ثبت از خانه. */
        const val ACTIVE_CUSTOMER = "active_customer"
        const val ACTIVE_CASE = "active_case"

        /** روزی که «روز بسته شد» و ساعت پیشنهاد. */
        const val CLOSED_ON = "closed_on"
        const val EVENING_HOUR = "evening_hour"

        /** آخرین نمای تقویم. */
        const val CAL_VIEW = "cal_view"
    }
}
