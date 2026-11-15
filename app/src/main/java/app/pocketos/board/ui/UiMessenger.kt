package app.pocketos.board.ui

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/** پیام کوتاه پایین صفحه با «بازگردانی»؛ بعد از بسته شدن صفحه هم کار می‌کند. */
data class UiMessage(val text: String, val actionLabel: String? = null, val action: (suspend () -> Unit)? = null)

@Singleton
class UiMessenger @Inject constructor() {
    private val _events = MutableSharedFlow<UiMessage>(extraBufferCapacity = 8)
    val events: SharedFlow<UiMessage> = _events

    /** بازگردانی روی این scope اجرا می‌شود، نه scope صفحه‌ای که شاید بسته شده باشد. */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    fun show(text: String, actionLabel: String? = null, action: (suspend () -> Unit)? = null) {
        _events.tryEmit(UiMessage(text, actionLabel, action))
    }
}
