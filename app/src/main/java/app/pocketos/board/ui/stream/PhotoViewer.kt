package app.pocketos.board.ui.stream

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pocketos.board.data.db.ItemEntity
import coil.compose.AsyncImage

/** عکس تمام‌صفحه با کیفیت اصلی؛ «بازگشت» فقط همین را می‌بندد. */
@Composable
fun PhotoViewer(item: ItemEntity, vm: StreamViewModel, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val path = item.filePath ?: return
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AsyncImage(
                model = vm.files.file(path), contentDescription = item.fileName,
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(bottom = 56.dp),
            )
            Row(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                TextButton({ shareFile(ctx, vm.files, item) }) { Text("اشتراک", color = Color.White) }
                TextButton({ vm.pin(item); onClose() }) { Text(if (item.pinnedAt != null) "برداشتن سنجاق" else "سنجاق", color = Color.White) }
                TextButton(onClose) { Text("بستن", color = Color.White) }
            }
        }
    }
}
