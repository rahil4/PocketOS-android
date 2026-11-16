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

private fun style(size: Float, weight: FontWeight = FontWeight.Normal, lineHeight: Float = size * 1.55f) =
    TextStyle(fontFamily = Vazirmatn, fontWeight = weight, fontSize = size.sp, lineHeight = lineHeight.sp)

/** اندازه‌ها از وب‌اپ: متن ۱۷، عنوان ۱۹، سلام ۲۳، زیرنویس ۱۳٫۵، چیپ ۱۲٫۵. */
val BoardTypography = Typography(
    headlineMedium = style(23f, FontWeight.Medium, 34f),
    titleLarge = style(19f, FontWeight.Bold, 29f),
    titleMedium = style(16.5f, FontWeight.Medium, 26f),
    bodyLarge = style(17f, lineHeight = 27f),
    bodyMedium = style(15.5f, lineHeight = 25f),
    bodySmall = style(13.5f, lineHeight = 21f),
    labelLarge = style(14.5f, FontWeight.Medium, 22f),
    labelMedium = style(12.5f, FontWeight.Medium, 19f),
    labelSmall = style(11.5f, FontWeight.Medium, 17f),
)
