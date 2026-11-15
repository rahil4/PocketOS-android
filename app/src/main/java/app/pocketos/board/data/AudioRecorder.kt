package app.pocketos.board.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

/** ضبط صدا با ضبط‌کنندهٔ خود اندروید، قالب M4A (AAC). مجوز میکروفون را فراخواننده می‌گیرد. */
class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null

    fun start(file: File) {
        val r = if (Build.VERSION.SDK_INT >= 31) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(96_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(file.absolutePath)
        r.prepare()
        r.start()
        recorder = r
    }

    /** true اگر فایل درست بسته شد (ضبط خیلی کوتاه می‌تواند خطا بدهد). */
    fun stop(): Boolean {
        val r = recorder ?: return false
        recorder = null
        return try {
            r.stop()
            true
        } catch (_: RuntimeException) {
            false
        } finally {
            r.release()
        }
    }
}
