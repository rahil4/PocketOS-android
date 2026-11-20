package app.pocketos.board.data.repo

import app.pocketos.board.domain.TagMode
import app.pocketos.board.domain.WidgetTone
import app.pocketos.board.domain.WidgetLogic
import app.pocketos.board.domain.WidgetPrefs
import javax.inject.Inject
import javax.inject.Singleton

/** خواندن/ذخیرهٔ تنظیمات ویجت در جدول تنظیمات (روی خود گوشی). */
@Singleton
class WidgetSettingsRepository @Inject constructor(private val prefs: PrefRepository) {
    suspend fun load(): WidgetPrefs = WidgetPrefs(
        showCustomer = prefs.getString(PrefRepository.WIDGET_NAMES) != "0",
        count = WidgetPrefs.clampCount(prefs.getString(PrefRepository.WIDGET_COUNT)?.toIntOrNull() ?: 6),
        opacity = WidgetPrefs.clampOpacity(prefs.getString(PrefRepository.WIDGET_OPACITY)?.toIntOrNull() ?: 100),
        tagMode = TagMode.of(prefs.getString(PrefRepository.WIDGET_TAG_MODE)),
        tags = WidgetLogic.decodeTags(prefs.getString(PrefRepository.WIDGET_TAGS)),
        tone = WidgetTone.of(prefs.getString(PrefRepository.WIDGET_TONE)),
    )

    suspend fun save(p: WidgetPrefs) {
        prefs.setBool(PrefRepository.WIDGET_NAMES, p.showCustomer)
        prefs.setString(PrefRepository.WIDGET_COUNT, WidgetPrefs.clampCount(p.count).toString())
        prefs.setString(PrefRepository.WIDGET_OPACITY, WidgetPrefs.clampOpacity(p.opacity).toString())
        prefs.setString(PrefRepository.WIDGET_TAG_MODE, p.tagMode.code)
        prefs.setString(PrefRepository.WIDGET_TAGS, WidgetLogic.encodeTags(p.tags))
        prefs.setString(PrefRepository.WIDGET_TONE, p.tone.code)
    }
}
