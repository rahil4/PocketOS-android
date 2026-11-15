package app.pocketos.board.domain

import java.util.regex.Pattern

/** برچسب `#اداره` داخل متن؛ ستون یا جدولی ندارد. */
object Tags {
    private val TAG = Pattern.compile("(?<![\\p{L}\\p{N}_#])#([\\p{L}\\p{N}_]{1,30})")
    private val SPACES = Regex("[ \\t]{2,}")

    /** برچسب‌های نرمال‌شدهٔ متن، بدون تکرار و به ترتیب؛ فقط‌عدد (مثل #۱۲) برچسب نیست. */
    fun tagsIn(text: String?): List<String> {
        val out = LinkedHashSet<String>()
        val m = TAG.matcher(text.orEmpty())
        while (m.find()) {
            val t = Normalize.key(m.group(1)).trim('_')
            if (t.isNotEmpty() && !t.all { it.isDigit() }) out += t
        }
        return out.toList()
    }

    /** متن بدون «#…»ها (برای نمایش؛ برچسب‌ها جدا به‌صورت چیپ می‌آیند). */
    fun strip(text: String?): String {
        val src = text.orEmpty()
        val sb = StringBuilder()
        var last = 0
        val m = TAG.matcher(src)
        while (m.find()) {
            sb.append(src, last, m.start())
            if (tagsIn(m.group()).isEmpty()) sb.append(m.group())
            last = m.end()
        }
        sb.append(src, last, src.length)
        return SPACES.replace(sb.toString(), " ").trim()
    }

    /** آیا متن این برچسب را (دقیقاً، نه پیشوندش را) دارد؟ */
    fun hasTag(text: String?, tag: String): Boolean = Normalize.key(tag).trimStart('#') in tagsIn(text)
}
