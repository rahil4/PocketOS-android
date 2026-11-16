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
        // اگر بازیابی از پشتیبان منتظر است، پیش از باز شدن پایگاه‌داده جایگزین می‌شود.
        Room.databaseBuilder(context, BoardDatabase::class.java, BoardDatabase.NAME)
            .also { app.pocketos.board.data.RestoreApplier.applyIfPending(context) }
            .build()

    @Provides
    @Singleton
    fun reminderSink(@ApplicationContext context: Context): app.pocketos.board.data.repo.ReminderSink =
        app.pocketos.board.system.AndroidNotifier(context)

    /** ساعت قابل‌تعویض تا منطق وابسته به «امروز» آزمون‌پذیر بماند. */
    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemDefaultZone()
}
