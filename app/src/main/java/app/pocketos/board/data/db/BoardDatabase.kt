package app.pocketos.board.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        CustomerEntity::class,
        CaseEntity::class,
        ItemEntity::class,
        TaskEntity::class,
        SimilarNoEntity::class,
        PrefEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class BoardDatabase : RoomDatabase() {
    companion object {
        const val NAME = "board.db"
    }
}
