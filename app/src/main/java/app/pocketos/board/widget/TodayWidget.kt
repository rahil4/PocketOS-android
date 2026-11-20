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
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.absolutePadding
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import app.pocketos.board.MainActivity
import app.pocketos.board.domain.Digits
import app.pocketos.board.domain.WidgetLogic

internal val CARD_BASE = Color(0xFF192019)
internal val AMBER = ColorProvider(Color(0xFFE2B872))

/**
 * ویجت «امروز»: تاریخ، «N کار امروز · M عقب‌مانده» و کارها در دو بخشِ جدا (امروز / عقب‌مانده).
 * نام متقاضی بالا و درشت، متن کار تا دو خط. راست‌به‌چپ؛ تنظیمات در [WidgetConfigActivity].
 */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 110.dp), DpSize(250.dp, 110.dp), DpSize(250.dp, 250.dp)),
    )

    suspend fun updateAllWidgets(context: Context) = updateAll(context)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadWidgetData(context)
        provideContent { Content(context, data) }
    }

    @Composable
    private fun Content(context: Context, d: WidgetData) {
        val size = LocalSize.current
        val openApp = actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val small = size.width < 200.dp
        val limit = if (size.height >= 200.dp) d.prefs.count else minOf(d.prefs.count, 3)
        val slots = WidgetLogic.slots(d.today.size, d.late.size, limit)
        val card = ColorProvider(CARD_BASE.copy(alpha = d.prefs.opacity / 100f))
        val right = TextAlign.Right    // مطلق (نه start): چیدمان ویجت از جهت لانچر می‌آید، اما متن همیشه راست‌چین است
        val full = GlanceModifier.fillMaxWidth()
        // رنگ متنِ انتخابی کاربر، با سه غلظت؛ «عقب‌مانده» همیشه کهربایی
        val base = Color(d.prefs.tone.argb)
        val ink = ColorProvider(base)
        val soft = ColorProvider(base.copy(alpha = 0.82f))
        val muted = ColorProvider(base.copy(alpha = 0.62f))
        val line = ColorProvider(base.copy(alpha = 0.18f))

        Column(
            GlanceModifier.fillMaxSize().background(card).cornerRadius(22.dp).padding(12.dp).clickable(openApp),
            horizontalAlignment = if (small) Alignment.Horizontal.CenterHorizontally else Alignment.Horizontal.Start,
        ) {
            if (small) {
                Text(d.date, style = TextStyle(color = muted, fontSize = 11.sp, textAlign = TextAlign.Center), maxLines = 1)
                Text(Digits.fa(d.today.size), style = TextStyle(color = ink, fontSize = 28.sp, fontWeight = FontWeight.Bold))
                Text("کار امروز", style = TextStyle(color = muted, fontSize = 11.sp))
                if (d.late.isNotEmpty()) {
                    Text(Digits.fa("${d.late.size} عقب‌مانده"), style = TextStyle(color = AMBER, fontSize = 12.sp, fontWeight = FontWeight.Bold))
                }
            } else {
                Text(d.date, full, style = TextStyle(color = ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = right), maxLines = 1)
                Text(
                    WidgetLogic.countLine(d.today.size, d.late.size), full,
                    style = TextStyle(color = if (d.late.isNotEmpty()) AMBER else muted, fontSize = 12.sp, textAlign = right), maxLines = 1,
                )
                if (d.today.isEmpty() && d.late.isEmpty()) {
                    Spacer(GlanceModifier.height(6.dp))
                    Text("✓ همه‌چیز مرتب است", full, style = TextStyle(color = muted, fontSize = 12.sp, textAlign = right))
                }
                if (slots.today > 0) {
                    Section(full, "امروز", d.today.size, muted, line, right)
                    d.today.take(slots.today).forEach { TaskRow(full, it, d.prefs.showCustomer, ink, soft, right) }
                }
                if (slots.late > 0) {
                    Section(full, "عقب‌مانده", d.late.size, AMBER, line, right)
                    d.late.take(slots.late).forEach { TaskRow(full, it, d.prefs.showCustomer, AMBER, AMBER, right) }
                }
                if (slots.more > 0) {
                    Text(Digits.fa("+ ${slots.more} مورد دیگر"), full.padding(top = 8.dp), style = TextStyle(color = muted, fontSize = 11.sp, textAlign = right))
                }
            }
        }
    }

    @Composable
    private fun Section(full: GlanceModifier, title: String, n: Int, color: ColorProvider, line: ColorProvider, right: TextAlign) {
        Spacer(GlanceModifier.height(10.dp))
        Text(Digits.fa("$title · $n"), full, style = TextStyle(color = color, fontSize = 11.sp, textAlign = right), maxLines = 1)
        Spacer(full.height(1.dp).background(line))
    }

    @Composable
    private fun TaskRow(full: GlanceModifier, t: WidgetTask, showCustomer: Boolean, nameColor: ColorProvider, textColor: ColorProvider, right: TextAlign) {
        val showName = showCustomer && !t.customer.isNullOrBlank()
        Column(full.padding(top = 8.dp)) {
            // نام متقاضی بالا و درشت، متن کار زیرش (۴ فاصله جلوتر از راست؛ بدون نام، سر خط)
            if (showName) {
                Text(t.customer.orEmpty(), full, style = TextStyle(color = nameColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, textAlign = right), maxLines = 1)
            }
            Text(
                Digits.fa(t.title), if (showName) full.absolutePadding(right = 14.dp) else full,
                style = TextStyle(color = if (showName) textColor else nameColor, fontSize = 12.sp, textAlign = right), maxLines = 2,
            )
        }
    }
}
