package com.diegoca.gpsmocker

import android.Manifest
import android.app.AppOpsManager
import android.app.Application
import android.content.Intent
import android.location.LocationManager
import android.os.Looper
import android.os.Process
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ServiceController
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class MockLocationServiceTest {
    @get:org.junit.Rule val userDataRule = UserDataTestRule()
    private lateinit var controller: ServiceController<TestService>
    private val context: Application get() = RuntimeEnvironment.getApplication()

    @Before fun setup() {
        shadowOf(context).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.FOREGROUND_SERVICE, Manifest.permission.FOREGROUND_SERVICE_LOCATION, Manifest.permission.WAKE_LOCK)
        shadowOf(context.getSystemService(AppOpsManager::class.java)).setMode(AppOpsManager.OPSTR_MOCK_LOCATION,
            Process.myUid(), context.packageName, AppOpsManager.MODE_ALLOWED)
        shadowOf(context.getSystemService(LocationManager::class.java)).setLocationEnabled(true)
        AppPreferences(context).saveRoute(RouteDraft(listOf(Coordinates(0.0, 0.0), Coordinates(0.0, 0.01)), 36.0))
        controller = Robolectric.buildService(TestService::class.java).create()
    }

    @After fun destroy() { controller.destroy() }

    private fun send(action: String) {
        controller.get().onStartCommand(Intent(context, TestService::class.java).setAction(action), 0, 1)
    }

    @Test fun `pause holds location and resume does not include paused time`() {
        send(MockLocationService.ACTION_START_ROUTE)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        send(MockLocationService.ACTION_TOGGLE_PAUSE)
        val pausedPoint = MockLocationService.snapshot!!.coordinates
        assertTrue(MockLocationService.snapshot!!.paused)
        assertEquals(0f, controller.get().backend.fixes.last().speedMetersPerSecond, 0f)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
        assertEquals(pausedPoint, MockLocationService.snapshot!!.coordinates)
        send(MockLocationService.ACTION_TOGGLE_PAUSE)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertFalse(MockLocationService.snapshot!!.paused)
        assertEquals(10.0, RouteNavigator.distance(pausedPoint, MockLocationService.snapshot!!.coordinates), 0.1)
    }

    @Test fun `completion holds the endpoint until explicitly stopped`() {
        AppPreferences(context).saveRoute(RouteDraft(listOf(Coordinates(0.0, 0.0), Coordinates(0.0, 0.0001)), 36.0))
        send(MockLocationService.ACTION_START_ROUTE)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(3))
        assertTrue(MockLocationService.isRunning)
        assertTrue(MockLocationService.snapshot!!.completed)
        assertEquals(Coordinates(0.0, 0.0001), MockLocationService.snapshot!!.coordinates)
        assertEquals(0f, controller.get().backend.fixes.last().speedMetersPerSecond, 0f)
        send(MockLocationService.ACTION_STOP)
        assertFalse(MockLocationService.isRunning)
        assertFalse(SimulationStore(context).cleanupNeeded)
        assertNull(MockLocationService.snapshot)
    }

    @Test fun `failed cleanup reports an error and remains retryable`() {
        send(MockLocationService.ACTION_START_ROUTE)
        controller.get().backend.failRemove = true
        send(MockLocationService.ACTION_STOP)
        assertTrue(SimulationStore(context).hasError)
        assertTrue(SimulationStore(context).cleanupNeeded)
        assertEquals(context.getString(R.string.cleanup_failed), SimulationStore(context).message)
        controller.get().backend.failRemove = false
        send(MockLocationService.ACTION_STOP)
        assertFalse(SimulationStore(context).cleanupNeeded)
        assertFalse(SimulationStore(context).hasError)
    }

    @Test fun `applies changed options without resetting route progress`() {
        send(MockLocationService.ACTION_START_ROUTE)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(2))
        val before = MockLocationService.snapshot!!.coordinates
        val options = SimulationOptions(2500.0, 20f, 500)
        AppPreferences(context).saveOptions(options)
        send(MockLocationService.ACTION_OPTIONS)
        assertEquals(options, controller.get().backend.fixes.last().options)
        assertTrue(RouteNavigator.distance(before, MockLocationService.snapshot!!.coordinates) < 0.01)
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(1))
        assertEquals(10.0, RouteNavigator.distance(before, MockLocationService.snapshot!!.coordinates), 0.1)
    }

    class TestService : MockLocationService() {
        val backend = FakeBackend()
        override fun createBackend(): MockLocationBackend = backend
    }

    class FakeBackend : MockLocationBackend {
        val fixes = mutableListOf<SimulatedFix>()
        var failRemove = false
        override fun install() = Unit
        override fun enable() = Unit
        override fun publish(fix: SimulatedFix) { fixes += fix }
        override fun remove() { if (failRemove) throw SecurityException() }
    }
}
