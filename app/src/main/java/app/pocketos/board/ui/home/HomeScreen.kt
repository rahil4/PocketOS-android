package app.pocketos.board.ui.home

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import app.pocketos.board.ui.PlaceholderScreen

@Composable
fun HomeScreen(vm: HomeViewModel = hiltViewModel()) {
    PlaceholderScreen(
        title = "سلام",
        subtitle = vm.todayText,
        note = "پایهٔ اپ آماده است. «امروز»، نیاز به توجه و دک ثبت در مرحله‌های بعد ساخته می‌شود.",
    )
}
