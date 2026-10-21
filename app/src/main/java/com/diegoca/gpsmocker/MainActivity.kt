package com.diegoca.gpsmocker

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration as AndroidConfiguration
import android.os.*
import android.provider.Settings
import android.text.Html
import android.text.method.LinkMovementMethod
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import android.webkit.WebView
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private lateinit var store: SimulationStore
    private lateinit var preferences: AppPreferences
    private lateinit var library: PlaceLibrary
    private lateinit var sheets: AppSheets
    private lateinit var lookup: PlaceLookup
    private lateinit var workspace: MapWorkspace
    private lateinit var deviceLocation: DeviceLocation
    private lateinit var root: View
    private lateinit var map: WebView
    private var selected = Coordinates.DEFAULT
    private var selectedName = ""
    private var selectedAddress = ""
    private var routeDraft = RouteDraft()
    private var editingRoute = false
    private var routeMode = false
        set(value) { field = value; if (::preferences.isInitialized) preferences.routeWorkspace = value }
    private var pendingRoute: Boolean? = null
    private var pendingPoint: Coordinates? = null
    private var exportPoints: List<Coordinates> = emptyList()
    private var bars = Insets.NONE
    private var receiverRegistered = false
    private var locateOnResume = true
    private var pendingCurrentLocation = false
    private val io = Executors.newSingleThreadExecutor()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_ROUTE_IMPORTED) {
                routeDraft = preferences.route()
                routeMode = true; editingRoute = false
                routeDraft.points.firstOrNull()?.let { select(it, center = true) }
                message(resources.getQuantityString(R.plurals.gpx_imported, routeDraft.points.size, routeDraft.points.size))
            }
            refreshStatus()
        }
    }

    private val importGpx = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val context = applicationContext
            val prior = preferences.route()
            io.execute {
                val result = runCatching {
                    val points = context.contentResolver.openInputStream(uri)?.use(GpxCodec::read)
                        ?: throw IllegalArgumentException("No se pudo abrir el archivo.")
                    RouteNavigator(points, prior.speedKmh, prior.loop)
                    AppPreferences(context).saveRoute(prior.copy(points = points))
                    AppPreferences(context).routeWorkspace = true
                    points.size
                }
                if (result.isSuccess) context.sendBroadcast(Intent(ACTION_ROUTE_IMPORTED).setPackage(context.packageName))
                else runOnUiThread { if (!isDestroyed) message(getString(R.string.gpx_failed, result.exceptionOrNull()?.message.orEmpty())) }
            }
        }
    }

    private val exportGpx = registerForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        if (uri != null && exportPoints.size >= 2) {
            val points = exportPoints.toList()
            val resolver = applicationContext.contentResolver
            io.execute {
                val result = runCatching {
                    resolver.openOutputStream(uri, "wt")?.use { GpxCodec.write(it, points) }
                        ?: throw IllegalArgumentException("No se pudo guardar el archivo.")
                }
                runOnUiThread { if (!isDestroyed) message(if (result.isSuccess) getString(R.string.gpx_exported)
                    else getString(R.string.gpx_failed, result.exceptionOrNull()?.message.orEmpty())) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        preferences = AppPreferences(this)
        super.onCreate(savedInstanceState)
        if (preferences.dynamicColor) DynamicColors.applyToActivityIfAvailable(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        store = SimulationStore(this)
        library = PlaceLibrary(this)
        sheets = AppSheets(this)
        lookup = PlaceLookup(this)
        deviceLocation = DeviceLocation(this)
        setContentView(R.layout.activity_main)
        root = findViewById(R.id.contentRoot)
        map = findViewById(R.id.map)
        selected = store.selection()
        selectedName = store.selectionName
        routeDraft = preferences.route()
        editingRoute = savedInstanceState?.getBoolean("editingRoute") ?: false
        routeMode = savedInstanceState?.getBoolean("routeMode") ?: preferences.routeWorkspace
        pendingRoute = savedInstanceState?.let { if (it.containsKey("pendingRoute")) it.getBoolean("pendingRoute") else null }
        pendingPoint = savedInstanceState?.let { Coordinates.from(it.getDouble("pendingLatitude", Double.NaN), it.getDouble("pendingLongitude", Double.NaN)) }
        exportPoints = savedInstanceState?.getDoubleArray("exportPoints")?.toList()?.chunked(2)?.mapNotNull {
            if (it.size == 2) Coordinates.from(it[0], it[1]) else null
        }.orEmpty()
        locateOnResume = savedInstanceState?.getBoolean("locateOnResume", false) ?: true
        workspace = MapWorkspace(this, map, ::mapSelected, onMoveRoutePoint = { index, point ->
            val points = routeDraft.points.toMutableList()
            if (index in points.indices) {
                points[index] = point
                saveDraft(routeDraft.copy(points = points))
                select(point, center = false)
                refreshStatus()
            }
        }, onError = { message(it) })
        applyMapStyle()
        workspace.restore(savedInstanceState, selected)
        select(selected, center = false, name = selectedName, address = store.selectionAddress, reverse = false)
        pendingCurrentLocation = savedInstanceState?.getBoolean("pendingCurrentLocation", false) ?: false
        bindActions()
        findViewById<TextView>(R.id.attributionText).apply {
            text = Html.fromHtml("© <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> · <a href=\"https://openmaptiles.org\">OpenMapTiles</a> · <a href=\"https://openfreemap.org\">OpenFreeMap</a>", Html.FROM_HTML_MODE_LEGACY)
            movementMethod = LinkMovementMethod.getInstance()
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            adaptLayout()
            insets
        }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> adaptLayout() }
        findViewById<View>(R.id.locationCard).addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> adaptLayout() }
        ViewCompat.requestApplyInsets(root)
        refreshStatus()
    }

    private fun bindActions() {
        val search = View.OnClickListener { sheets.search(lookup) {
            routeMode = false; editingRoute = false
            workspace.zoom(16.0)
            select(it.coordinates, true, it.title, it.address, reverse = false)
            refreshStatus()
        } }
        findViewById<View>(R.id.searchButton).setOnClickListener(search)
        findViewById<View>(R.id.searchLabel).setOnClickListener(search)
        findViewById<View>(R.id.libraryButton).setOnClickListener {
            sheets.library(library) {
                routeMode = false; editingRoute = false
                select(it.coordinates, true, it.name)
                refreshStatus()
            }
        }
        findViewById<View>(R.id.favoriteButton).setOnClickListener {
            sheets.favorite(library, selected, selectedName) { refreshFavorite(); message(getString(R.string.favorite_saved)) }
        }
        findViewById<View>(R.id.copyButton).setOnClickListener {
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(getString(R.string.selected_point), coordinatesText(selected)))
            message(getString(R.string.coordinates_copied))
        }
        findViewById<View>(R.id.shareButton).setOnClickListener { share() }
        findViewById<View>(R.id.routesButton).setOnClickListener { showRoutes() }
        findViewById<View>(R.id.settingsButton).setOnClickListener {
            sheets.settings(preferences, onSave = { options, dynamic ->
                val changed = preferences.dynamicColor != dynamic
                preferences.saveOptions(options)
                preferences.dynamicColor = dynamic
                if (MockLocationService.isRunning) sendService(MockLocationService.ACTION_OPTIONS)
                message(getString(R.string.options_saved))
                if (changed) root.post { recreate() }
            }, onDeveloper = { developerGuide() })
        }
        findViewById<View>(R.id.layersButton).setOnClickListener {
            sheets.mapAppearance(preferences.mapStyle) { preferences.mapStyle = it; applyMapStyle() }
        }
        findViewById<View>(R.id.centerButton).setOnClickListener {
            val active = MockLocationService.snapshot
            if (MockLocationService.isRunning && active != null) workspace.center(active.coordinates)
            else locateDevice()
        }
        findViewById<View>(R.id.startButton).setOnClickListener { requestSimulation(routeMode) }
        findViewById<View>(R.id.stopButton).setOnClickListener { sendService(MockLocationService.ACTION_STOP) }
        findViewById<View>(R.id.pauseButton).setOnClickListener { sendService(MockLocationService.ACTION_TOGGLE_PAUSE) }
        findViewById<View>(R.id.undoButton).setOnClickListener {
            if (routeDraft.points.isNotEmpty()) {
                saveDraft(routeDraft.copy(points = routeDraft.points.dropLast(1)))
                routeDraft.points.lastOrNull()?.let { select(it, false) }
                refreshStatus()
            }
        }
        findViewById<View>(R.id.finishRouteButton).setOnClickListener { editingRoute = false; refreshStatus(); showRoutes() }
    }

    private fun mapSelected(point: Coordinates) {
        if (editingRoute) {
            if (routeDraft.points.size >= RouteNavigator.MAX_POINTS) { message(getString(R.string.route_max_points)); return }
            if (routeDraft.points.lastOrNull() != point) saveDraft(routeDraft.copy(points = routeDraft.points + point))
        } else routeMode = false
        select(point, center = false, reverse = !editingRoute)
        refreshStatus()
    }

    private fun select(point: Coordinates, center: Boolean, name: String = getString(R.string.selected_point), address: String = "", reverse: Boolean = true) {
        cancelDeviceLocation()
        selected = point
        selectedName = name
        selectedAddress = address
        store.saveSelection(point)
        store.selectionName = name
        store.selectionAddress = address
        workspace.select(point, center)
        displaySelection()
        if (reverse) lookup.reverse(point) { place ->
            if (place != null && selected == point) {
                if (selectedName == getString(R.string.selected_point)) selectedName = place.title
                selectedAddress = place.address
                store.selectionName = selectedName
                store.selectionAddress = selectedAddress
                displaySelection()
            }
        }
    }

    private fun displaySelection() {
        findViewById<TextView>(R.id.placeTitle).text = selectedName
        findViewById<TextView>(R.id.addressText).text = selectedAddress.ifBlank { getString(R.string.only_map_selection) }
        findViewById<TextView>(R.id.coordinatesText).text = coordinatesText(selected)
        refreshFavorite()
    }

    private fun refreshFavorite() {
        val saved = library.favoriteAt(selected) != null
        findViewById<MaterialButton>(R.id.favoriteButton).setIconResource(if (saved) R.drawable.ic_star_filled else R.drawable.ic_star)
    }

    private fun showRoutes() {
        sheets.routes(routeDraft,
            onEdit = {
                val points = it.points.ifEmpty { listOf(selected) }
                saveDraft(it.copy(points = points)); routeMode = true; editingRoute = true
                refreshStatus(); message(getString(R.string.route_edit_hint))
            },
            onStart = { saveDraft(it); routeMode = true; editingRoute = false; refreshStatus(); requestSimulation(true) },
            onImport = { saveDraft(it); importGpx.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream", "*/*")) },
            onExport = { saveDraft(it); exportPoints = it.points.toList(); exportGpx.launch("gps-mocker-ruta.gpx") },
            onClear = { saveDraft(routeDraft.copy(points = emptyList())); editingRoute = false; routeMode = false; refreshStatus() })
    }

    private fun saveDraft(draft: RouteDraft) { routeDraft = draft; preferences.saveRoute(draft) }

    private fun requestSimulation(route: Boolean, target: Coordinates = selected) {
        cancelDeviceLocation()
        if (route) {
            try { RouteNavigator(routeDraft.points, routeDraft.speedKmh, routeDraft.loop) }
            catch (error: IllegalArgumentException) { message(error.message ?: getString(R.string.route_need_points)); return }
        }
        if (!MockPermissions.isMockApp(this)) { developerGuide(); return }
        if (!MockPermissions.hasLocation(this)) {
            pendingRoute = route; pendingPoint = target
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), LOCATION_REQUEST)
            return
        }
        if (!MockPermissions.locationEnabled(this)) {
            message(getString(R.string.enable_location))
            runCatching { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            return
        }
        try {
            val intent = Intent(this, MockLocationService::class.java)
                .setAction(if (route) MockLocationService.ACTION_START_ROUTE else MockLocationService.ACTION_START)
                .putExtra(MockLocationService.EXTRA_LATITUDE, target.latitude)
                .putExtra(MockLocationService.EXTRA_LONGITUDE, target.longitude)
            if (route) { preferences.saveRoute(routeDraft); editingRoute = false }
            startForegroundService(intent)
            if (Build.VERSION.SDK_INT >= 33 && !preferences.notificationsPrompted &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                preferences.notificationsPrompted = true
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
            }
        } catch (_: Exception) { message(getString(R.string.start_failed)) }
    }

    private fun sendService(action: String) {
        try { startService(Intent(this, MockLocationService::class.java).setAction(action)) }
        catch (_: Exception) { message(getString(if (action == MockLocationService.ACTION_STOP) R.string.cleanup_failed else R.string.start_failed)) }
    }

    private fun developerGuide() { sheets.developer {
        runCatching { startActivity(DeveloperSettings.mockLocationIntent()) }
            .recoverCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            .onFailure { message(getString(R.string.settings_unavailable)) }
    } }

    private fun refreshStatus() {
        val state = MockLocationService.snapshot.takeIf { MockLocationService.isRunning }
        val interrupted = store.cleanupNeeded && state == null && !store.hasError
        val status = findViewById<TextView>(R.id.statusText)
        val statusMessage = when {
            interrupted -> getString(R.string.simulation_interrupted)
            store.hasError && state == null -> store.message
            state?.completed == true -> getString(R.string.route_completed)
            state?.paused == true -> getString(R.string.route_paused)
            state?.routePoints?.isNotEmpty() == true -> getString(R.string.route_running)
            state != null -> getString(R.string.simulation_badge)
            editingRoute -> resources.getQuantityString(R.plurals.route_editor_badge, routeDraft.points.size, routeDraft.points.size)
            deviceLocation.searching -> getString(R.string.current_location_searching)
            else -> getString(R.string.select_point_hint)
        }
        if (status.text.toString() != statusMessage) status.text = statusMessage
        status.setTextColor(if (interrupted || store.hasError && state == null) MaterialColors.getColor(this, com.google.android.material.R.attr.colorError, "status")
            else MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant, "status"))
        findViewById<View>(R.id.stopButton).visibility = if (state != null || store.cleanupNeeded) View.VISIBLE else View.GONE
        val pause = findViewById<MaterialButton>(R.id.pauseButton)
        pause.visibility = if (state?.routePoints?.isNotEmpty() == true && !state.completed) View.VISIBLE else View.GONE
        pause.setIconResource(if (state?.paused == true) R.drawable.ic_play else R.drawable.ic_pause)
        pause.contentDescription = getString(if (state?.paused == true) R.string.resume else R.string.pause)
        findViewById<MaterialButton>(R.id.startButton).apply {
            setText(if (routeMode) R.string.route_start else if (state != null) R.string.update_location else R.string.begin)
            icon = if (pause.visibility == View.VISIBLE) null else AppCompatResources.getDrawable(this@MainActivity, R.drawable.ic_play)
        }
        findViewById<View>(R.id.routeBanner).visibility = if (editingRoute) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.routeEditHint).text = resources.getQuantityString(R.plurals.route_editor_badge, routeDraft.points.size, routeDraft.points.size)
        findViewById<View>(R.id.undoButton).isEnabled = routeDraft.points.isNotEmpty()
        val progress = state?.routePoints?.isNotEmpty() == true
        findViewById<View>(R.id.routeProgressRow).visibility = if (progress) View.VISIBLE else View.GONE
        if (progress) {
            findViewById<LinearProgressIndicator>(R.id.routeProgress).setProgressCompat((state.fraction * 1000).toInt(), false)
            findViewById<TextView>(R.id.routeProgressText).text = getString(R.string.route_progress,
                (state.fraction * 100).toInt(), String.format(Locale.getDefault(), "%.1f", state.speedKmh))
        }
        workspace.active(state?.coordinates)
        workspace.route(if (routeMode || editingRoute) routeDraft.points else state?.routePoints.orEmpty(), editingRoute)
    }

    private fun share() {
        val text = getString(R.string.share_location_text, selectedName, coordinatesText(selected),
            selected.latitude.toString(), selected.longitude.toString())
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        runCatching { startActivity(Intent.createChooser(intent, getString(R.string.share_location))) }
            .onFailure { message(getString(R.string.settings_unavailable)) }
    }

    private fun coordinatesText(point: Coordinates) = String.format(Locale.US, "%.6f, %.6f", point.latitude, point.longitude)
    private fun message(text: String) { Snackbar.make(root, text, Snackbar.LENGTH_LONG).setAnchorView(R.id.locationCard).show() }

    private fun cancelDeviceLocation() {
        deviceLocation.cancel()
        pendingCurrentLocation = false
        findViewById<View>(R.id.centerButton)?.apply {
            isEnabled = true
            contentDescription = getString(R.string.center_selected)
        }
    }

    private fun locateDevice() {
        if (MockLocationService.isRunning || store.cleanupNeeded) {
            message(getString(R.string.current_location_stop_mock))
            return
        }
        if (!MockPermissions.hasLocation(this)) {
            pendingCurrentLocation = true
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), CURRENT_LOCATION_REQUEST)
            return
        }
        findViewById<View>(R.id.centerButton).apply {
            isEnabled = false
            contentDescription = getString(R.string.current_location_searching)
        }
        deviceLocation.locate { point, failure ->
            cancelDeviceLocation()
            if (MockLocationService.isRunning || store.cleanupNeeded) return@locate
            if (point != null) {
                workspace.zoom(16.0)
                if (routeMode || editingRoute) workspace.center(point)
                else select(point, center = true, name = getString(R.string.current_location))
            } else message(getString(when (failure) {
                DeviceLocation.Failure.PERMISSION -> R.string.current_location_permission
                DeviceLocation.Failure.DISABLED -> R.string.current_location_disabled
                else -> R.string.current_location_unavailable
            }))
            refreshStatus()
        }
        refreshStatus()
    }

    @Suppress("DEPRECATION")
    private fun applyMapStyle() {
        if (::workspace.isInitialized) workspace.style(preferences.mapStyle)
        val dark = preferences.mapStyle == "dark" || preferences.mapStyle == "auto" &&
            resources.configuration.uiMode and AndroidConfiguration.UI_MODE_NIGHT_MASK == AndroidConfiguration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = resources.configuration.uiMode and AndroidConfiguration.UI_MODE_NIGHT_MASK != AndroidConfiguration.UI_MODE_NIGHT_YES
        }
        if (Build.VERSION.SDK_INT < 27) {
            window.navigationBarColor = android.graphics.Color.BLACK
        }
    }

    private fun adaptLayout() {
        if (root.width == 0 || root.height == 0) return
        val density = resources.displayMetrics.density
        fun dp(value: Int) = (value * density).toInt()
        val wide = root.width > root.height && root.width / density >= 600
        fun margins(id: Int, width: Int, gravity: Int, left: Int, top: Int, right: Int, bottom: Int, height: Int? = null) {
            val view = findViewById<View>(id)
            val params = view.layoutParams as FrameLayout.LayoutParams
            val h = height ?: params.height
            if (params.width != width || params.height != h || params.gravity != gravity || params.leftMargin != left ||
                params.topMargin != top || params.rightMargin != right || params.bottomMargin != bottom) {
                params.width = width; params.height = h; params.gravity = gravity
                params.setMargins(left, top, right, bottom)
                view.layoutParams = params
            }
        }
        val card = findViewById<MaterialCardView>(R.id.locationCard)
        val maximum = if (wide) root.height - bars.top - bars.bottom - dp(24)
            else ((root.height - bars.top - bars.bottom) * 0.58).toInt()
        findViewById<BoundedScrollView>(R.id.locationScroll).maximumHeight = maximum.coerceAtLeast(dp(200))
        margins(R.id.locationCard, if (wide) dp(360) else -1, Gravity.BOTTOM or if (wide) Gravity.END else Gravity.CENTER_HORIZONTAL,
            bars.left + dp(12), if (wide) bars.top + dp(12) else 0, bars.right + dp(12), bars.bottom + dp(12), -2)
        val searchWidth = if (wide) (root.width - dp(404) - bars.left - bars.right).coerceAtLeast(dp(180)) else -1
        margins(R.id.searchCard, searchWidth, Gravity.TOP or Gravity.START,
            bars.left + dp(16), bars.top + dp(12), bars.right + dp(16), 0)
        margins(R.id.routeBanner, searchWidth, Gravity.TOP or Gravity.START,
            bars.left + dp(16), bars.top + dp(84), bars.right + dp(16), 0)
        val cardHeight = card.height
        margins(R.id.mapControls, dp(56), Gravity.BOTTOM or if (wide) Gravity.START else Gravity.END,
            bars.left + dp(16), 0, bars.right + dp(16), bars.bottom + dp(24) + if (wide) 0 else cardHeight)
        margins(R.id.attributionText, -2, Gravity.BOTTOM or Gravity.START, bars.left + dp(16), 0,
            bars.right + dp(90), bars.bottom + if (wide) dp(4) else cardHeight + dp(16))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQUEST) {
            val route = pendingRoute; val point = pendingPoint
            pendingRoute = null; pendingPoint = null
            if (MockPermissions.hasLocation(this) && route != null && point != null) requestSimulation(route, point)
            else message(getString(R.string.location_permission_needed))
        } else if (requestCode == CURRENT_LOCATION_REQUEST) {
            val pending = pendingCurrentLocation
            pendingCurrentLocation = false
            if (pending) {
                if (MockPermissions.hasLocation(this)) locateDevice()
                else message(getString(R.string.current_location_permission))
            }
        } else if (requestCode == NOTIFICATION_REQUEST && grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED) {
            message(getString(R.string.notification_permission_optional))
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(this, receiver, IntentFilter().apply {
            addAction(MockLocationService.ACTION_STATUS); addAction(ACTION_ROUTE_IMPORTED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        val latest = preferences.route()
        if (latest.points != routeDraft.points && preferences.routeWorkspace) {
            editingRoute = false
            latest.points.firstOrNull()?.let { select(it, true) }
        }
        routeDraft = latest
        routeMode = preferences.routeWorkspace
        refreshStatus()
        if (locateOnResume) {
            locateOnResume = false
            if (!MockLocationService.isRunning && !store.cleanupNeeded) locateDevice()
        }
    }

    override fun onPause() { map.onPause(); super.onPause() }
    override fun onStop() {
        if (deviceLocation.searching) locateOnResume = true
        cancelDeviceLocation()
        if (receiverRegistered) unregisterReceiver(receiver)
        receiverRegistered = false
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        workspace.save(outState)
        outState.putBoolean("editingRoute", editingRoute)
        outState.putBoolean("routeMode", routeMode)
        outState.putBoolean("locateOnResume", locateOnResume || deviceLocation.searching)
        outState.putBoolean("pendingCurrentLocation", pendingCurrentLocation)
        pendingRoute?.let { outState.putBoolean("pendingRoute", it) }
        pendingPoint?.let { outState.putDouble("pendingLatitude", it.latitude); outState.putDouble("pendingLongitude", it.longitude) }
        if (exportPoints.isNotEmpty()) outState.putDoubleArray("exportPoints", exportPoints.flatMap { listOf(it.latitude, it.longitude) }.toDoubleArray())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        deviceLocation.cancel()
        sheets.close(); lookup.close(); io.shutdown(); workspace.close()
        super.onDestroy()
    }

    companion object {
        const val ACTION_ROUTE_IMPORTED = "com.diegoca.gpsmocker.ROUTE_IMPORTED"
        private const val LOCATION_REQUEST = 100
        private const val NOTIFICATION_REQUEST = 101
        private const val CURRENT_LOCATION_REQUEST = 102
    }
}
