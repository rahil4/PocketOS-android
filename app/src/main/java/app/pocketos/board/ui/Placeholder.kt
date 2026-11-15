package app.pocketos.board.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pocketos.board.ui.theme.board

/** صفحهٔ آمادهٔ مرحله‌های بعد: عنوان، زیرعنوان و یک کارت توضیح. */
@Composable
fun PlaceholderScreen(title: String, subtitle: String, note: String) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.board.muted)
        Text(
            note,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.board.slate, RoundedCornerShape(14.dp))
                .padding(16.dp),
        )
    }
}
