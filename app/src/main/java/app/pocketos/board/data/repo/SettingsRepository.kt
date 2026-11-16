package app.pocketos.board.data.repo

import app.pocketos.board.domain.AppSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** تنظیمات اپ در جدول prefs؛ [state] برای رابط و [load] برای کارگر پس‌زمینه. */
@Singleton
class SettingsRepository @Inject constructor(private val prefs: PrefRepository) {
    private val _state = MutableStateFlow(AppSettings())
    val state: StateFlow<AppSettings> = _state

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { _state.value = load() }
    }

    private suspend fun bool(key: String, default: Boolean): Boolean = prefs.getString(key)?.let { it == "1" } ?: default

    suspend fun load(): AppSettings {
        val d = AppSettings()
        return AppSettings(
            theme = prefs.getString(PrefRepository.THEME)?.takeIf { it in listOf("auto", "dark", "light") } ?: d.theme,
            fsize = prefs.getString(PrefRepository.FSIZE)?.takeIf { it in listOf("sm", "md", "lg", "xl") } ?: d.fsize,
            eveningHour = prefs.getString(PrefRepository.EVENING_HOUR)?.toIntOrNull()?.takeIf { it in 0..23 } ?: d.eveningHour,
            morningOn = bool(PrefRepository.MORNING_ON, d.morningOn),
            morningHour = prefs.getString(PrefRepository.MORNING_HOUR)?.toIntOrNull()?.takeIf { it in 0..23 } ?: d.morningHour,
            eveningPush = bool(PrefRepository.EVENING_PUSH, d.eveningPush),
            pushNames = bool(PrefRepository.PUSH_NAMES, d.pushNames),
            backupRemind = bool(PrefRepository.BACKUP_REMIND, d.backupRemind),
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val n = transform(load())
        prefs.setString(PrefRepository.THEME, n.theme)
        prefs.setString(PrefRepository.FSIZE, n.fsize)
        prefs.setString(PrefRepository.EVENING_HOUR, n.eveningHour.toString())
        prefs.setBool(PrefRepository.MORNING_ON, n.morningOn)
        prefs.setString(PrefRepository.MORNING_HOUR, n.morningHour.toString())
        prefs.setBool(PrefRepository.EVENING_PUSH, n.eveningPush)
        prefs.setBool(PrefRepository.PUSH_NAMES, n.pushNames)
        prefs.setBool(PrefRepository.BACKUP_REMIND, n.backupRemind)
        _state.value = n
    }
}
