package app.pocketos.board.domain

/** نرمال‌سازی متن فارسی برای جست‌وجو و مقایسه (همان `when.norm` وب‌اپ). */
object Normalize {
    private val SPACES = Regex("\\s+")
    private val PUNCT = Regex("[،,.؟?!؛;«»\"'()\\[\\]{}:：]")

    /** ارقام لاتین، ی/ک فارسی، نیم‌فاصله ← فاصله، فاصله‌های اضافه حذف. */
    fun norm(s: String?): String =
        SPACES.replace(Digits.en(s.orEmpty()).replace('‌', ' ').replace('ي', 'ی').replace('ك', 'ک'), " ").trim()

    /** برای ذخیره: فاصله‌های اضافه حذف می‌شود ولی نیم‌فاصله و ارقام کاربر دست‌نخورده می‌ماند. */
    fun clean(s: String?): String = SPACES.replace(s.orEmpty(), " ").trim()

    /** مثل norm ولی طول و جای نویسه‌ها حفظ می‌شود (برای بریدن بخشی از متن اصلی). */
    internal fun aligned(s: String): String =
        Digits.en(s).replace('\u200c', ' ').replace('ي', 'ی').replace('ك', 'ک')

    /** کلید مقایسه و جست‌وجو: norm + حروف کوچک. */
    fun key(s: String?): String = norm(s).lowercase()

    /** واژه‌ها بدون نشانه‌گذاری. */
    fun words(s: String?): List<String> =
        PUNCT.replace(key(s), " ").split(' ').filter { it.isNotEmpty() }
}

/** شمارهٔ تلفن ایران. */
object Phone {
    /** به شکل `09121234567` می‌برد؛ اگر شمارهٔ ایرانی نبود همان ارقام را نگه می‌دارد. */
    fun normalize(raw: String?): String {
        var d = Digits.en(raw.orEmpty()).filter { it.isDigit() || it == '+' }
        d = when {
            d.startsWith("+98") -> "0" + d.substring(3)
            d.startsWith("0098") -> "0" + d.substring(4)
            d.startsWith("98") && d.length == 12 -> "0" + d.substring(2)
            d.length == 10 && d.startsWith("9") -> "0$d"
            else -> d
        }
        return d
    }

    /** نمایش فارسی: ۰۹۱۲ ۳۴۵ ۶۷۸۹ */
    fun display(phone: String?): String {
        val d = normalize(phone)
        return if (d.length == 11 && d.startsWith("09")) {
            Digits.fa("${d.substring(0, 4)} ${d.substring(4, 7)} ${d.substring(7)}")
        } else Digits.fa(d)
    }
}
