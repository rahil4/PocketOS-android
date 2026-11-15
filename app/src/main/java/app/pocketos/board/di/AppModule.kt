package app.pocketos.board.di

import android.content.Context
import androidx.room.Room
import app.pocketos.board.data.db.BoardDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): BoardDatabase =
        Room.databaseBuilder(context, BoardDatabase::class.java, BoardDatabase.NAME).build()

    /** ساعت قابل‌تعویض تا منطق وابسته به «امروز» آزمون‌پذیر بماند. */
    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()
}
