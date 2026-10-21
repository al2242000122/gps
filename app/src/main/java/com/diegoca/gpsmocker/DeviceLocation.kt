package com.diegoca.gpsmocker

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.location.LocationCompat

/** A foreground-only, bounded request. A mock or old cached fix is never a device position. */
class DeviceLocation(
    private val context: Context,
    private val manager: LocationManager = context.getSystemService(LocationManager::class.java),
    private val elapsedNanos: () -> Long = SystemClock::elapsedRealtimeNanos
) {
    enum class Failure { PERMISSION, DISABLED, UNAVAILABLE }
    private val handler = Handler(Looper.getMainLooper())
    private var onResult: ((Coordinates?, Failure?) -> Unit)? = null
    val searching: Boolean get() = onResult != null
    private val timeout = Runnable { finish(null, Failure.UNAVAILABLE) }
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (usable(location, elapsedNanos())) finish(Coordinates.from(location.latitude, location.longitude), null)
        }
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
        @Deprecated("Legacy LocationListener callback")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    }

    @SuppressLint("MissingPermission") // Both approximate and precise grants are supported below.
    fun locate(callback: (Coordinates?, Failure?) -> Unit) {
        cancel()
        onResult = callback
        if (!MockPermissions.hasLocation(context)) { finish(null, Failure.PERMISSION); return }
        if (!MockPermissions.locationEnabled(context)) { finish(null, Failure.DISABLED); return }
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        val cached = providers.mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .filter { usable(it, elapsedNanos()) }.maxByOrNull { it.elapsedRealtimeNanos }
        if (cached != null) { finish(Coordinates(cached.latitude, cached.longitude), null); return }
        var listening = false
        for (provider in providers) {
            try {
                manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                listening = true
            } catch (_: SecurityException) { /* GPS may need a precise grant; try the network provider too. */ }
              catch (_: IllegalArgumentException) { /* Provider disappeared; try the remaining one. */ }
        }
        if (listening) handler.postDelayed(timeout, TIMEOUT_MILLIS) else finish(null, Failure.UNAVAILABLE)
    }

    fun cancel() {
        onResult = null
        handler.removeCallbacks(timeout)
        runCatching { manager.removeUpdates(listener) }
    }

    private fun finish(point: Coordinates?, failure: Failure?) {
        val callback = onResult ?: return
        cancel()
        callback(point, failure)
    }

    companion object {
        const val TIMEOUT_MILLIS = 15000L
        internal fun usable(location: Location, nowNanos: Long): Boolean {
            val ageNanos = nowNanos - location.elapsedRealtimeNanos
            return !LocationCompat.isMock(location) && location.elapsedRealtimeNanos > 0 &&
                ageNanos in 0..30_000_000_000L && Coordinates.from(location.latitude, location.longitude) != null
        }
    }
}
