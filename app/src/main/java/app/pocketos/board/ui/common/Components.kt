package app.pocketos.board.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketos.board.domain.Names
import app.pocketos.board.ui.theme.board

/** رنگ نوار پرونده؛ با id % 6 (ذخیره نمی‌شود). */
@Composable
fun stripeColor(id: Long): Color = MaterialTheme.board.caseStripes[(id % 6).toInt().let { if (it < 0) it + 6 else it }]

/** مشتری = دایرهٔ آبی‌خاکستری با حرف اول نام (مثل وب‌اپ). [id] برای سازگاری با فراخوان‌ها نگه داشته شده. */
@Composable
fun Avatar(name: String, @Suppress("UNUSED_PARAMETER") id: Long, size: Dp = 36.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(MaterialTheme.board.cu),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            Names.avatar(name),
            color = MaterialTheme.board.cuInk,
            fontSize = (size.value * 0.46f).sp,
            fontWeight = FontWeight.ExtraBold,
            lineHeight = (size.value * 0.46f).sp,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.board.muted,
        modifier = modifier.padding(top = 14.dp, bottom = 2.dp),
    )
}

/** چیپ: خاکستریِ دورخط‌دار؛ انتخاب‌شده = پر از رنگ اصلی؛ amber = دورخط کهربایی. */
@Composable
fun Chip(text: String, selected: Boolean = false, amber: Boolean = false, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(99.dp)
    val bg = if (selected && !amber) MaterialTheme.colorScheme.primary else if (selected && amber) MaterialTheme.board.amberSoft else MaterialTheme.board.slate
    val fg = when {
        selected && !amber -> MaterialTheme.board.accentInk
        amber -> MaterialTheme.board.amber
        else -> MaterialTheme.board.muted
    }
    val borderColor = when {
        selected && !amber -> Color.Transparent
        amber -> MaterialTheme.board.amber
        else -> MaterialTheme.board.line
    }
    Text(
        text,
        color = fg,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clip(shape).background(bg).border(1.dp, borderColor, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = if (onClick != null) 12.dp else 10.dp, vertical = if (onClick != null) 5.dp else 1.dp),
    )
}

/** برچسب #: سبز، کوچک، کنار عنوان کار یا یادداشت. */
@Composable
fun TagChip(tag: String, onClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(7.dp)
    Text(
        "#$tag",
        color = MaterialTheme.colorScheme.primary,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(shape).background(MaterialTheme.board.accentSoft)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

/** دکمه: کم‌رنگِ دورخط‌دار؛ primary = سبز پر. */
@Composable
fun Pill(text: String, primary: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .defaultMinSize(minHeight = 40.dp)
            .clip(shape)
            .background(if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.board.slate)
            .then(if (primary) Modifier else Modifier.border(BorderStroke(1.dp, MaterialTheme.board.line), shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (primary) MaterialTheme.board.accentInk else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
}

/** کارت پرونده: سطح سفید، دورخط، نوار رنگی کنار شروع (۶dp). */
@Composable
fun StripeCard(stripe: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEnd = 14.dp, bottomEnd = 14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.board.line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Box(Modifier.width(6.dp).fillMaxHeight().background(stripe))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            content()
        }
    }
}

/** کارت مشتری: زمینهٔ آبی‌خاکستری و دورخط آبی (سربرگ مشتری و گروه‌های مشتری). */
@Composable
fun CustomerCard(modifier: Modifier = Modifier, radius: Dp = 14.dp, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(radius)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.board.cuBg)
            .border(1.dp, MaterialTheme.board.cuLine, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}

/** کارت سفید دورخط‌دار (مورد ثبت‌شده، ردیف مشتری). */
@Composable
fun SurfaceCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.board.line, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) { content() }
}

@Composable
fun EmptyNote(text: String) {
    Text(
        text,
        color = MaterialTheme.board.muted,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 8.dp),
    )
}

/** «‹ مشتری» بالای صفحهٔ پرونده: قرص آبی‌خاکستری با آواتار کوچک. */
@Composable
fun Crumb(name: String, id: Long, onClick: () -> Unit) {
    val shape = RoundedCornerShape(99.dp)
    Row(
        Modifier.clip(shape).background(MaterialTheme.board.cuBg).border(1.dp, MaterialTheme.board.cuLine, shape)
            .clickable(onClick = onClick).padding(start = 4.dp, end = 12.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Avatar(name, id, 22.dp)
        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}
