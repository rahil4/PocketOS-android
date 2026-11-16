package app.pocketos.board

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import app.pocketos.board.data.repo.SettingsRepository
import app.pocketos.board.ui.BoardRoot
import app.pocketos.board.ui.LaunchRequests
import app.pocketos.board.ui.theme.BoardTheme
import app.pocketos.board.widget.TodayWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var launch: LaunchRequests
    @Inject lateinit var settings: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        setContent {
            val s by settings.state.collectAsState()
            val dark = when (s.theme) {
                "dark" -> true
                "light" -> false
                else -> isSystemInDarkTheme()
            }
            val base = LocalDensity.current
            // رابط همیشه راست‌به‌چپ است؛ «اندازهٔ نوشته» مثل زومِ وب‌اپ کل رابط را بزرگ/کوچک می‌کند.
            CompositionLocalProvider(
                LocalLayoutDirection provides LayoutDirection.Rtl,
                LocalDensity provides Density(base.density * s.scale, base.fontScale),
            ) {
                BoardTheme(dark) { BoardRoot() }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    override fun onStop() {
        super.onStop()
        // ویجت بعد از کار با اپ تازه شود.
        CoroutineScope(Dispatchers.Default).launch { runCatching { TodayWidget().updateAllWidgets(applicationContext) } }
    }

    private fun handle(intent: Intent?) {
        intent ?: return
        if (intent.action == ACTION_QUICK_NOTE) {
            launch.route.value = "home"
            launch.quickNote.value = true
        } else {
            intent.getStringExtra(EXTRA_ROUTE)?.let { launch.route.value = it }
        }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        const val ACTION_QUICK_NOTE = "app.pocketos.board.QUICK_NOTE"
    }
}
