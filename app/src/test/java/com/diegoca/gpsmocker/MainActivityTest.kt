package com.diegoca.gpsmocker

import android.app.AppOpsManager
import android.app.Application
import android.Manifest
import android.content.ClipboardManager
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.SystemClock
import android.location.Location
import android.location.LocationManager
import android.os.Process
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputLayout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import android.webkit.WebView
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.android.controller.ActivityController
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowSystemClock
import java.time.Duration
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35, 36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MainActivityTest {
    @get:org.junit.Rule val userDataRule = UserDataTestRule()
    private val application: Application get() = RuntimeEnvironment.getApplication()

    @Suppress("DEPRECATION")
    @Before fun grantInstallTimeSignaturePermission() {
        val permission = "${application.packageName}.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION"
        val info = application.packageManager.getPackageInfo(application.packageName, PackageManager.GET_PERMISSIONS)
        assertTrue(info.requestedPermissions.orEmpty().contains(permission))
        assertEquals(PermissionInfo.PROTECTION_SIGNATURE, application.packageManager.getPermissionInfo(permission, 0).protectionLevel)
        shadowOf(application).grantPermissions(permission)
    }

    private fun launch(): ActivityController<MainActivity> {
        val controller = Robolectric.buildActivity(MainActivity::class.java).create()
        settleDataWork()
        controller.get().findViewById<WebView>(R.id.map).settings.blockNetworkLoads = true
        controller.start().resume().visible()
        settleDataWork()
        layout(controller.get())
        return controller
    }

    private fun layout(activity: MainActivity, width: Int = 1080, height: Int = 2200) {
        val decor = activity.window.decorView
        repeat(3) {
            views(decor).forEach { it.forceLayout() }
            decor.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY))
            decor.layout(0, 0, width, height)
        }
    }

    @Test fun `coordinates are read only and loaded from the selected place`() {
        SimulationStore(application).apply { saveSelection(Coordinates(40.4168, -3.7038)); selectionName = "Madrid" }
        val controller = launch()
        val activity = controller.get()
        val coordinates = activity.findViewById<TextView>(R.id.coordinatesText)
        assertEquals("40.416800, -3.703800", coordinates.text.toString())
        assertFalse(coordinates is EditText)
        assertEquals(0, views(activity.findViewById(R.id.contentRoot)).filterIsInstance<EditText>().size)
        assertEquals("Madrid", activity.findViewById<TextView>(R.id.placeTitle).text.toString())
        controller.pause().stop().destroy()
    }

    @Test fun `manual coordinates select a point without starting simulation or deleting a saved route`() {
        val route = RouteDraft(listOf(Coordinates(40.0, -3.0), Coordinates(40.01, -3.01)))
        val preferences = AppPreferences(application)
        preferences.saveRoute(route)
        preferences.routeWorkspace = true
        val controller = launch()
        val activity = controller.get()
        activity.findViewById<Button>(R.id.editCoordinatesButton).clickAndSettle()
        val dialog = ShadowDialog.getLatestDialog()
        dialog.findViewById<EditText>(R.id.manualLatitudeInput).setText("-12,5")
        dialog.findViewById<EditText>(R.id.manualLongitudeInput).setText("179.25")
        dialog.findViewById<Button>(R.id.applyManualCoordinates).clickAndSettle()
        assertFalse(dialog.isShowing)
        assertEquals(Coordinates(-12.5, 179.25), SimulationStore(application).selection())
        assertEquals("-12.500000, 179.250000", activity.findViewById<TextView>(R.id.coordinatesText).text.toString())
        assertEquals(route, preferences.route())
        assertFalse(preferences.routeWorkspace)
        assertFalse(MockLocationService.isRunning)
        assertNull(shadowOf(application).nextStartedService)
        controller.pause().stop().destroy()
    }

    @Test fun `manual selection leaves a running route and its GPS position untouched`() {
        shadowOf(application).grantPermissions(
            Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.FOREGROUND_SERVICE, Manifest.permission.FOREGROUND_SERVICE_LOCATION,
            Manifest.permission.WAKE_LOCK
        )
        shadowOf(application.getSystemService(AppOpsManager::class.java)).setMode(
            AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), application.packageName, AppOpsManager.MODE_ALLOWED)
        shadowOf(application.getSystemService(LocationManager::class.java)).setLocationEnabled(true)
        val route = RouteDraft(listOf(Coordinates(0.0, 0.0), Coordinates(0.0, 0.01)), 36.0)
        AppPreferences(application).saveRoute(route)
        val service = Robolectric.buildService(MockLocationServiceTest.TestService::class.java).create()
        service.get().onStartCommand(
            Intent(application, MockLocationServiceTest.TestService::class.java).setAction(MockLocationService.ACTION_START_ROUTE),
            0, 1
        )
        val controller = launch()
        try {
            val before = MockLocationService.snapshot
            val publications = service.get().backend.fixes.size
            controller.get().findViewById<Button>(R.id.editCoordinatesButton).clickAndSettle()
            val dialog = ShadowDialog.getLatestDialog()
            dialog.findViewById<EditText>(R.id.manualLatitudeInput).setText("10")
            dialog.findViewById<EditText>(R.id.manualLongitudeInput).setText("20")
            dialog.findViewById<Button>(R.id.applyManualCoordinates).clickAndSettle()
            assertFalse(dialog.isShowing)
            assertEquals(Coordinates(10.0, 20.0), SimulationStore(application).selection())
            assertTrue(MockLocationService.isRunning)
            assertEquals(before, MockLocationService.snapshot)
            assertEquals(publications, service.get().backend.fixes.size)
            assertEquals(route, AppPreferences(application).route())
            assertNull(shadowOf(application).nextStartedService)
        } finally {
            controller.pause().stop().destroy()
            service.destroy()
        }
    }

    @Test fun `invalid manual coordinates show field errors and preserve existing selection`() {
        val previous = Coordinates(40.4168, -3.7038)
        SimulationStore(application).saveSelection(previous)
        val controller = launch()
        controller.get().findViewById<Button>(R.id.editCoordinatesButton).clickAndSettle()
        val dialog = ShadowDialog.getLatestDialog()
        val latitude = dialog.findViewById<EditText>(R.id.manualLatitudeInput)
        val longitude = dialog.findViewById<EditText>(R.id.manualLongitudeInput)
        for ((lat, lon) in listOf("91" to "0", "-90.1" to "0", "0" to "181", "0" to "-180.1", "-" to "", "1,2,3" to "0")) {
            latitude.setText(lat); longitude.setText(lon)
            dialog.findViewById<Button>(R.id.applyManualCoordinates).clickAndSettle()
            assertTrue(dialog.isShowing)
            assertEquals(previous, SimulationStore(application).selection())
            assertTrue(views(dialog.window!!.decorView).filterIsInstance<TextInputLayout>().any { it.error != null })
        }
        assertFalse(MockLocationService.isRunning)
        assertNull(shadowOf(application).nextStartedService)
        controller.pause().stop().destroy()
    }

    @Test fun `cancelling manual coordinates preserves the point and route workspace`() {
        val previous = Coordinates(40.4168, -3.7038)
        SimulationStore(application).saveSelection(previous)
        AppPreferences(application).routeWorkspace = true
        val controller = launch()
        controller.get().findViewById<Button>(R.id.editCoordinatesButton).clickAndSettle()
        val dialog = ShadowDialog.getLatestDialog()
        assertEquals("40.416800", dialog.findViewById<EditText>(R.id.manualLatitudeInput).text.toString())
        dialog.findViewById<EditText>(R.id.manualLatitudeInput).setText("0")
        views(dialog.window!!.decorView).filterIsInstance<Button>()
            .single { it.text.toString() == application.getString(R.string.cancel) }.clickAndSettle()
        assertFalse(dialog.isShowing)
        assertEquals(previous, SimulationStore(application).selection())
        assertTrue(AppPreferences(application).routeWorkspace)
        assertNull(shadowOf(application).nextStartedService)
        controller.pause().stop().destroy()
    }

    @Test fun `manual coordinate draft survives recreation without committing or reopening after cancel`() {
        val previous = Coordinates(40.4168, -3.7038)
        SimulationStore(application).saveSelection(previous)
        val controller = launch()
        controller.get().findViewById<Button>(R.id.editCoordinatesButton).clickAndSettle()
        var dialog = ShadowDialog.getLatestDialog()
        dialog.findViewById<EditText>(R.id.manualLatitudeInput).setText("-")
        dialog.findViewById<EditText>(R.id.manualLongitudeInput).setText("19,43")
        controller.recreate()
        settleDataWork()
        dialog = ShadowDialog.getLatestDialog()
        assertTrue(dialog.isShowing)
        assertEquals("-", dialog.findViewById<EditText>(R.id.manualLatitudeInput).text.toString())
        assertEquals("19,43", dialog.findViewById<EditText>(R.id.manualLongitudeInput).text.toString())
        assertEquals(previous, SimulationStore(application).selection())
        dialog.dismiss()
        controller.recreate()
        settleDataWork()
        assertFalse(ShadowDialog.getLatestDialog().isShowing)
        assertEquals(previous, SimulationStore(application).selection())
        controller.pause().stop().destroy()
    }

    @Test fun `manual editor stays open if restoring data blocks the selection`() {
        val controller = launch()
        controller.get().findViewById<Button>(R.id.editCoordinatesButton).clickAndSettle()
        val dialog = ShadowDialog.getLatestDialog()
        dialog.findViewById<EditText>(R.id.manualLatitudeInput).setText("10")
        dialog.findViewById<EditText>(R.id.manualLongitudeInput).setText("20")
        BackupGuard.restoring.set(true)
        try {
            dialog.findViewById<Button>(R.id.applyManualCoordinates).clickAndSettle()
            assertTrue(dialog.isShowing)
            assertEquals(Coordinates.DEFAULT, SimulationStore(application).selection())
            assertNull(shadowOf(application).nextStartedService)
        } finally { BackupGuard.restoring.set(false) }
        controller.pause().stop().destroy()
    }

    @Test fun `map tap changes selection and copy uses the selected coordinates`() {
        val controller = launch()
        val activity = controller.get()
        tap(activity, Coordinates(19.433, -99.132))
        val selected = SimulationStore(application).selection()
        assertEquals(19.433, selected.latitude, 0.00005)
        assertEquals(-99.132, selected.longitude, 0.00005)
        activity.findViewById<Button>(R.id.copyButton).clickAndSettle()
        val copied = activity.getSystemService(ClipboardManager::class.java).primaryClip!!.getItemAt(0).text.toString()
        assertEquals(activity.findViewById<TextView>(R.id.coordinatesText).text.toString(), copied)
        controller.pause().stop().destroy()
    }

    @Test fun `developer guide requests scrolling and highlighting of the mock location preference`() {
        shadowOf(application.getSystemService(AppOpsManager::class.java)).setMode(
            AppOpsManager.OPSTR_MOCK_LOCATION, Process.myUid(), application.packageName, AppOpsManager.MODE_ERRORED)
        val controller = launch()
        controller.get().findViewById<Button>(R.id.startButton).clickAndSettle()
        val dialog = ShadowDialog.getLatestDialog() as AlertDialog
        assertTrue(dialog.isShowing)
        assertEquals(controller.get().getString(R.string.open_settings_action), dialog.getButton(DialogInterface.BUTTON_POSITIVE).text.toString())
        val intent = DeveloperSettings.mockLocationIntent()
        assertEquals(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS, intent.action)
        assertEquals("mock_location_app", intent.getStringExtra(DeveloperSettings.FRAGMENT_KEY))
        assertEquals("mock_location_app", intent.getBundleExtra(DeveloperSettings.FRAGMENT_ARGUMENTS)?.getString(DeveloperSettings.FRAGMENT_KEY))
        assertNull(shadowOf(application).nextStartedService)
        controller.pause().stop().destroy()
    }

    @Test fun `choosing a saved favorite restores its point on the map`() {
        PlaceLibrary(application).saveFavorite("Oficina", Coordinates(40.0, -3.0))
        val controller = launch()
        controller.get().findViewById<Button>(R.id.libraryButton).clickAndSettle()
        val dialog = ShadowDialog.getLatestDialog()
        val list = views(dialog.window!!.decorView).filterIsInstance<ListView>().single()
        list.performItemClick(null, 0, 0)
        settleDataWork()
        assertEquals(Coordinates(40.0, -3.0), SimulationStore(application).selection())
        assertEquals("Oficina", controller.get().findViewById<TextView>(R.id.placeTitle).text.toString())
        controller.pause().stop().destroy()
    }

    @Test fun `route editor collects map points and survives recreation`() {
        val controller = launch()
        val activity = controller.get()
        activity.findViewById<Button>(R.id.routesButton).clickAndSettle()
        val dialog = ShadowDialog.getLatestDialog()
        views(dialog.window!!.decorView).filterIsInstance<MaterialButton>()
            .first { it.text.toString() == activity.getString(R.string.draw_route) }.clickAndSettle()
        tap(activity, Coordinates(19.433, -99.132))
        tap(activity, Coordinates(19.434, -99.131))
        assertEquals(3, AppPreferences(application).route().points.size)
        assertEquals(View.VISIBLE, activity.findViewById<View>(R.id.routeBanner).visibility)
        val saved = Bundle()
        controller.saveInstanceState(saved).pause().stop().destroy()
        val recreated = Robolectric.buildActivity(MainActivity::class.java).create(saved)
        settleDataWork()
        recreated.get().findViewById<WebView>(R.id.map).settings.blockNetworkLoads = true
        recreated.start().restoreInstanceState(saved).resume().visible()
        assertEquals(3, AppPreferences(application).route().points.size)
        assertEquals(View.VISIBLE, recreated.get().findViewById<View>(R.id.routeBanner).visibility)
        recreated.pause().stop().destroy()
    }

    @Test fun `interrupted process offers cleanup instead of claiming it is active`() {
        SimulationStore(application).apply { cleanupNeeded = true; writeStatus("Ubicación activa") }
        val controller = launch()
        assertEquals(controller.get().getString(R.string.simulation_interrupted), controller.get().findViewById<TextView>(R.id.statusText).text)
        assertFalse(MockLocationService.isRunning)
        assertEquals(View.VISIBLE, controller.get().findViewById<View>(R.id.stopButton).visibility)
        controller.pause().stop().destroy()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h891dp-xxhdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `primary controls fit a phone and produce a visual preview`() {
        val controller = launch()
        val activity = controller.get()
        layout(activity, 1233, 2673)
        val start = activity.findViewById<View>(R.id.startButton)
        assertTrue(start.height >= 48 * activity.resources.displayMetrics.density)
        assertTrue(activity.findViewById<View>(R.id.locationCard).height < activity.findViewById<View>(R.id.contentRoot).height * 0.65)
        val decor = activity.window.decorView
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(bitmap))
        val directory = File("build/outputs/previews").apply { mkdirs() }
        StoreAssets.export(activity)
        File(directory, "home-light.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        controller.pause().stop().destroy()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w800dp-h411dp-land-mdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `landscape places the location card beside the map controls`() {
        val controller = launch()
        val activity = controller.get()
        layout(activity, 800, 411)
        val search = activity.findViewById<View>(R.id.searchCard)
        val card = activity.findViewById<View>(R.id.locationCard)
        capture(activity, "home-landscape.png")
        assertTrue("root=${activity.findViewById<View>(R.id.contentRoot).width}x${activity.findViewById<View>(R.id.contentRoot).height} search=${search.left}..${search.right} card=${card.left}..${card.right} density=${activity.resources.displayMetrics.density}", search.right < card.left)
        assertTrue(card.bottom <= activity.findViewById<View>(R.id.contentRoot).height)
        controller.pause().stop().destroy()
    }

    @Test
    @Config(sdk = [35], qualifiers = "w411dp-h891dp-night-xxhdpi")
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `dark system theme keeps a light map by default`() {
        val controller = launch()
        val activity = controller.get()
        layout(activity, 1233, 2673)
        val bridge = shadowOf(activity.findViewById<WebView>(R.id.map)).getJavascriptInterface(MapWorkspace.BRIDGE_NAME) as MapWorkspace.Bridge
        assertTrue(org.json.JSONObject(bridge.initialState()).getString("style").endsWith("/liberty"))
        assertFalse(activity.findViewById<WebView>(R.id.map).isForceDarkAllowed)
        capture(activity, "home-dark.png")
        controller.pause().stop().destroy()
    }

    @Test fun `opening centers and selects the current real position without starting simulation`() {
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val manager = application.getSystemService(LocationManager::class.java)
        shadowOf(manager).setLocationEnabled(true)
        ShadowSystemClock.advanceBy(Duration.ofSeconds(60))
        shadowOf(manager).setLastKnownLocation(LocationManager.GPS_PROVIDER, Location(LocationManager.GPS_PROVIDER).apply {
            latitude = 20.0; longitude = -100.0; elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos(); accuracy = 5f
        })
        SimulationStore(application).saveSelection(Coordinates(40.0, -3.0))
        val controller = launch()
        assertEquals(Coordinates(20.0, -100.0), SimulationStore(application).selection())
        val bridge = shadowOf(controller.get().findViewById<WebView>(R.id.map)).getJavascriptInterface(MapWorkspace.BRIDGE_NAME) as MapWorkspace.Bridge
        // GPS can resolve while the WebView is still loading. Deliver its camera command when ready.
        bridge.onReady()
        val center = org.json.JSONObject(bridge.initialState()).getJSONArray("center")
        assertEquals(-100.0, center.getDouble(0), 0.00001)
        assertEquals(20.0, center.getDouble(1), 0.00001)
        val camera = org.json.JSONObject(bridge.initialState()).getJSONObject("camera").getJSONArray("center")
        assertEquals(-100.0, camera.getDouble(0), 0.00001)
        assertEquals(20.0, camera.getDouble(1), 0.00001)
        assertFalse(MockLocationService.isRunning)
        assertNull(shadowOf(application).nextStartedService)
        // Recreating the screen must retain a point chosen manually after the initial fix.
        tap(controller.get(), Coordinates(21.0, -101.0))
        val saved = Bundle()
        controller.saveInstanceState(saved).pause().stop().destroy()
        val recreated = Robolectric.buildActivity(MainActivity::class.java).create(saved)
        settleDataWork()
        recreated.get().findViewById<WebView>(R.id.map).settings.blockNetworkLoads = true
        recreated.start().resume()
        settleDataWork()
        assertEquals(Coordinates(21.0, -101.0), SimulationStore(application).selection())
        recreated.pause().stop().destroy()
    }

    @Test fun `location permission response only locates and never starts simulation`() {
        val controller = launch()
        val request = shadowOf(controller.get()).lastRequestedPermission
        assertEquals(102, request.requestCode)
        controller.get().onRequestPermissionsResult(request.requestCode, request.requestedPermissions,
            IntArray(request.requestedPermissions.size) { PackageManager.PERMISSION_DENIED })
        assertFalse(MockLocationService.isRunning)
        assertNull(shadowOf(application).nextStartedService)
        tap(controller.get(), Coordinates(19.0, -99.0))
        assertEquals(Coordinates(19.0, -99.0), SimulationStore(application).selection())
        controller.pause().stop().destroy()
    }


    @Test @Config(sdk = [35])
    fun `backup export uses document picker without broad storage permission`() {
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val controller = launch();val activity=controller.get()
        activity.findViewById<View>(R.id.settingsButton).clickAndSettle()
        val settings=ShadowDialog.getLatestDialog()
        views(settings.window!!.decorView).filterIsInstance<MaterialButton>().first { it.text.toString()==activity.getString(R.string.backup_restore) }.clickAndSettle()
        val backup=ShadowDialog.getLatestDialog() as AlertDialog
        backup.getButton(DialogInterface.BUTTON_POSITIVE).clickAndSettle()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val request=shadowOf(activity).nextStartedActivityForResult
        assertEquals(android.content.Intent.ACTION_CREATE_DOCUMENT,request.intent.action)
        assertEquals("application/json",request.intent.type)
        assertEquals(View.GONE,activity.findViewById<View>(R.id.adContainer).visibility)
        controller.pause().stop().destroy()
    }

    @Test @Config(sdk = [35])
    fun `restore rechecks pending GPS cleanup after preview`() {
        shadowOf(application).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        val controller=launch();val activity=controller.get()
        val file=File(application.cacheDir,"restore.json")
        file.outputStream().use { BackupCodec.write(it,UserData(selectionName="Restaurado")) }
        val before=UserDataRepository.get(application).snapshot()
        activity.findViewById<View>(R.id.settingsButton).clickAndSettle()
        views(ShadowDialog.getLatestDialog().window!!.decorView).filterIsInstance<MaterialButton>()
            .first { it.text.toString()==activity.getString(R.string.backup_restore) }.clickAndSettle()
        val backup=ShadowDialog.getLatestDialog() as AlertDialog
        backup.getButton(DialogInterface.BUTTON_NEUTRAL).clickAndSettle()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        val request=shadowOf(activity).nextStartedActivityForResult
        shadowOf(activity).receiveResult(request.intent,android.app.Activity.RESULT_OK,android.content.Intent().setData(android.net.Uri.fromFile(file)))
        val deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(5)
        while (ShadowDialog.getLatestDialog() === backup && System.nanoTime()<deadline) {
            shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(10)
        }
        val preview=ShadowDialog.getLatestDialog() as AlertDialog
        assertNotSame(backup,preview)
        SimulationStore(application).cleanupNeeded=true
        preview.getButton(DialogInterface.BUTTON_POSITIVE).clickAndSettle()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals(before,UserDataRepository.get(application).snapshot())
        assertFalse(BackupGuard.restoring.get())
        controller.pause().stop().destroy()
    }

    @Test @Config(sdk = [35])
    fun `damaged storage opens recovery instead of replacing saved places`() {
        val file=File(application.filesDir,"userdata/user-data.json");file.parentFile!!.mkdirs();file.writeText("broken")
        val controller=Robolectric.buildActivity(MainActivity::class.java).create()
        settleDataWork()
        val next=shadowOf(controller.get()).nextStartedActivity
        assertEquals(DataRecoveryActivity::class.java.name,next.component!!.className)
        assertEquals("broken",file.readText())
        controller.destroy()
    }


    @Test @Config(sdk = [35])
    fun `switching an imported provider geometry to direct mode preserves attribution`() {
        AppPreferences(application).saveRoute(RouteDraft(listOf(Coordinates.DEFAULT,Coordinates(19.44,-99.14)),mode=RouteMode.IMPORTED,source="geoapify"))
        val controller=launch();val activity=controller.get()
        activity.findViewById<View>(R.id.routesButton).clickAndSettle()
        val dialog=ShadowDialog.getLatestDialog()
        views(dialog.window!!.decorView).filterIsInstance<android.widget.Spinner>().first().setSelection(RouteMode.DIRECT.ordinal)
        views(dialog.window!!.decorView).filterIsInstance<MaterialButton>()
            .first { it.text.toString()==activity.getString(R.string.edit_route) }.clickAndSettle()
        assertEquals(RouteMode.DIRECT,AppPreferences(application).route().mode)
        assertEquals("geoapify",AppPreferences(application).route().source)
        controller.pause().stop().destroy()
    }

    @Test fun `manual commit finishes after recreation and closes the restored editor`() {
        AppPreferences(application).routeWorkspace = true
        val controller = launch()
        controller.get().findViewById<Button>(R.id.editCoordinatesButton).clickAndSettle()
        val dialog = ShadowDialog.getLatestDialog()
        dialog.findViewById<EditText>(R.id.manualLatitudeInput).setText("10")
        dialog.findViewById<EditText>(R.id.manualLongitudeInput).setText("20")
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        UserDataWork.submit({
            entered.countDown()
            check(release.await(5, java.util.concurrent.TimeUnit.SECONDS))
        }, {})
        try {
            assertTrue(entered.await(2, java.util.concurrent.TimeUnit.SECONDS))
            dialog.findViewById<Button>(R.id.applyManualCoordinates).performClick()
            controller.recreate()
            release.countDown()
            settleDataWork()
            assertEquals(Coordinates(10.0, 20.0), SimulationStore(application).selection())
            assertFalse(AppPreferences(application).routeWorkspace)
            assertFalse(ShadowDialog.getLatestDialog().isShowing)
            assertEquals("10.000000, 20.000000", controller.get().findViewById<TextView>(R.id.coordinatesText).text.toString())
            assertNull(shadowOf(application).nextStartedService)
        } finally {
            release.countDown()
            settleDataWork()
            controller.pause().stop().destroy()
        }
    }

    private fun View.clickAndSettle(): Boolean {
        val clicked = performClick()
        settleDataWork()
        return clicked
    }

    private fun capture(activity: MainActivity, name: String) {
        val decor = activity.window.decorView
        val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
        decor.draw(Canvas(bitmap))
        val directory = File("build/outputs/previews").apply { mkdirs() }
        File(directory, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun tap(activity: MainActivity, point: Coordinates) {
        val map = activity.findViewById<WebView>(R.id.map)
        val bridge = shadowOf(map).getJavascriptInterface(MapWorkspace.BRIDGE_NAME) as MapWorkspace.Bridge
        bridge.onSelect(point.latitude, point.longitude)
        settleDataWork()
    }

    private fun views(view: View): List<View> = listOf(view) + if (view is ViewGroup)
        (0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList()
}
