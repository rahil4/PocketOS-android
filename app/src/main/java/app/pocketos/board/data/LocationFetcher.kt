package app.pocketos.board.data

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/** موقعیت فعلی بدون سرویس‌های گوگل: آخرین موقعیتِ تازه، وگرنه یک موقعیت جدید (حداکثر ۱۵ ثانیه). مجوز را فراخواننده می‌گیرد. */
@Singleton
class LocationFetcher @Inject constructor(@ApplicationContext private val context: Context) {

    @SuppressLint("MissingPermission")
    suspend fun current(): Pair<Double, Double>? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
        if (providers.isEmpty()) return null
        val last = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }.maxByOrNull { it.time }
        if (last != null && System.currentTimeMillis() - last.time < FRESH_MS) return last.latitude to last.longitude

        val provider = if (LocationManager.GPS_PROVIDER in providers) LocationManager.GPS_PROVIDER else providers.first()
        val fresh = withTimeoutOrNull(TIMEOUT_MS) {
            suspendCancellableCoroutine<Location?> { cont ->
                if (Build.VERSION.SDK_INT >= 30) {
                    val signal = CancellationSignal()
                    cont.invokeOnCancellation { signal.cancel() }
                    lm.getCurrentLocation(provider, signal, context.mainExecutor) { if (cont.isActive) cont.resume(it) }
                } else {
                    val listener = object : LocationListener {
                        override fun onLocationChanged(location: Location) {
                            lm.removeUpdates(this)
                            if (cont.isActive) cont.resume(location)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                        override fun onProviderEnabled(provider: String) = Unit
                        override fun onProviderDisabled(provider: String) = Unit
                    }
                    cont.invokeOnCancellation { lm.removeUpdates(listener) }
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
                }
            }
        }
        return (fresh ?: last)?.let { it.latitude to it.longitude }
    }

    private companion object {
        const val FRESH_MS = 2 * 60_000L
        const val TIMEOUT_MS = 15_000L
    }
}
