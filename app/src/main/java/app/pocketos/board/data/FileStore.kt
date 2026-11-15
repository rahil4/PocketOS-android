package app.pocketos.board.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** فایلِ ذخیره‌شده: [path] فقط نام (uuid) داخل پوشهٔ خصوصی اپ است؛ [name] نام اصلی و فقط برای نمایش. */
data class StoredFile(val path: String, val name: String, val mime: String) {
    val kind: String
        get() = when {
            mime.startsWith("image/") -> "photo"
            mime.startsWith("audio/") -> "audio"
            else -> "file"
        }
}

/**
 * فایل‌های عکس/صدا/سند در حافظهٔ خصوصی اپ. فایل بایت‌به‌بایت کپی می‌شود (بدون کوچک‌کردن یا فشرده‌سازی مجدد).
 * مسیرها فقط با uuid ساخته می‌شوند.
 */
@Singleton
class FileStore @Inject constructor(@ApplicationContext private val context: Context) {
    private val dir: File = File(context.filesDir, "files").also { it.mkdirs() }

    fun file(path: String): File = File(dir, File(path).name)

    fun delete(path: String) {
        runCatching { file(path).delete() }
    }

    fun uriFor(path: String): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file(path))

    /** فایل خالی برای دوربین یا ضبط صدا. */
    fun create(name: String, mime: String): StoredFile {
        val id = UUID.randomUUID().toString()
        File(dir, id).createNewFile()
        return StoredFile(id, name, mime)
    }

    suspend fun import(uri: Uri): StoredFile = withContext(Dispatchers.IO) {
        val cr = context.contentResolver
        val mime = cr.getType(uri) ?: "application/octet-stream"
        var name: String? = null
        runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) name = it.getString(0)
            }
        }
        val id = UUID.randomUUID().toString()
        val dest = File(dir, id)
        try {
            val input = requireNotNull(cr.openInputStream(uri)) { "فایل خوانده نشد" }
            input.use { src -> dest.outputStream().use { out -> src.copyTo(out) } }
        } catch (e: Exception) {
            dest.delete()
            throw e
        }
        StoredFile(id, name ?: id, mime)
    }
}
