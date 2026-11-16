package app.pocketos.board.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pocketos.board.data.repo.AssistantRepository
import app.pocketos.board.domain.AssistantTexts
import app.pocketos.board.domain.Block
import app.pocketos.board.domain.ConfirmAction
import app.pocketos.board.domain.Undo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** یک حباب گفتگو: پیام کاربر، پاسخ دستیار، یا «…» (در حال پاسخ). */
data class Msg(val id: Long, val fromUser: Boolean, val text: String? = null, val block: Block? = null, val busy: Boolean = false)

/** گفتگو تا وقتی برنامه باز است می‌ماند؛ ذخیره نمی‌شود (مثل وب‌اپ). */
@Singleton
class ChatSession @Inject constructor() {
    private var next = 2L
    val messages = MutableStateFlow(listOf(Msg(1, false, text = AssistantTexts.GREETING)))

    fun add(m: (id: Long) -> Msg): Long {
        val id = next++
        messages.value = messages.value + m(id)
        return id
    }

    fun remove(id: Long) { messages.value = messages.value.filterNot { it.id == id } }

    fun replace(id: Long, with: (Long) -> Msg) {
        messages.value = messages.value.flatMap { if (it.id == id) listOf(with(next++)) else listOf(it) }
    }
}

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val session: ChatSession,
    private val repo: AssistantRepository,
) : ViewModel() {
    val messages: StateFlow<List<Msg>> = session.messages
    private val pending = HashSet<Long>()

    fun say(raw: String) {
        val text = raw.trim().take(AssistantTexts.MAX_LENGTH)
        if (text.isEmpty()) return
        session.add { Msg(it, true, text = text) }
        val wait = session.add { Msg(it, false, text = "…", busy = true) }
        viewModelScope.launch {
            val replies = try {
                repo.reply(text)
            } catch (_: Exception) {
                null
            }
            session.remove(wait)
            if (replies == null) session.add { Msg(it, false, text = "انجام نشد؛ دوباره تلاش کنید.") }
            else replies.forEach { b -> session.add { Msg(it, false, block = b) } }
        }
    }

    /** «ثبت»: دوبار فشردن دوبار نمی‌سازد. */
    fun confirm(msgId: Long, action: ConfirmAction) {
        if (!pending.add(msgId)) return
        viewModelScope.launch {
            try {
                val done = repo.confirm(action)
                session.remove(msgId)
                session.add { Msg(it, false, block = done) }
            } catch (_: Exception) {
                pending.remove(msgId)
                session.add { Msg(it, false, text = "ثبت نشد؛ دوباره تلاش کنید.") }
            }
        }
    }

    fun cancel(msgId: Long) {
        session.remove(msgId)
        session.add { Msg(it, false, text = "لغو شد.") }
    }

    fun undo(msgId: Long, undo: Undo) {
        if (!pending.add(msgId)) return
        viewModelScope.launch {
            repo.undo(undo)
            session.replace(msgId) { Msg(it, false, text = "بازگردانی شد.") }
        }
    }
}
