package app.pocketos.board

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.platform.LocalLayoutDirection
import app.pocketos.board.ui.BoardRoot
import app.pocketos.board.ui.theme.BoardTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // رابط همیشه راست‌به‌چپ است، فارغ از زبان گوشی.
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                BoardTheme { BoardRoot() }
            }
        }
    }
}
