package app.pocketos.board.ui.stream

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pocketos.board.data.FileStore
import app.pocketos.board.data.LocationFetcher
import app.pocketos.board.data.StoredFile
import app.pocketos.board.data.db.CustomerEntity
import app.pocketos.board.data.db.ItemEntity
import app.pocketos.board.data.db.RecentCase
import app.pocketos.board.data.db.TaskEntity
import app.pocketos.board.data.repo.CaseRepository
import app.pocketos.board.data.repo.CustomerRepository
import app.pocketos.board.data.repo.ItemKind
import app.pocketos.board.data.repo.ItemRepository
import app.pocketos.board.data.repo.PrefRepository
import app.pocketos.board.data.repo.TagRepository
import app.pocketos.board.data.repo.Target
import app.pocketos.board.data.repo.TaskRepository
import app.pocketos.board.domain.CaseStatus
import app.pocketos.board.domain.DayLabel
import app.pocketos.board.domain.Tags
import app.pocketos.board.domain.When
import app.pocketos.board.ui.UiMessenger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

sealed interface StreamScope {
    data class Customer(val id: Long) : StreamScope
    data class Case(val id: Long) : StreamScope
    data object Inbox : StreamScope
}

data class StreamState(
    val tasks: List<TaskEntity> = emptyList(),
    val pinned: List<ItemEntity> = emptyList(),
    val items: List<ItemEntity> = emptyList(),
    val kinds: Map<String, Int> = emptyMap(),
    val hasMore: Boolean = false,
    val kind: String? = null,
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class StreamViewModel @Inject constructor(
    private val itemRepo: ItemRepository,
    private val taskRepo: TaskRepository,
    private val caseRepo: CaseRepository,
    private val customerRepo: CustomerRepository,
    private val tagRepo: TagRepository,
    private val prefs: PrefRepository,
    val files: FileStore,
    private val locations: LocationFetcher,
    private val ui: UiMessenger,
    clock: Clock,
) : ViewModel() {
    val today: LocalDate = LocalDate.now(clock)

    private val scope = MutableStateFlow<StreamScope?>(null)
    private val kind = MutableStateFlow<String?>(null)
    private val limit = MutableStateFlow(PAGE)

    /** مقصد ثبت؛ پیش‌فرض خودِ صفحه، با «ثبت در» عوض می‌شود. */
    val target = MutableStateFlow(Target.Inbox)

    /** پیشنهاد «پرونده هم نهایی شود؟» (شناسهٔ پرونده). */
    private val _finishAsk = MutableSharedFlow<Long>(extraBufferCapacity = 1)
    val finishAsk: SharedFlow<Long> = _finishAsk

    fun bind(s: StreamScope) {
        if (scope.value == s) return
        scope.value = s
        kind.value = null
        limit.value = PAGE
        target.value = when (s) {
            is StreamScope.Customer -> Target(s.id, null)
            is StreamScope.Case -> Target(null, s.id)
            StreamScope.Inbox -> Target.Inbox
        }
    }

    val state: StateFlow<StreamState> = scope.filterNotNull().flatMapLatest { s ->
        when (s) {
            StreamScope.Inbox -> itemRepo.observeInbox().map { StreamState(items = it, loaded = true) }
            else -> {
                val customerId = (s as? StreamScope.Customer)?.id
                val caseId = (s as? StreamScope.Case)?.id
                val tasks = if (customerId != null) taskRepo.observeOpenByCustomer(customerId) else taskRepo.observeOpenByCase(caseId!!)
                val stream = combine(kind, limit) { k, l -> k to l }.flatMapLatest { (k, l) ->
                    itemRepo.observeStream(customerId, caseId, k, l + 1).map { Triple(it, k, l) }
                }
                combine(tasks, itemRepo.observePinned(customerId, caseId), stream, itemRepo.observeKindCounts(customerId, caseId)) { t, p, st, c ->
                    val (rows, k, l) = st
                    StreamState(t, p, rows.take(l), c.associate { it.kind to it.n }, rows.size > l, k, loaded = true)
                }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StreamState())

    /** «ثبت در: …» */
    val targetLabel: StateFlow<String> = target.flatMapLatest { t ->
        when {
            t.caseId != null -> caseRepo.observe(t.caseId).flatMapLatest { c ->
                if (c == null) flowOf("صندوق ورودی")
                else customerRepo.observe(c.customerId).map { cu -> (cu?.name ?: "مشتری") + " › " + c.title }
            }
            t.customerId != null -> customerRepo.observe(t.customerId).map { it?.name ?: "مشتری" }
            else -> flowOf("صندوق ورودی")
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "…")

    val recentCases: StateFlow<List<RecentCase>> =
        caseRepo.observeRecent(6).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val customers: StateFlow<List<CustomerEntity>> =
        customerRepo.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setKind(k: String?) { kind.value = k; limit.value = PAGE }
    fun showMore() { limit.value += PAGE }

    suspend fun tagSuggestions(): List<String> = tagRepo.all().map { it.first }

    // ── ثبت ──
    private fun added(id: Long) = ui.show("ثبت شد", "بازگردانی") { itemRepo.delete(id) }

    fun saveNote(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch { added(itemRepo.add(target.value, ItemKind.TEXT, text)) }
    }

    /** متنِ دارای روز ← کار (با تکرار اگر در متن بود). */
    fun saveTaskFromText(text: String) {
        val cap = When.analyze(text, today) ?: return
        viewModelScope.launch {
            val id = taskRepo.create(target.value, cap.title, cap.due, cap.repeat)
            ui.show("کار برای ${DayLabel.of(cap.due, today)} ثبت شد", "بازگردانی") { taskRepo.delete(id) }
        }
    }

    fun addStored(f: StoredFile) {
        viewModelScope.launch { added(itemRepo.add(target.value, f.kind, file = f)) }
    }

    fun discard(f: StoredFile) = itemRepo.discard(f)

    fun importUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var last = 0L
            var failed = 0
            for (u in uris) {
                try {
                    val f = files.import(u)
                    last = itemRepo.add(target.value, f.kind, file = f)
                } catch (_: Exception) {
                    failed++
                }
            }
            if (last != 0L) added(last)
            if (failed > 0) ui.show("$failed فایل خوانده نشد")
        }
    }

    fun addLocation() {
        viewModelScope.launch {
            val p = locations.current()
            if (p == null) {
                ui.show("موقعیت پیدا نشد. GPS را روشن کنید.")
            } else {
                added(itemRepo.add(target.value, ItemKind.PLACE, text = "موقعیت من", lat = p.first, lng = p.second))
            }
        }
    }

    // ── کار ──
    fun done(t: TaskEntity) {
        viewModelScope.launch {
            val r = taskRepo.done(t.id)
            ui.show("«${Tags.strip(t.title)}» انجام شد", "بازگردانی") { taskRepo.undone(t.id) }
            r.askFinishCaseId?.let { if (!prefs.getBool(PrefRepository.NO_FINISH_ASK)) _finishAsk.tryEmit(it) }
        }
    }

    /** برداشتن «انجام شد» از یک کار (مثلاً در تقویم). */
    fun undone(t: TaskEntity) {
        viewModelScope.launch { taskRepo.undone(t.id) }
    }

    fun setDay(t: TaskEntity, day: LocalDate) {
        viewModelScope.launch {
            val old = taskRepo.setDay(t.id, day)
            ui.show("به ${DayLabel.of(day, today)} رفت", if (old != null) "بازگردانی" else null) {
                if (old != null) taskRepo.setDay(t.id, old)
            }
        }
    }

    fun editTask(t: TaskEntity, title: String, due: LocalDate?, repeat: String?) {
        viewModelScope.launch { taskRepo.edit(t.id, title, due, repeat) }
    }

    fun stopRepeat(t: TaskEntity) {
        viewModelScope.launch { taskRepo.stopRepeat(t.id) }
    }

    fun deleteTask(t: TaskEntity) {
        viewModelScope.launch {
            taskRepo.delete(t.id)
            ui.show("کار حذف شد", "بازگردانی") { taskRepo.restore(t.id) }
        }
    }

    fun moveTask(t: TaskEntity, to: Target) {
        viewModelScope.launch { taskRepo.move(t.id, to) }
    }

    // ── مورد ──
    fun pin(i: ItemEntity) {
        viewModelScope.launch { itemRepo.setPinned(i.id, i.pinnedAt == null) }
    }

    fun editNote(i: ItemEntity, text: String) {
        viewModelScope.launch { itemRepo.updateText(i.id, text) }
    }

    fun deleteItem(i: ItemEntity) {
        viewModelScope.launch {
            itemRepo.delete(i.id)
            ui.show("حذف شد", "بازگردانی") { itemRepo.restore(i.id) }
        }
    }

    fun moveItem(i: ItemEntity, to: Target) {
        viewModelScope.launch { itemRepo.move(i.id, to) }
    }

    fun noteToTask(i: ItemEntity, title: String, due: LocalDate?, repeat: String?) {
        viewModelScope.launch {
            val id = taskRepo.fromNote(i.id, title, due, repeat)
            ui.show("به کار تبدیل شد", "بازگردانی") { taskRepo.undoFromNote(id, i.id) }
        }
    }

    // ── «پرونده هم نهایی شود؟» ──
    fun finishCase(caseId: Long) {
        viewModelScope.launch {
            val prev = caseRepo.get(caseId)?.status ?: return@launch
            caseRepo.setStatus(caseId, CaseStatus.FINAL)
            ui.show("پرونده نهایی شد", "بازگردانی") { caseRepo.setStatus(caseId, CaseStatus.of(prev)) }
        }
    }

    fun neverAskFinish() {
        viewModelScope.launch { prefs.setBool(PrefRepository.NO_FINISH_ASK, true) }
    }

    private companion object {
        const val PAGE = 20
    }
}
