package app.pocketos.board.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalBoardColors = staticCompositionLocalOf { LightBoardColors }

/** دسترسی به رنگ‌های اضافهٔ دفتر: `MaterialTheme.board.amber` */
val MaterialTheme.board: BoardColors
    @Composable @ReadOnlyComposable get() = LocalBoardColors.current

@Composable
fun BoardTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBoardColors provides if (dark) DarkBoardColors else LightBoardColors) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = BoardTypography,
            content = content,
        )
    }
}
