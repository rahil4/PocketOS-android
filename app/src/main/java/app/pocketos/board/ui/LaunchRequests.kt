package app.pocketos.board.ui

import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** درخواست‌هایی که از بیرون اپ می‌رسند (اعلان، ویجت، اشتراک) و رابط باید اجرا کند. */
@Singleton
class LaunchRequests @Inject constructor() {
    /** مسیر مقصد: home | evening | customers | calendar | settings */
    val route = MutableStateFlow<String?>(null)

    /** «＋ یادداشت» ویجت: پنجرهٔ یادداشت روی خانه باز شود. */
    val quickNote = MutableStateFlow(false)
}
