package app.pocketos.board.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/** رنگ‌های اضافهٔ دفتر. قرمز در هیچ‌جای اپ نیست؛ «مانده» کهربایی است. */
data class BoardColors(
    val amber: Color,
    val amberSoft: Color,
    val ok: Color,
    val okSoft: Color,
    val slate: Color,
    val muted: Color,
    val accentSoft: Color,
    /** رنگ نوار پرونده‌ها؛ پرونده با id % 6 یکی را برمی‌دارد. */
    val caseStripes: List<Color>,
)

internal val LightBoardColors = BoardColors(
    amber = Color(0xFFB7791F), amberSoft = Color(0xFFFBF0D9),
    ok = Color(0xFF3B7F5C), okSoft = Color(0xFFE1F1E8),
    slate = Color(0xFFE8EDF2), muted = Color(0xFF5F6F7A), accentSoft = Color(0xFFE0EEF1),
    caseStripes = listOf(
        Color(0xFF5B8FB9), Color(0xFF7AA07A), Color(0xFFB59A5B),
        Color(0xFF9486B5), Color(0xFF5FA3A3), Color(0xFFA89494),
    ),
)

internal val DarkBoardColors = BoardColors(
    amber = Color(0xFFE3B25A), amberSoft = Color(0xFF3A2F17),
    ok = Color(0xFF7FC59F), okSoft = Color(0xFF1B3328),
    slate = Color(0xFF242D34), muted = Color(0xFF97A6B0), accentSoft = Color(0xFF1F3238),
    caseStripes = listOf(
        Color(0xFF7BA9D0), Color(0xFF93B893), Color(0xFFCDB377),
        Color(0xFFADA0CB), Color(0xFF7FBABA), Color(0xFFC2AEAE),
    ),
)

// خطا هم قرمز نیست: کهربایی.
internal val LightScheme: ColorScheme = lightColorScheme(
    primary = Color(0xFF2F6F7E), onPrimary = Color.White,
    primaryContainer = Color(0xFFE0EEF1), onPrimaryContainer = Color(0xFF16363E),
    secondary = Color(0xFF5F6F7A), onSecondary = Color.White,
    background = Color(0xFFF3F5F7), onBackground = Color(0xFF1D2A33),
    surface = Color.White, onSurface = Color(0xFF1D2A33),
    surfaceVariant = Color(0xFFE8EDF2), onSurfaceVariant = Color(0xFF5F6F7A),
    outline = Color(0xFFDDE3E8), outlineVariant = Color(0xFFDDE3E8),
    error = Color(0xFFB7791F), onError = Color.White,
    errorContainer = Color(0xFFFBF0D9), onErrorContainer = Color(0xFF5C3C08),
)

internal val DarkScheme: ColorScheme = darkColorScheme(
    primary = Color(0xFF7CC0CF), onPrimary = Color(0xFF10242A),
    primaryContainer = Color(0xFF1F3238), onPrimaryContainer = Color(0xFFCDE8EE),
    secondary = Color(0xFF97A6B0), onSecondary = Color(0xFF14191D),
    background = Color(0xFF14191D), onBackground = Color(0xFFE6ECF0),
    surface = Color(0xFF1C2328), onSurface = Color(0xFFE6ECF0),
    surfaceVariant = Color(0xFF242D34), onSurfaceVariant = Color(0xFF97A6B0),
    outline = Color(0xFF2B353C), outlineVariant = Color(0xFF2B353C),
    error = Color(0xFFE3B25A), onError = Color(0xFF14191D),
    errorContainer = Color(0xFF3A2F17), onErrorContainer = Color(0xFFF3DDA8),
)
