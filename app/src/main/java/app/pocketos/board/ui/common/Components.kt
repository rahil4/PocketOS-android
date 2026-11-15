package app.pocketos.board.ui.common

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.pocketos.board.domain.Names
import app.pocketos.board.ui.theme.board

@Composable
fun stripeColor(id: Long): Color = MaterialTheme.board.caseStripes[(id % 6).toInt().let { if (it < 0) it + 6 else it }]

@Composable
fun Avatar(name: String, id: Long, size: Dp = 36.dp) {
    Box(
        Modifier.size(size).clip(CircleShape).background(stripeColor(id)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            Names.avatar(name),
            color = MaterialTheme.colorScheme.onPrimary,
            fontSize = (size.value * 0.42f).sp,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.board.muted,
        modifier = modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

/** گوشهٔ گرد کوچک: خالی/انتخاب‌شده. */
@Composable
fun Chip(text: String, selected: Boolean = false, amber: Boolean = false, onClick: (() -> Unit)? = null) {
    val bg = when {
        selected && amber -> MaterialTheme.board.amberSoft
        selected -> MaterialTheme.board.accentSoft
        else -> MaterialTheme.board.slate
    }
    val fg = when {
        selected && amber -> MaterialTheme.board.amber
        selected -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.board.muted
    }
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = bg,
        modifier = if (onClick != null) Modifier.clip(RoundedCornerShape(99.dp)).clickable(onClick = onClick) else Modifier,
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
    }
}

/** دکمهٔ گردِ کوچک؛ primary = رنگ اصلی. */
@Composable
fun Pill(text: String, primary: Boolean = false, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(99.dp),
        color = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        border = if (primary) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.clip(RoundedCornerShape(99.dp)).clickable(onClick = onClick),
    ) {
        Text(
            text,
            color = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
        )
    }
}

/** کارت با نوار رنگی کنار شروع (پرونده). */
@Composable
fun StripeCard(stripe: Color, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(shape)
            .background(MaterialTheme.board.slate)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Box(Modifier.width(5.dp).fillMaxHeight().background(stripe))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            content()
        }
    }
}

@Composable
fun EmptyNote(text: String) {
    Text(
        text,
        color = MaterialTheme.board.muted,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
    )
}
