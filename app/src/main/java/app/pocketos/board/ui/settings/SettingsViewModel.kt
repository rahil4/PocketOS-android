package app.pocketos.board.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pocketos.board.data.repo.BackupService
import app.pocketos.board.data.repo.PrefRepository
import app.pocketos.board.data.repo.ReminderService
import app.pocketos.board.data.repo.RestoreKind
import app.pocketos.board.data.repo.RestorePreview
import app.pocketos.board.data.repo.SettingsRepository
import app.pocketos.board.domain.AppSettings
import app.pocketos.board.domain.BackupFormat
import app.pocketos.board.domain.BackupStatus
import app.pocketos.board.domain.Digits
import app.pocketos.board.system.AppRestarter
import app.pocketos.board.ui.UiMessenger
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

data class BackupUi(
    val status: String = "",
    val dataMb: Int = 1,
    val filesMb: Int = 0,
    /** پیام «در حال …» وقتی کاری در جریان است. */
    val busy: String? = null,
    val preview: RestorePreview? = null,
    val version: String = "",
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: SettingsRepository,
    private val reminders: ReminderService,
    private val backup: BackupService,
    private val prefs: PrefRepository,
    private val ui: UiMessenger,
    private val clock: Clock,
) : ViewModel() {
    val state: StateFlow<AppSettings> = repo.state

    private val _backup = MutableStateFlow(BackupUi())
    val backupState: StateFlow<BackupUi> = _backup

    private var restoreUri: Uri? = null

    init { refreshBackup() }

    fun update(change: (AppSettings) -> AppSettings) {
        viewModelScope.launch { repo.update(change) }
    }

    fun sendTest() {
        viewModelScope.launch {
            ui.show(if (reminders.sendTest()) "اعلان آزمایشی فرستاده شد" else "اعلان‌ها خاموش است؛ اول مجوز اعلان را بدهید")
        }
    }

    // ── پشتیبان ──
    fun dataFileName(): String = BackupFormat.dataName(LocalDate.now(clock))

    fun filesFileName(): String = BackupFormat.filesName(LocalDate.now(clock))

    fun refreshBackup() {
        viewModelScope.launch {
            val days = BackupStatus.daysSince(prefs.getString(PrefRepository.LAST_BACKUP), LocalDate.now(clock))
            val (dbMb, filesMb) = withContext(Dispatchers.IO) {
                val dbFile = context.getDatabasePath("board.db")
                val filesDir = File(context.filesDir, "files")
                mb(dbFile.length() + (File(dbFile.path + "-wal").takeIf { it.exists() }?.length() ?: 0L)) to
                    mb(filesDir.listFiles()?.sumOf { it.length() } ?: 0L)
            }
            val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
            _backup.value = _backup.value.copy(status = BackupStatus.text(days), dataMb = dbMb, filesMb = filesMb, version = version)
        }
    }

    private fun mb(bytes: Long) = maxOf(1L, (bytes + 1_048_575) / 1_048_576).toInt()

    private fun sizeText(bytes: Long) = Digits.fa("%.1f".format(bytes / 1_048_576.0)).replace('.', '٫')

    fun createData(uri: Uri?) {
        uri ?: return
        run("در حال ساخت پشتیبان…") {
            val out = requireNotNull(context.contentResolver.openOutputStream(uri, "w")) { "محل ذخیره باز نشد" }
            val bytes = backup.writeData(out)
            ui.show("پشتیبان ساخته شد · ${sizeText(bytes)} مگابایت")
        }
    }

    fun createFiles(uri: Uri?) {
        uri ?: return
        run("در حال ساخت پشتیبان عکس‌ها و فایل‌ها…") {
            val out = requireNotNull(context.contentResolver.openOutputStream(uri, "w")) { "محل ذخیره باز نشد" }
            val (n, bytes) = backup.writeFiles(out)
            ui.show("پشتیبان ${Digits.fa(n)} فایل ساخته شد · ${sizeText(bytes)} مگابایت")
        }
    }

    private fun run(busy: String, block: suspend () -> Unit) {
        if (_backup.value.busy != null) return
        _backup.value = _backup.value.copy(busy = busy)
        viewModelScope.launch {
            try {
                block()
            } catch (_: Exception) {
                ui.show("ساخت پشتیبان انجام نشد. محل دیگری را امتحان کنید.")
            } finally {
                _backup.value = _backup.value.copy(busy = null)
                refreshBackup()
            }
        }
    }

    // ── بازیابی ──
    fun inspect(uri: Uri?) {
        uri ?: return
        restoreUri = uri
        run("در حال خواندن پشتیبان…") {
            val p = backup.inspect { requireNotNull(context.contentResolver.openInputStream(uri)) { "فایل باز نشد" } }
            _backup.value = _backup.value.copy(preview = p)
        }
    }

    fun dismissPreview() {
        _backup.value = _backup.value.copy(preview = null)
        restoreUri = null
    }

    fun confirmRestore() {
        val p = _backup.value.preview ?: return
        when (p.kind) {
            RestoreKind.DATA -> {
                // جایگزینی در شروع بعدی برنامه، پیش از باز شدن پایگاه‌داده؛ پس برنامه دوباره باز می‌شود.
                if (backup.commitRestore()) AppRestarter.restart(context) else ui.show("بازیابی آماده نشد؛ دوباره امتحان کنید.")
            }
            RestoreKind.FILES -> {
                val uri = restoreUri ?: return
                _backup.value = _backup.value.copy(preview = null)
                run("در حال بازیابی فایل‌ها…") {
                    val n = backup.restoreFiles { requireNotNull(context.contentResolver.openInputStream(uri)) { "فایل باز نشد" } }
                    ui.show(Digits.fa("$n فایل بازیابی شد"))
                }
            }
            RestoreKind.INVALID -> dismissPreview()
        }
    }
}
