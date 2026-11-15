package app.pocketos.board.ui.stream

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pocketos.board.data.AudioRecorder
import app.pocketos.board.data.StoredFile
import app.pocketos.board.domain.Digits
import app.pocketos.board.ui.theme.board
import kotlinx.coroutines.delay

/** دک ثبت: «ثبت در: …» و دکمه‌های یادداشت، عکس، صدا، فایل، موقعیت. */
@Composable
fun CaptureBar(vm: StreamViewModel, onPickTarget: () -> Unit, onNote: () -> Unit) {
    val ctx = LocalContext.current
    val label by vm.targetLabel.collectAsStateWithLifecycle()
    var photoChoice by remember { mutableStateOf(false) }
    var recording by remember { mutableStateOf(false) }
    var camFile by remember { mutableStateOf<StoredFile?>(null) }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val f = camFile
        camFile = null
        if (f != null) { if (ok) vm.addStored(f) else vm.discard(f) }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { vm.importUris(it) }
    val docs = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.importUris(it) }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) recording = true else Toast.makeText(ctx, "برای ضبط صدا مجوز میکروفون لازم است", Toast.LENGTH_SHORT).show()
    }
    val locPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.any { it }) vm.addLocation() else Toast.makeText(ctx, "برای موقعیت مجوز لازم است", Toast.LENGTH_SHORT).show()
    }

    fun has(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED

    if (photoChoice) {
        AlertDialog(
            onDismissRequest = { photoChoice = false },
            title = { Text("عکس") },
            text = { Text("عکس با کیفیت اصلی ذخیره می‌شود و کوچک نمی‌شود.") },
            confirmButton = {
                TextButton({
                    photoChoice = false
                    val f = vm.files.create("photo.jpg", "image/jpeg")
                    camFile = f
                    camera.launch(vm.files.uriFor(f.path))
                }) { Text("دوربین") }
            },
            dismissButton = {
                TextButton({
                    photoChoice = false
                    gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }) { Text("گالری") }
            },
        )
    }
    if (recording) RecordDialog(vm) { recording = false }

    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onPickTarget).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ثبت در:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.board.muted)
            Text("  $label ▾", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1)
        }
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Btn("یادداشت", Modifier.weight(1f), onNote)
            Btn("عکس", Modifier.weight(1f)) { photoChoice = true }
            Btn("صدا", Modifier.weight(1f)) {
                if (has(Manifest.permission.RECORD_AUDIO)) recording = true else micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
            Btn("فایل", Modifier.weight(1f)) { docs.launch(arrayOf("*/*")) }
            Btn("موقعیت", Modifier.weight(1f)) {
                if (has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)) vm.addLocation()
                else locPermission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }
    }
}

@Composable
private fun Btn(text: String, modifier: Modifier, onClick: () -> Unit) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier.clip(RoundedCornerShape(12.dp)).background(MaterialTheme.board.slate).clickable(onClick = onClick).padding(vertical = 10.dp),
    )
}

/** ضبط صدا: شروع خودکار، زمان‌سنج، «ثبت» یا «بیخیال». */
@Composable
private fun RecordDialog(vm: StreamViewModel, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val recorder = remember { AudioRecorder(ctx) }
    var file by remember { mutableStateOf<StoredFile?>(null) }
    var seconds by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val f = vm.files.create("audio.m4a", "audio/mp4")
        try {
            recorder.start(vm.files.file(f.path))
            file = f
        } catch (_: Exception) {
            vm.discard(f)
            failed = true
        }
        while (true) {
            delay(1000)
            seconds++
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            // اگر دیالوگ بدون «ثبت» بسته شد، ضبط نیمه‌کاره دور ریخته می‌شود.
            val f = file
            if (f != null) {
                recorder.stop()
                vm.discard(f)
            }
        }
    }
    if (failed) {
        Toast.makeText(ctx, "ضبط صدا شروع نشد", Toast.LENGTH_SHORT).show()
        onDone()
        return
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("در حال ضبط…") },
        text = { Text(Digits.fa("%d:%02d".format(seconds / 60, seconds % 60)), style = MaterialTheme.typography.headlineMedium) },
        confirmButton = {
            TextButton({
                val f = file
                if (f != null) {
                    file = null
                    if (recorder.stop()) vm.addStored(f) else vm.discard(f)
                }
                onDone()
            }) { Text("ثبت") }
        },
        dismissButton = { TextButton(onDone) { Text("بیخیال") } },
    )
}
