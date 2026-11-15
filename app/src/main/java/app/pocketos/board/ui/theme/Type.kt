package app.pocketos.board.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.pocketos.board.R

val Vazirmatn = FontFamily(
    Font(R.font.vazirmatn_regular, FontWeight.Normal),
    Font(R.font.vazirmatn_medium, FontWeight.Medium),
    Font(R.font.vazirmatn_bold, FontWeight.Bold),
)

private fun style(size: Int, weight: FontWeight = FontWeight.Normal, line: Int = size + 10) =
    TextStyle(fontFamily = Vazirmatn, fontWeight = weight, fontSize = size.sp, lineHeight = line.sp)

val BoardTypography = Typography(
    headlineMedium = style(24, FontWeight.Bold, 34),
    titleLarge = style(20, FontWeight.Bold, 30),
    titleMedium = style(16, FontWeight.Medium, 26),
    bodyLarge = style(16, line = 26),
    bodyMedium = style(14, line = 24),
    bodySmall = style(12, line = 20),
    labelLarge = style(14, FontWeight.Medium, 22),
    labelMedium = style(12, FontWeight.Medium, 18),
    labelSmall = style(11, FontWeight.Medium, 16),
)
