package app.pocketos.board.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

/** نسخهٔ ساختار پایگاه‌داده؛ پشتیبان‌گیری و بازیابی هم آن را می‌سنجند. */
const val DB_VERSION = 1

@Database(
    entities = [
        CustomerEntity::class,
        CaseEntity::class,
        ItemEntity::class,
        TaskEntity::class,
        SimilarNoEntity::class,
        PrefEntity::class,
    ],
    version = DB_VERSION,
    exportSchema = true,
)
abstract class BoardDatabase : RoomDatabase() {
    abstract fun customerDao(): CustomerDao
    abstract fun caseDao(): CaseDao
    abstract fun bulkDao(): BulkDao
    abstract fun itemDao(): ItemDao
    abstract fun taskDao(): TaskDao
    abstract fun prefDao(): PrefDao
    abstract fun treeDao(): TreeDao

    companion object {
        const val NAME = "board.db"
    }
}
