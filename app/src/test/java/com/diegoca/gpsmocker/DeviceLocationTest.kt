package com.diegoca.gpsmocker

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.core.location.LocationCompat
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class DeviceLocationTest {
    private val context: Application get() = RuntimeEnvironment.getApplication()
    private val manager get() = context.getSystemService(LocationManager::class.java)
    private val now = 1_000_000_000_000L
    private lateinit var reader: DeviceLocation

    @Before fun setup() {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        shadowOf(manager).setLocationEnabled(true)
        shadowOf(manager).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        shadowOf(manager).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        reader = DeviceLocation(context, manager) { now }
    }

    private fun fix(provider: String = LocationManager.GPS_PROVIDER, ageSeconds: Long = 0, mock: Boolean = false) = Location(provider).apply {
        latitude = 19.504413; longitude = -99.182974; accuracy = 10f
        elapsedRealtimeNanos = now - ageSeconds * 1_000_000_000
        @Suppress("DEPRECATION")
        LocationCompat.setMock(this, mock)
    }

    @Test fun `rejects mock stale future and invalid positions`() {
        assertTrue(DeviceLocation.usable(fix(), now))
        assertTrue(DeviceLocation.usable(fix(ageSeconds = 30), now))
        assertFalse(DeviceLocation.usable(fix(mock = true), now))
        assertFalse(DeviceLocation.usable(fix(ageSeconds = 31), now))
        assertFalse(DeviceLocation.usable(fix(ageSeconds = -1), now))
        assertFalse(DeviceLocation.usable(fix().apply { latitude = Double.NaN }, now))
    }

    @Test fun `uses a fresh non mock network fix with approximate permission`() {
        shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        shadowOf(manager).setLastKnownLocation(LocationManager.GPS_PROVIDER, fix(mock = true))
        shadowOf(manager).setLastKnownLocation(LocationManager.NETWORK_PROVIDER, fix(LocationManager.NETWORK_PROVIDER, ageSeconds = 5))
        var result: Coordinates? = null
        reader.locate { point, failure -> result = point; assertNull(failure) }
        assertEquals(Coordinates(19.504413, -99.182974), result)
        assertFalse(reader.searching)
    }

    @Test fun `times out rather than accepting stale or mocked cached positions`() {
        shadowOf(manager).setLastKnownLocation(LocationManager.GPS_PROVIDER, fix(mock = true))
        shadowOf(manager).setLastKnownLocation(LocationManager.NETWORK_PROVIDER, fix(LocationManager.NETWORK_PROVIDER, ageSeconds = 31))
        var callbacks = 0
        reader.locate { point, failure ->
            callbacks++; assertNull(point); assertEquals(DeviceLocation.Failure.UNAVAILABLE, failure)
        }
        assertTrue(reader.searching)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(DeviceLocation.TIMEOUT_MILLIS + 1))
        assertEquals(1, callbacks)
        assertFalse(reader.searching)
    }

    @Test fun `accepts a live real fix and removes the listener after finishing`() {
        var callbacks = 0
        reader.locate { point, failure -> callbacks++; assertNotNull(point); assertNull(failure) }
        shadowOf(manager).simulateLocation(fix(mock = true))
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(0, callbacks)
        shadowOf(manager).simulateLocation(fix())
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(1, callbacks)
        shadowOf(manager).simulateLocation(fix())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(DeviceLocation.TIMEOUT_MILLIS + 1))
        assertEquals(1, callbacks)
    }

    @Test fun `cancel prevents late positions and timeout callbacks`() {
        var callbacks = 0
        reader.locate { _, _ -> callbacks++ }
        reader.cancel()
        shadowOf(manager).simulateLocation(fix())
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(DeviceLocation.TIMEOUT_MILLIS + 1))
        assertEquals(0, callbacks)
        assertFalse(reader.searching)
    }

    @Test fun `reports disabled location and missing permission without listening`() {
        shadowOf(manager).setLocationEnabled(false)
        reader.locate { point, failure -> assertNull(point); assertEquals(DeviceLocation.Failure.DISABLED, failure) }
        shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        reader.locate { point, failure -> assertNull(point); assertEquals(DeviceLocation.Failure.PERMISSION, failure) }
        assertFalse(reader.searching)
    }

    @Test fun `migrates automatic dark map once and keeps later explicit choices`() {
        context.getSharedPreferences("preferences", 0).edit().putString("map_style", "auto").apply()
        val preferences = AppPreferences(context)
        assertEquals("light", preferences.mapStyle)
        preferences.mapStyle = "auto"
        assertEquals("auto", AppPreferences(context).mapStyle)
        preferences.mapStyle = "dark"
        assertEquals("dark", AppPreferences(context).mapStyle)
    }
}
