package app.pocketos.board

import android.app.Application
import app.pocketos.board.data.repo.TrashRepository
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class BoardApp : Application() {
    @Inject lateinit var trash: TrashRepository

    override fun onCreate() {
        super.onCreate()
        // هر چیزی که بیش از ۳۰ روز در سطل بوده برای همیشه پاک می‌شود.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { runCatching { trash.purge() } }
    }
}
