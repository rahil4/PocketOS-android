package app.pocketos.board.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.pocketos.board.data.db.BranchRow
import app.pocketos.board.ui.theme.board
import androidx.compose.material3.MaterialTheme
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** رنگ‌های درخت؛ همه از تم می‌آیند (بدون قرمز: میوه نارنجی-کهربایی است). */
internal data class TreePalette(
    val bark: Color, val barkDark: Color, val leaf: Color, val leaf2: Color, val leaf3: Color,
    val fresh: Color, val bud: Color, val budLine: Color, val old: Color, val fruit: Color, val sky: Color,
    val season: Color,
)

@Composable
internal fun rememberTreePalette(seasonIndex: Int): TreePalette {
    val b = MaterialTheme.board
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val seasonColor = when (seasonIndex) {
        0 -> if (dark) Color(0xFF6FB181) else Color(0xFF8CBF93)
        1 -> if (dark) Color(0xFF7BD89A) else Color(0xFF2F7D50)
        2 -> if (dark) Color(0xFFD9A24E) else Color(0xFFC9923F)
        else -> if (dark) Color(0xFF6F7D74) else Color(0xFF9AA89E)
    }
    return remember(dark, seasonIndex, b) {
        TreePalette(
            bark = if (dark) Color(0xFFA88B6B) else Color(0xFF8A6A4B),
            barkDark = if (dark) Color(0xFF6E5A44) else Color(0xFF5B4430),
            leaf = b.ok,
            leaf2 = if (dark) Color(0xFF4C9468) else Color(0xFF5FA67A),
            leaf3 = if (dark) Color(0xFF6BB487) else Color(0xFF8FC59F),
            fresh = if (dark) Color(0xFFE3B857) else Color(0xFFB98A2E),
            bud = b.amberSoft, budLine = b.amber,
            old = if (dark) Color(0xFF3A463E) else Color(0xFFB9C4BB),
            fruit = if (dark) Color(0xFFE6A25A) else Color(0xFFD98A3D),
            sky = b.slate,
            season = seasonColor,
        )
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

/** یک شاخهٔ قابل‌رسم: برگ انجام‌شده، همین هفته، جوانه (کار مانده) و میوه. */
internal data class BranchSpec(
    val id: Long, val seed: Long, val done: Int, val fresh: Int, val buds: Int, val fruits: Int,
    val faded: Boolean = false,
)

internal fun BranchRow.spec(faded: Boolean = false) =
    BranchSpec(id, id * 31 + 7, done, doneWeek, min(open, 3), fruits, faded)

/** مختصات انتهای هر شاخه برای لمس (در مختصات 300×330). */
internal class Laid(val spec: BranchSpec, val tip: Offset)

private const val W = 300f
private const val H = 330f

private class Rnd(seed: Long) {
    private val r = java.util.Random(seed)
    fun next(): Float = r.nextFloat()
}

private fun q(p0: Offset, c: Offset, p1: Offset, t: Float): Offset {
    val u = 1 - t
    return Offset(u * u * p0.x + 2 * u * t * c.x + t * t * p1.x, u * u * p0.y + 2 * u * t * c.y + t * t * p1.y)
}

private fun tg(p0: Offset, c: Offset, p1: Offset, t: Float): Float {
    val u = 1 - t
    return atan2(2 * u * (c.y - p0.y) + 2 * t * (p1.y - c.y), 2 * u * (c.x - p0.x) + 2 * t * (p1.x - c.x))
}

private fun DrawScope.limb(p0: Offset, c: Offset, p1: Offset, w0: Float, w1: Float, color: Color, barkLine: Color? = null) {
    val n = 14
    val l = ArrayList<Offset>(); val r = ArrayList<Offset>()
    for (i in 0..n) {
        val t = i / n.toFloat(); val p = q(p0, c, p1, t); val a = tg(p0, c, p1, t); val w = (w0 + (w1 - w0) * t) / 2
        l += Offset(p.x - sin(a) * w, p.y + cos(a) * w); r += Offset(p.x + sin(a) * w, p.y - cos(a) * w)
    }
    val path = Path().apply {
        moveTo(l[0].x, l[0].y); l.drop(1).forEach { lineTo(it.x, it.y) }; r.reversed().forEach { lineTo(it.x, it.y) }; close()
    }
    drawPath(path, color)
    if (barkLine != null && w0 > 6f) for (k in 0 until 3) {
        val tt = 0.12f + k * 0.07f; val off = (k - 1) * w0 * 0.16f
        val a1 = q(p0, c, p1, tt); val a2 = q(p0, c, p1, tt + 0.35f); val ang = tg(p0, c, p1, tt)
        drawLine(
            barkLine.copy(alpha = 0.55f),
            Offset(a1.x - sin(ang) * off, a1.y + cos(ang) * off), Offset(a2.x - sin(ang) * off * .7f, a2.y + cos(ang) * off * .7f), 1f,
        )
    }
}

private val leafPath = Path().apply {
    moveTo(0f, 0f); cubicTo(4f, -5.5f, 12f, -5.5f, 19f, 0f); cubicTo(12f, 5.5f, 4f, 5.5f, 0f, 0f); close()
}

private fun DrawScope.leaf(x: Float, y: Float, rotDeg: Float, s: Float, fill: Color, line: Color? = null) {
    translate(x, y) {
        rotate(rotDeg, Offset.Zero) {
            scale(s, s, Offset.Zero) {
                drawPath(leafPath, fill)
                if (line != null) drawPath(leafPath, line, style = Stroke(1.6f / s))
            }
        }
    }
}

private fun DrawScope.fruit(x: Float, y: Float, r: Float, p: TreePalette) {
    drawLine(p.bark, Offset(x, y - r), Offset(x + 2f, y - r - 6f), 1.4f)
    drawCircle(p.fruit, r, Offset(x, y))
    drawCircle(Color.White.copy(alpha = .35f), r * .32f, Offset(x - r * .35f, y - r * .35f))
    leaf(x + 2f, y - r - 5f, -20f, 0.6f, p.leaf)
}

/** یک شاخه با شاخک و برگ؛ نوک شاخهٔ اصلی را برمی‌گرداند. */
private fun DrawScope.plant(p0: Offset, c: Offset, p1: Offset, b: BranchSpec, w: Float, len: Float, s: Float, cap: Int, pal: TreePalette): Offset {
    val rnd = Rnd(b.seed)
    limb(p0, c, p1, w, max(1.2f, w * .35f), pal.bark)
    val doneShown = min(b.done, cap)
    val needed = doneShown + b.buds
    val k = max(2, Math.ceil(needed / 4.5).toInt())
    class Slot(val x: Float, val y: Float, val a: Float, val s: Float)
    val slots = ArrayList<Slot>()
    var lastTip: Offset? = null
    for (i in 0 until k) {
        val t = 0.22f + 0.7f * (i + .5f) / k
        val pt = q(p0, c, p1, t); val a = tg(p0, c, p1, t); val side = if (i % 2 == 1) 1 else -1
        val ang = a + side * (0.7f + rnd.next() * 0.35f)
        val l = len * (0.7f + rnd.next() * 0.6f)
        val e = Offset(pt.x + cos(ang) * l, pt.y + sin(ang) * l)
        val cc = Offset(pt.x + cos(ang - side * .35f) * l * .55f, pt.y + sin(ang - side * .35f) * l * .55f)
        limb(pt, cc, e, max(1.3f, w * .38f), .8f, pal.bark)
        lastTip = e
        val m = 4 + (rnd.next() * 2).toInt()
        for (j in 0 until m) {
            val tt = .35f + .65f * (j + .5f) / m; val qq = q(pt, cc, e, tt); val aa = tg(pt, cc, e, tt); val sd = if (j % 2 == 1) 1 else -1
            slots += Slot(qq.x, qq.y, aa + sd * (.6f + rnd.next() * .5f), s * (.8f + rnd.next() * .45f))
        }
        slots += Slot(e.x, e.y, ang + (rnd.next() - .5f) * .5f, s * 1.15f)
    }
    for (z in slots.size - 1 downTo 1) { val r = (rnd.next() * (z + 1)).toInt().coerceAtMost(z); val t = slots[z]; slots[z] = slots[r]; slots[r] = t }
    val fresh = min(b.fresh, doneShown)
    slots.take(needed).forEachIndexed { i, sl ->
        val deg = (sl.a * 180f / Math.PI).toFloat()
        when {
            b.faded -> leaf(sl.x, sl.y, deg, sl.s, pal.old)
            i < doneShown - fresh -> leaf(sl.x, sl.y, deg, sl.s, when { i % 4 == 0 -> pal.leaf3; i % 3 == 0 -> pal.leaf2; else -> pal.leaf })
            i < doneShown -> leaf(sl.x, sl.y, deg, sl.s, pal.leaf, pal.fresh)
            else -> leaf(sl.x, sl.y, deg, sl.s, pal.bud, pal.budLine)
        }
    }
    val tip = lastTip
    if (b.fruits > 0 && tip != null) fruit(tip.x, tip.y + 14f, 8f, pal)
    return p1
}

private fun DrawScope.trunk(top: Float, w: Float, pal: TreePalette) {
    limb(Offset(150f, 322f), Offset(134f, 326f), Offset(112f, 330f), 9f, 2f, pal.bark)
    limb(Offset(150f, 322f), Offset(166f, 326f), Offset(190f, 330f), 9f, 2f, pal.bark)
    limb(Offset(150f, 332f), Offset(149f, 230f), Offset(151f, top), w, w * .38f, pal.bark, pal.barkDark)
}

/** درخت خانه: [active] شاخه‌های فعال (حداکثر ۶)، بقیه کم‌رنگ پایین تنه. */
internal fun DrawScope.drawHomeTree(active: List<BranchSpec>, pal: TreePalette, laid: MutableList<Laid>?) {
    trunk(120f, 18f, pal)
    val ys = floatArrayOf(285f, 262f, 226f, 204f, 170f, 146f)
    active.take(6).forEachIndexed { i, b ->
        val side = if (i % 2 == 0) -1 else 1
        val run = 118f - i * 7f
        val p0 = Offset(150f + side * 3, ys[i]); val p1 = Offset(150f + side * run, ys[i] - 44f - i)
        val c = Offset(150f + side * run * .4f, ys[i] - 24f)
        val tip = plant(p0, c, p1, b, 6.2f - i * .5f, 30f, 1.05f, 24, pal)
        laid?.add(Laid(b, tip))
    }
}

/** شاخهٔ قدیمی‌ها؛ فقط یک شاخه با برگ کم‌رنگ. */
internal fun DrawScope.drawOldBranch(pal: TreePalette) {
    plant(Offset(148f, 318f), Offset(104f, 306f), Offset(54f, 314f), BranchSpec(-1, 2, 14, 0, 0, 0, faded = true), 4f, 18f, .75f, 14, pal)
}

/** درخت کامل: همهٔ متقاضی‌ها؛ فعال‌ها درشت، قدیمی‌ها کم‌رنگ. */
internal fun DrawScope.drawFullTree(all: List<BranchSpec>, pal: TreePalette, laid: MutableList<Laid>?) {
    trunk(96f, 22f, pal)
    all.take(40).forEachIndexed { i, b ->
        val side = if (i % 2 == 1) 1 else -1
        val y = max(292f - i * 5.9f, 118f)
        val run = 48f + (i % 5) * 14f + (if (i < 8) 22f else 0f)
        val act = !b.faded
        val p1 = Offset(150f + side * run, y - 30f - (i % 4) * 5f)
        val tip = plant(Offset(150f + side * 4, y), Offset(150f + side * run * .45f, y - 12f), p1, b, if (act) 4.4f else 3f, if (act) 18f else 13f, if (act) .8f else .62f, if (act) 9 else 4, pal)
        laid?.add(Laid(b, tip))
    }
}

/** بوم درخت با لمس: روی شاخه ← [onBranch]، جای دیگر ← [onTree]. */
@Composable
internal fun TreeCanvas(
    branches: List<BranchSpec>, pal: TreePalette, full: Boolean, height: Dp,
    onBranch: (Long) -> Unit, onTree: () -> Unit, modifier: Modifier = Modifier,
) {
    val laid = remember(branches, full) { ArrayList<Laid>() }
    Canvas(
        modifier.fillMaxWidth().height(height)
            .pointerInput(branches, full) {
                detectTapGestures { pos ->
                    val k = size.width / W
                    val yOff = (size.height - H * k) / 2
                    fun dist(l: Laid) = hypot(l.tip.x * k - pos.x, l.tip.y * k + yOff - pos.y)
                    val hit = laid.minByOrNull { dist(it) }
                    if (hit != null && hit.spec.id > 0 && dist(hit) < 70f * k) onBranch(hit.spec.id) else onTree()
                }
            },
    ) {
        laid.clear()
        val k = size.width / W
        val yOff = (size.height - H * k) / 2
        translate(0f, yOff) {
            scale(k, k, Offset.Zero) {
                if (full) drawFullTree(branches, pal, laid) else { drawOldBranch(pal); drawHomeTree(branches, pal, laid) }
            }
        }
    }
}
