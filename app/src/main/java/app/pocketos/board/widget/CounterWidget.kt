package app.pocketos.board.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.pocketos.board.MainActivity
import app.pocketos.board.domain.Digits

/**
 * ویجت «شمارنده»: فقط تاریخ، تعداد کارهای امروز و تعداد عقب‌مانده‌ها (دو عدد درشت کنار هم).
 * رنگ متن و شفافیت از همان تنظیمات مشترک ویجت‌ها می‌آید.
 */
class CounterWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 56.dp), DpSize(180.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        provideContent { Content(context, data) }
    }

    @Composable
    private fun Content(context: Context, d: WidgetData) {
        val tall = LocalSize.current.height >= 100.dp
        val openApp = actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val base = Color(d.prefs.tone.argb)
        val ink = ColorProvider(base)
        val muted = ColorProvider(base.copy(alpha = 0.62f))
        val numSize = if (tall) 34.sp else 24.sp

        Column(
            GlanceModifier.fillMaxSize().background(ColorProvider(CARD_BASE.copy(alpha = d.prefs.opacity / 100f)))
                .cornerRadius(22.dp).padding(horizontal = 12.dp, vertical = 8.dp).clickable(openApp),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        ) {
            Text(d.date, style = TextStyle(color = ink, fontSize = if (tall) 14.sp else 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center), maxLines = 1)
            Spacer(GlanceModifier.height(if (tall) 8.dp else 2.dp))
            // دو ستون برابر؛ ترتیب بصری چپ/راست مهم نیست (هر ستون برچسب خودش را دارد)
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Vertical.CenterVertically) {
                Stat(GlanceModifier.defaultWeight(), d.today.size, "کار امروز", ink, muted, numSize)
                Box(GlanceModifier.width(1.dp).height(if (tall) 40.dp else 28.dp).background(ColorProvider(base.copy(alpha = 0.18f)))) {}
                val lateColor = if (d.late.isEmpty()) muted else AMBER
                Stat(GlanceModifier.defaultWeight(), d.late.size, "عقب‌مانده", lateColor, if (d.late.isEmpty()) muted else AMBER, numSize)
            }
        }
    }

    @Composable
    private fun Stat(modifier: GlanceModifier, n: Int, label: String, numColor: ColorProvider, labelColor: ColorProvider, numSize: androidx.compose.ui.unit.TextUnit) {
        Column(modifier, horizontalAlignment = Alignment.Horizontal.CenterHorizontally) {
            Text(Digits.fa(n), style = TextStyle(color = numColor, fontSize = numSize, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center))
            Text(label, style = TextStyle(color = labelColor, fontSize = 11.sp, textAlign = TextAlign.Center))
        }
    }
}
