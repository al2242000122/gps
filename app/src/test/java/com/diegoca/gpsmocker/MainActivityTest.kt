package com.diegoca.gpsmocker

import android.app.AppOpsManager
import android.app.Application
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.android.controller.ActivityController

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28, 35])
class MainActivityTest {
    private val application: Application get() = RuntimeEnvironment.getApplication()

    @Before fun grantInstallTimeSignaturePermission() {
        // Older Robolectric SDKs do not automatically grant an app's own signature
        // permission. Verify the merged manifest, then emulate the install-time grant.
        val permission = "${application.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        val info = application.packageManager.getPackageInfo(application.packageName, PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions.orEmpty().contains(permission))
        assertEquals(PermissionInfo.PROTECTION_SIGNATURE,
            application.packageManager.getPermissionInfo(permission, 0).protectionLevel)
        shadowOf(application).grantPermissions(permission)
    }

    private fun launch(): ActivityController<MainActivity> {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        // The UI tests do not depend on external tile-server availability.
        controller.get().findViewById<MapView>(R.id.map).setUseDataConnection(false)
        return controller.start().resume().visible()
    }

    @Test fun `loads saved selection and applies manual coordinates to the marker`() {
        SimulationStore(application).saveSelection(Coordinates(40.4168, -3.7038))
        val controller = launch()
        val activity = controller.get()
        val map = activity.findViewById<MapView>(R.id.map)
        map.setUseDataConnection(false)
        assertEquals("40.416800", activity.findViewById<EditText>(R.id.latitudeInput).text.toString())
        activity.findViewById<EditText>(R.id.latitudeInput).setText("51,5")
        activity.findViewById<EditText>(R.id.longitudeInput).setText("-0,12")
        activity.findViewById<Button>(R.id.centerButton).performClick()
        val marker = map.overlays.filterIsInstance<Marker>().single()
        assertEquals(51.5, marker.position.latitude, 0.000001)
        assertEquals(-0.12, marker.position.longitude, 0.000001)
        assertEquals(Coordinates(51.5, -0.12), SimulationStore(application).selection())
        controller.pause().stop().destroy()
    }

    @Test fun `invalid coordinates show an error without starting a service`() {
        val controller = launch()
        val activity = controller.get()
        activity.findViewById<MapView>(R.id.map).setUseDataConnection(false)
        activity.findViewById<EditText>(R.id.latitudeInput).setText("91")
        activity.findViewById<Button>(R.id.startButton).performClick()
        assertEquals(activity.getString(R.string.invalid_coordinates), activity.findViewById<TextView>(R.id.statusText).text)
        assertNull(shadowOf(application).nextStartedService)
        controller.pause().stop().destroy()
    }

    @Test fun `missing mock selection opens developer settings without starting simulation`() {
        shadowOf(application.getSystemService(AppOpsManager::class.java)).setMode(
            AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), application.packageName, AppOpsManager.MODE_ERRORED
        )
        val controller = launch()
        val activity = controller.get()
        activity.findViewById<MapView>(R.id.map).setUseDataConnection(false)
        activity.findViewById<Button>(R.id.startButton).performClick()
        assertEquals(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, shadowOf(activity).nextStartedActivity.action)
        assertNull(shadowOf(application).nextStartedService)
        controller.pause().stop().destroy()
    }

    @Test fun `interrupted process offers cleanup instead of claiming simulation is active`() {
        SimulationStore(application).apply {
            cleanupNeeded = true
            writeStatus("Ubicación activa")
        }
        val controller = launch()
        val activity = controller.get()
        activity.findViewById<MapView>(R.id.map).setUseDataConnection(false)
        assertEquals(activity.getString(R.string.simulation_interrupted), activity.findViewById<TextView>(R.id.statusText).text)
        val saved = Bundle()
        controller.saveInstanceState(saved).pause().stop().destroy()
        val recreated = Robolectric.buildActivity(MainActivity::class.java).create(saved)
        recreated.get().findViewById<MapView>(R.id.map).setUseDataConnection(false)
        recreated.start().restoreInstanceState(saved).resume().visible()
        assertFalse(MockLocationService.isRunning)
        assertEquals(true, recreated.get().findViewById<Button>(R.id.stopButton).isEnabled)
        recreated.pause().stop().destroy()
    }
}
