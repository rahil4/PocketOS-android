package app.pocketos.board

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import app.pocketos.board.data.repo.ShareHandler
import app.pocketos.board.domain.Digits
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * مقصد «اشتراک‌گذاری» اندروید: عکس، فایل، متن، لینک و مخاطب (vCard) از برنامه‌های دیگر.
 * بدون صفحه؛ ذخیره می‌کند، پیام می‌دهد و برنامه را باز می‌کند.
 */
@AndroidEntryPoint
class ShareActivity : ComponentActivity() {
    @Inject lateinit var handler: ShareHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = listOfNotNull(intent.getStringExtra(Intent.EXTRA_SUBJECT), intent.getStringExtra(Intent.EXTRA_TEXT))
            .map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString("\n")
        val uris = LinkedHashSet<Uri>()
        IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris += it }
        IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)?.let { uris += it }
        intent.clipData?.let { c -> for (i in 0 until c.itemCount) c.getItemAt(i).uri?.let { uris += it } }

        lifecycleScope.launch {
            // دسترسی به فایل‌های اشتراکی تا بسته‌شدن این صفحه معتبر است؛ پس همین‌جا کامل ذخیره می‌شوند.
            val r = handler.handle(text, uris.toList())
            val msg = buildList {
                if (r.items > 0) add(Digits.fa("${r.items} مورد دریافت شد"))
                if (r.contacts > 0) add(Digits.fa("${r.contacts} متقاضی از مخاطبین افزوده شد"))
                if (r.failed > 0) add(Digits.fa("${r.failed} مورد خوانده نشد"))
            }.joinToString(" · ").ifEmpty { "چیزی برای دریافت نبود" }
            Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()
            if (!r.nothing) {
                startActivity(
                    Intent(this@ShareActivity, MainActivity::class.java)
                        .putExtra(MainActivity.EXTRA_ROUTE, if (r.contacts > 0 && r.items == 0) "customers" else "home")
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                )
            }
            finish()
        }
    }
}
