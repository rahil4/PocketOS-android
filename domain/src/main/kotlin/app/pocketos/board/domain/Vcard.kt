package app.pocketos.board.domain

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

data class Contact(val name: String, val phone: String)

/**
 * خواندن کارت‌های vCard (مخاطب اشتراکی): FN را ترجیح می‌دهد و در نبودش N؛ نخستین TEL. تا ۲۰۰ مخاطب.
 * vCard 2.1 اندروید نام فارسی را با `ENCODING=QUOTED-PRINTABLE;CHARSET=UTF-8` (و ادامهٔ خط با «=») می‌نویسد.
 * همان `parse_vcards` وب‌اپ.
 */
object Vcard {
    const val MAX = 200
    private val FOLD = Regex("\\r?\\n[ \\t]")
    private val CARD = Regex("BEGIN:VCARD(.*?)END:VCARD", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val CHARSET = Regex("CHARSET=([\\w-]+)", RegexOption.IGNORE_CASE)

    fun looksLikeVcard(text: String?): Boolean = text != null && text.contains("BEGIN:VCARD", ignoreCase = true)

    private fun decodeQp(s: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == '=' && i + 2 < s.length + 0 && s.length - i >= 3 && s[i + 1].isHexDigit() && s[i + 2].isHexDigit()) {
                out.write(s.substring(i + 1, i + 3).toInt(16))
                i += 3
            } else {
                out.write(c.code and 0xFF)
                i++
            }
        }
        return out.toByteArray()
    }

    private fun Char.isHexDigit() = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun decodeValue(key: String, value: String): String {
        val params = key.uppercase()
        if ("QUOTED-PRINTABLE" !in params) return value
        val declared = CHARSET.find(params)?.groupValues?.get(1)
        val raw = decodeQp(value)
        for (name in listOfNotNull(declared, "UTF-8", "windows-1256")) {
            try {
                val dec = Charset.forName(name).newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                return dec.decode(ByteBuffer.wrap(raw)).toString()
            } catch (_: Exception) {
                continue
            }
        }
        return String(raw, Charsets.UTF_8)
    }

    fun parse(text: String?): List<Contact> {
        val unfolded = FOLD.replace(text.orEmpty(), "")
        val out = ArrayList<Contact>()
        for (m in CARD.findAll(unfolded)) {
            var fn = ""
            var n = ""
            var tel = ""
            val lines = ArrayList<String>()
            var buf = ""
            for (ln in m.groupValues[1].lines()) { // «=» آخر خط = ادامهٔ همان مقدار (QP)
                buf += ln.trimEnd('\r')
                if ("QUOTED-PRINTABLE" in buf.uppercase() && buf.endsWith("=")) {
                    buf = buf.dropLast(1)
                    continue
                }
                lines += buf
                buf = ""
            }
            if (buf.isNotEmpty()) lines += buf
            for (line in lines) {
                val key = line.substringBefore(':', "")
                if (key.isEmpty() || ':' !in line) continue
                val value = decodeValue(key, line.substringAfter(':'))
                when (key.substringBefore(';').trim().uppercase()) {
                    "FN" -> if (fn.isEmpty()) fn = value.trim()
                    "N" -> if (n.isEmpty()) {
                        val p = value.split(';').map { it.trim() }
                        n = (listOf(p.getOrNull(1), p.getOrNull(0))).filter { !it.isNullOrEmpty() }.joinToString(" ")
                    }
                    "TEL" -> if (tel.isEmpty()) tel = value.trim()
                }
            }
            val name = fn.ifEmpty { n }
            if (name.isNotBlank()) out += Contact(Normalize.clean(name).replace('ي', 'ی').replace('ك', 'ک'), Phone.normalize(tel))
            if (out.size >= MAX) break
        }
        return out
    }
}
