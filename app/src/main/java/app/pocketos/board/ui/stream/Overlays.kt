package app.pocketos.board.ui.stream

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.pocketos.board.data.db.ItemEntity

/** وضعیت پنجره‌های روی صفحه: یادداشت، «ثبت در»، نمایشگر عکس، «پرونده هم نهایی شود؟». */
class OverlayState {
    var note by mutableStateOf(false)
    var taskMode by mutableStateOf(false)

    fun openNote(task: Boolean = false) { taskMode = task; note = true }
    var picker by mutableStateOf(false)
    var viewer by mutableStateOf<ItemEntity?>(null)
    var finishCase by mutableStateOf<Long?>(null)
}

@Composable
fun rememberOverlayState(): OverlayState = remember { OverlayState() }

@Composable
fun StreamOverlays(vm: StreamViewModel, o: OverlayState) {
    LaunchedEffect(vm) { vm.finishAsk.collect { o.finishCase = it } }
    if (o.note) NoteDialog(vm, o.taskMode) { o.note = false }
    if (o.picker) TargetPickerDialog(vm, "ثبت در…", { o.picker = false }) { vm.target.value = it; o.picker = false }
    o.viewer?.let { PhotoViewer(it, vm) { o.viewer = null } }
    o.finishCase?.let { id ->
        FinishCaseDialog(
            onYes = { vm.finishCase(id); o.finishCase = null },
            onNo = { o.finishCase = null },
            onNever = { vm.neverAskFinish(); o.finishCase = null },
        )
    }
}
