package app.pocketos.board.domain

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class WidgetLogicTest {
    private val text = "بررسی مدارک #دفتر #اداره"

    @Test
    fun dateLineIsJalaliWithWeekday() {
        assertEquals("جمعه ۲۹ آبان", WidgetLogic.dateLine(LocalDate.of(2026, 11, 20)))
    }

    @Test
    fun countLine() {
        assertEquals("امروز کاری ندارید", WidgetLogic.countLine(0, 0))
        assertEquals("۳ کار دارید", WidgetLogic.countLine(3, 0))
        assertEquals("۳ کار دارید · ۱ مانده", WidgetLogic.countLine(3, 1))
    }

    @Test
    fun tagsAreHiddenByDefault() {
        assertEquals("بررسی مدارک", WidgetLogic.title(text, WidgetPrefs()))
    }

    @Test
    fun onlySelectedTagsAreShown() {
        val p = WidgetPrefs(tagMode = TagMode.SELECTED, tags = setOf("اداره"))
        assertEquals("بررسی مدارک #اداره", WidgetLogic.title(text, p))
        assertEquals("بررسی مدارک", WidgetLogic.title(text, p.copy(tags = setOf("مالی"))))
    }

    @Test
    fun allTagsMode() {
        assertEquals("بررسی مدارک #دفتر #اداره", WidgetLogic.title(text, WidgetPrefs(tagMode = TagMode.ALL)))
    }

    @Test
    fun toneFallsBackToLight() {
        assertEquals(WidgetTone.LIGHT, WidgetTone.of(null))
        assertEquals(WidgetTone.LIGHT, WidgetTone.of("nope"))
        assertEquals(WidgetTone.DARK, WidgetTone.of("dark"))
        WidgetTone.entries.forEach { assertEquals(it, WidgetTone.of(it.code)) }
    }

    @Test
    fun clampsAndEncoding() {
        assertEquals(6, WidgetPrefs.clampCount(7))
        assertEquals(10, WidgetPrefs.clampCount(10))
        assertEquals(0, WidgetPrefs.clampOpacity(-5))
        assertEquals(100, WidgetPrefs.clampOpacity(140))
        assertEquals(setOf("a", "b"), WidgetLogic.decodeTags(WidgetLogic.encodeTags(setOf("b", "a"))))
        assertEquals(emptySet(), WidgetLogic.decodeTags(null))
    }
}
