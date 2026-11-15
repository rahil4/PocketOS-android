package app.pocketos.board.domain

/** ارقام: نمایش فارسی، و نرمال‌سازی ورودی کاربر (فارسی/عربی ← لاتین). */
object Digits {
    private const val FA = "۰۱۲۳۴۵۶۷۸۹"
    private const val AR = "٠١٢٣٤٥٦٧٨٩"

    /** فقط برای نمایش. */
    fun fa(s: Any?): String = buildString {
        for (c in s.toString()) append(if (c in '0'..'9') FA[c - '0'] else c)
    }

    /** برای ورودی کاربر. */
    fun en(s: String): String = buildString {
        for (c in s) {
            val i = FA.indexOf(c).takeIf { it >= 0 } ?: AR.indexOf(c)
            append(if (i >= 0) ('0' + i) else c)
        }
    }
}
