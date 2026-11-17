package app.pocketos.board.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * رنگ‌های دفتر؛ همان پالت وب‌اپ (سبز آرام، آواتار آبی‌خاکستری، نوار پرونده‌های رنگی).
 * قرمز در هیچ‌جای اپ نیست؛ «مانده» کهربایی است.
 */
data class BoardColors(
    val amber: Color,
    val amberSoft: Color,
    /** رنگ «انجام شد» و تأیید (همان accent). */
    val ok: Color,
    val okSoft: Color,
    /** سطح بالاآمده (raised). */
    val slate: Color,
    val muted: Color,
    /** زمینهٔ ملایم accent (چیپ تگ). */
    val accentSoft: Color,
    val accentInk: Color,
    val line: Color,
    /** متقاضی: آواتار، نوار گروه و سربرگ. */
    val cu: Color,
    val cuBg: Color,
    val cuLine: Color,
    val cuInk: Color,
    val info: Color,
    /** رنگ نوار پرونده‌ها؛ پرونده با id % 6 یکی را برمی‌دارد. */
    val caseStripes: List<Color>,
)

internal val LightBoardColors = BoardColors(
    amber = Color(0xFFA7681A), amberSoft = Color(0xFFF7EEDF),
    ok = Color(0xFF2F7D50), okSoft = Color(0xFFE2EDE7),
    slate = Color(0xFFEEF1EC), muted = Color(0xFF5F6E63),
    accentSoft = Color(0xFFE2EDE7), accentInk = Color.White,
    line = Color(0xFFDDE3DA),
    cu = Color(0xFF3F74B5), cuBg = Color(0xFFE8F0FA), cuLine = Color(0xFFC6D6EA), cuInk = Color.White,
    info = Color(0xFF2C6AA0),
    caseStripes = listOf(
        Color(0xFFB9801A), Color(0xFF7A5BC0), Color(0xFFC25A4B),
        Color(0xFF2D8F85), Color(0xFF5B9244), Color(0xFFB85D8A),
    ),
)

internal val DarkBoardColors = BoardColors(
    amber = Color(0xFFE2B872), amberSoft = Color(0xFF2B2418),
    ok = Color(0xFF8CC9A1), okSoft = Color(0xFF29382C),
    slate = Color(0xFF1F2822), muted = Color(0xFF8B9A90),
    accentSoft = Color(0xFF29382C), accentInk = Color(0xFF0C1710),
    line = Color(0xFF262F29),
    cu = Color(0xFF6F9BD4), cuBg = Color(0xFF1A2634), cuLine = Color(0xFF2B3A4D), cuInk = Color(0xFF0B1420),
    info = Color(0xFF8BB6D9),
    caseStripes = listOf(
        Color(0xFFD9A441), Color(0xFF9B7FD6), Color(0xFFD9786A),
        Color(0xFF4FB3A9), Color(0xFF7FB56A), Color(0xFFD17FA8),
    ),
)

// خطا هم قرمز نیست: کهربایی. سطح‌های M3 (سطح پنجره‌ها، منوها) صریح تنظیم شده‌اند تا بنفشِ پیش‌فرض نیاید.
internal val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF2F7D50), onPrimary = Color.White,
    primaryContainer = Color(0xFFE2EDE7), onPrimaryContainer = Color(0xFF1C2620),
    secondary = Color(0xFF5F6E63), onSecondary = Color.White,
    background = Color(0xFFF5F6F2), onBackground = Color(0xFF1C2620),
    surface = Color.White, onSurface = Color(0xFF1C2620),
    surfaceVariant = Color(0xFFEEF1EC), onSurfaceVariant = Color(0xFF5F6E63),
    surfaceTint = Color(0xFF2F7D50),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White, surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White, surfaceContainerHighest = Color(0xFFEEF1EC),
    outline = Color(0xFFDDE3DA), outlineVariant = Color(0xFFDDE3DA),
    error = Color(0xFFA7681A), onError = Color.White,
    errorContainer = Color(0xFFF7EEDF), onErrorContainer = Color(0xFF5C3C08),
)

internal val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF8CC9A1), onPrimary = Color(0xFF0C1710),
    primaryContainer = Color(0xFF29382C), onPrimaryContainer = Color(0xFFE8EEE9),
    secondary = Color(0xFF8B9A90), onSecondary = Color(0xFF111613),
    background = Color(0xFF111613), onBackground = Color(0xFFE8EEE9),
    surface = Color(0xFF192019), onSurface = Color(0xFFE8EEE9),
    surfaceVariant = Color(0xFF1F2822), onSurfaceVariant = Color(0xFF8B9A90),
    surfaceTint = Color(0xFF8CC9A1),
    surfaceContainerLowest = Color(0xFF111613), surfaceContainerLow = Color(0xFF192019), surfaceContainer = Color(0xFF192019),
    surfaceContainerHigh = Color(0xFF1F2822), surfaceContainerHighest = Color(0xFF263029),
    outline = Color(0xFF262F29), outlineVariant = Color(0xFF262F29),
    error = Color(0xFFE2B872), onError = Color(0xFF111613),
    errorContainer = Color(0xFF2B2418), onErrorContainer = Color(0xFFF3DDA8),
)
