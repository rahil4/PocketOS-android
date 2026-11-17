package app.pocketos.board.data.repo

import android.content.Context
import android.net.Uri
import app.pocketos.board.data.FileStore
import app.pocketos.board.domain.Contact
import app.pocketos.board.domain.Vcard
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import javax.inject.Inject
import javax.inject.Singleton

data class ShareResult(val items: Int = 0, val contacts: Int = 0, val failed: Int = 0) {
    val nothing: Boolean get() = items == 0 && contacts == 0
}

/**
 * دریافت از «اشتراک‌گذاری» برنامه‌های دیگر: متن و لینک (یادداشت)، عکس/فایل (بایت‌به‌بایت)،
 * و مخاطب vCard (متقاضی). مقصد: «پروندهٔ فعال»؛ اگر نبود صندوق ورودی. لینک دانلود نمی‌شود (اپ اینترنت ندارد).
 */
@Singleton
class ShareHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val items: ItemRepository,
    private val customers: CustomerRepository,
    private val files: FileStore,
    private val active: ActiveTargetRepository,
) {
    private val vcardTypes = setOf("text/vcard", "text/x-vcard", "text/directory", "text/directory;profile=vcard")

    suspend fun importContacts(list: List<Contact>): Int {
        list.forEach { customers.create(it.name, it.phone) }
        return list.size
    }

    suspend fun handle(text: String?, uris: List<Uri>): ShareResult {
        val target = active.current()
        var made = 0
        var contacts = 0
        var failed = 0

        val body = text?.trim().orEmpty()
        if (body.isNotEmpty()) {
            if (Vcard.looksLikeVcard(body)) contacts += importContacts(Vcard.parse(body))
            else {
                items.add(target, ItemKind.TEXT, body)
                made++
            }
        }
        for (uri in uris) {
            try {
                val mime = context.contentResolver.getType(uri)
                val name = uri.lastPathSegment.orEmpty()
                if ((mime != null && mime.lowercase().substringBefore(';') in vcardTypes) || name.endsWith(".vcf", ignoreCase = true)) {
                    contacts += importContacts(Vcard.parse(readText(uri)))
                } else {
                    val stored = files.import(uri)
                    items.add(target, stored.kind, file = stored)
                    made++
                }
            } catch (_: Exception) {
                failed++
            }
        }
        return ShareResult(made, contacts, failed)
    }

    /** vCard اندروید: UTF-8، UTF-16 با BOM، وگرنه windows-1256. */
    private suspend fun readText(uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = requireNotNull(context.contentResolver.openInputStream(uri)) { "فایل خوانده نشد" }.use { it.readBytes() }
        decode(bytes)
    }

    internal fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2 && ((bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) || (bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()))) {
            return String(bytes, Charsets.UTF_16)
        }
        for (name in listOf("UTF-8", "windows-1256")) {
            try {
                return Charset.forName(name).newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
            } catch (_: Exception) {
                continue
            }
        }
        return String(bytes, Charsets.UTF_8)
    }
}
