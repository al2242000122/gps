@file:Suppress("DEPRECATION")

package com.diegoca.gpsmocker

import android.annotation.SuppressLint
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock

class AndroidMockLocationBackend(private val manager: LocationManager) : MockLocationBackend {
    override fun install() {
        if (Build.VERSION.SDK_INT >= 31) {
            manager.addTestProvider(LocationManager.GPS_PROVIDER, ProviderProperties.Builder()
                .setHasSatelliteRequirement(true)
                .setHasAltitudeSupport(true)
                .setHasSpeedSupport(true)
                .setHasBearingSupport(true)
                .setPowerUsage(ProviderProperties.POWER_USAGE_LOW)
                .setAccuracy(ProviderProperties.ACCURACY_FINE)
                .build())
        } else {
            installLegacyProvider()
        }
    }

    // API 26–30 use Criteria constants with the same values as the newer IntDef.
    @SuppressLint("WrongConstant")
    private fun installLegacyProvider() {
        manager.addTestProvider(
            LocationManager.GPS_PROVIDER,
            false, true, false, false, true, true, true,
            Criteria.POWER_LOW, Criteria.ACCURACY_FINE
        )
    }

    override fun enable() {
        manager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
    }

    override fun publish(coordinates: Coordinates) {
        val location = Location(LocationManager.GPS_PROVIDER).apply {
            latitude = coordinates.latitude
            longitude = coordinates.longitude
            altitude = 0.0
            accuracy = 3f
            speed = 0f
            bearing = 0f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        }
        manager.setTestProviderLocation(LocationManager.GPS_PROVIDER, location)
    }

    override fun remove() {
        try {
            // Removal restores the real provider. Do not disable the real GPS.
            manager.removeTestProvider(LocationManager.GPS_PROVIDER)
        } catch (_: IllegalArgumentException) {
            // With this fixed, valid provider name this means no test provider exists.
        }
        // SecurityException must reach the caller: restoration was not confirmed.
    }
}
