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
import android.view.MotionEvent
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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
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
    private val dataModel by lazy { ViewModelProvider(this)[UserDataViewModel::class.java] }
    private var renderedDataRevision = -1L
    private var renderedManualApplied = 0L
    private var creationState: Bundle? = null
    private val readyActions = ArrayDeque<() -> Unit>()
    private lateinit var store: SimulationStore
    private lateinit var preferences: AppPreferences
    private lateinit var library: PlaceLibrary
    private lateinit var sheets: AppSheets
    private lateinit var lookup: PlaceLookup
    private lateinit var workspace: MapWorkspace
    private lateinit var deviceLocation: DeviceLocation
    private lateinit var ads: AdsController
    private lateinit var root: View
    private lateinit var map: WebView
    private var selected = Coordinates.DEFAULT
    private var selectedName = ""
    private var selectedAddress = ""
    private var coordinateDraft: CoordinateDraft? = null
    private var routeDraft = RouteDraft()
    private var editingRoute = false
    private var routeMode = false
        set(value) { field = value; if (::preferences.isInitialized) preferences.routeWorkspace = value }
    private var pendingRoute: Boolean? = null
    private var pendingPoint: Coordinates? = null
    private var exportPoints: List<Coordinates> = emptyList()
    private var exportSource = ""
    private var bars = Insets.NONE
    private var receiverRegistered = false
    private var locateOnResume = true
    private var pendingCurrentLocation = false
    private val io = Executors.newSingleThreadExecutor()
    private val routing = RoutingClient()
    private var routeRevision = 0
    private val backups = BackupController(this, beforeRestore = {
        cancelDeviceLocation(); lookup.cancelReverse(); lookup.cancelSearch()
        sheets.close(); locateOnResume = false
    })

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BackupController.ACTION_RESTORED) { reloadRestoredData(); return }
            if (intent?.action == ACTION_ROUTE_IMPORTED) {
                routeDraft = preferences.route()
                routeMode = true; editingRoute = false
                routeDraft.points.firstOrNull()?.let { select(it, center = true) }
                message(resources.getQuantityString(R.plurals.gpx_imported, routeDraft.editablePoints.size, routeDraft.editablePoints.size))
            }
            refreshStatus()
        }
    }

    private val importGpx = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) whenWorkspaceReady {
            val context = applicationContext
            val prior = preferences.route()
            io.execute {
                val result = runCatching {
                    val document = context.contentResolver.openInputStream(uri)?.use(GpxCodec::readDocument)
                        ?: throw IllegalArgumentException("No se pudo abrir el archivo.")
                    val points = document.points
                    RouteNavigator(points, prior.speedKmh, prior.loop)
                    check(!BackupGuard.restoring.get()) { "Espera a que termine la restauración." }
                    AppPreferences(context).saveRoute(RouteDraft(points, prior.speedKmh, prior.loop, mode = RouteMode.IMPORTED, source = document.source))
                    AppPreferences(context).routeWorkspace = true
                    points.size
                }
                if (result.isSuccess) context.sendBroadcast(Intent(ACTION_ROUTE_IMPORTED).setPackage(context.packageName))
                else runOnUiThread { if (!isDestroyed) message(getString(R.string.gpx_failed, result.exceptionOrNull()?.message.orEmpty())) }
            }
        }
    }

    private val exportGpx = registerForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        if (uri != null) whenWorkspaceReady {
            if (exportPoints.size < 2) return@whenWorkspaceReady
            val points = exportPoints.toList()
            val source = exportSource
            val resolver = applicationContext.contentResolver
            io.execute {
                val result = runCatching {
                    resolver.openOutputStream(uri, "wt")?.use { GpxCodec.write(it, points, source) }
                        ?: throw IllegalArgumentException("No se pudo guardar el archivo.")
                }
                runOnUiThread { if (!isDestroyed) message(if (result.isSuccess) getString(R.string.gpx_exported)
                    else getString(R.string.gpx_failed, result.exceptionOrNull()?.message.orEmpty())) }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        creationState = savedInstanceState
        renderedManualApplied = savedInstanceState?.getLong("manualApplied", 0L) ?: 0L
        setContentView(TextView(this).apply {
            text = getString(R.string.loading_user_data)
            gravity = Gravity.CENTER
            textSize = 18f
        })
        lifecycleScope.launch {
            dataModel.state.collect { state ->
                if (state.loadError != null && !isFinishing) {
                    startActivity(Intent(this@MainActivity, DataRecoveryActivity::class.java))
                    finish()
                } else if (state.ready && !::workspace.isInitialized) {
                    initializeWorkspace(savedInstanceState)
                } else if (::workspace.isInitialized && state.pending == 0 && state.revision != renderedDataRevision) {
                    renderedDataRevision = state.revision
                    routeDraft = preferences.route()
                    if (selected != store.selection()) select(store.selection(), false, store.selectionName,
                        store.selectionAddress, reverse = false, persist = false)
                    refreshFavorite()
                    refreshStatus()
                }
                if (::workspace.isInitialized && state.manualApplied > renderedManualApplied) {
                    renderedManualApplied = state.manualApplied
                    sheets.closeCoordinates()
                    routeMode = false; editingRoute = false
                    refreshStatus()
                }
            }
        }
    }

    private fun initializeWorkspace(savedInstanceState: Bundle?) {
        try { preferences = AppPreferences(this) }
        catch (_: Exception) {
            startActivity(Intent(this, DataRecoveryActivity::class.java)); finish(); return
        }
        if (preferences.dynamicColor) DynamicColors.applyToActivityIfAvailable(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        store = SimulationStore(this)
        library = PlaceLibrary(this)
        sheets = AppSheets(this, dataModel)
        lookup = PlaceLookup(this)
        deviceLocation = DeviceLocation(this)
        setContentView(R.layout.activity_main)
        root = findViewById(R.id.contentRoot)
        ads = AdsController(this, findViewById(R.id.adContainer)) { adaptLayout() }
        ads.start()
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
        exportSource = savedInstanceState?.getString("exportSource", "").orEmpty()
        locateOnResume = savedInstanceState?.getBoolean("locateOnResume", false) ?: !intent.getBooleanExtra("skipInitialFix", false)
        intent.removeExtra("skipInitialFix")
        workspace = MapWorkspace(this, map, ::mapSelected, onMoveRoutePoint = { index, point ->
            val points = routeDraft.editablePoints.toMutableList()
            if (index in points.indices) {
                points[index] = point
                saveDraft(routeDraft.edited(points))
                select(point, center = false)
                refreshStatus()
            }
        }, onError = { message(it) })
        applyMapStyle()
        workspace.restore(if (intent.getBooleanExtra("restoreCamera", false)) null else savedInstanceState, selected)
        intent.removeExtra("restoreCamera")
        select(selected, center = false, name = selectedName, address = store.selectionAddress, reverse = false, persist = false)
        pendingCurrentLocation = savedInstanceState?.getBoolean("pendingCurrentLocation", false) ?: false
        bindActions()
        if (resources.configuration.fontScale > 1.3f) {
            listOf(R.id.libraryButton, R.id.routesButton, R.id.shareButton).forEach { id ->
                findViewById<MaterialButton>(id).icon = null
            }
        }
        if (savedInstanceState?.containsKey("manualLatitude") == true) {
            openCoordinateEditor(CoordinateDraft(
                savedInstanceState.getString("manualLatitude").orEmpty(),
                savedInstanceState.getString("manualLongitude").orEmpty()
            ))
        }
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
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) registerStatusReceiver()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) resumeWorkspace()
        while (readyActions.isNotEmpty()) readyActions.removeFirst().invoke()
        creationState = null
    }

    private fun whenWorkspaceReady(action: () -> Unit) {
        if (::workspace.isInitialized) action() else readyActions.addLast(action)
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
        findViewById<View>(R.id.editCoordinatesButton).setOnClickListener {
            openCoordinateEditor(CoordinateDraft(
                String.format(Locale.US, "%.6f", selected.latitude),
                String.format(Locale.US, "%.6f", selected.longitude)
            ))
        }
        findViewById<View>(R.id.shareButton).setOnClickListener { share() }
        findViewById<View>(R.id.routesButton).setOnClickListener { showRoutes() }
        findViewById<View>(R.id.settingsButton).setOnClickListener {
            sheets.settings(preferences, onSave = { options, dynamic, complete ->
                val changed = preferences.dynamicColor != dynamic
                dataModel.write(this, { preferences.saveSettingsAwait(options, dynamic) }, {
                    if (MockLocationService.isRunning) sendService(MockLocationService.ACTION_OPTIONS)
                    message(getString(R.string.options_saved))
                    complete(true)
                    if (changed) root.post { recreate() }
                }, { message("No se pudieron guardar los ajustes: ${it.message}"); complete(false) })
            }, onDeveloper = { developerGuide() }, onBackup = { backups.show() },
                privacyOptionsRequired = ads.privacyOptionsRequired, onPrivacyOptions = { ads.privacyOptions() },
                onPrivacyPolicy = { startActivity(Intent(this, PrivacyActivity::class.java)) })
        }
        findViewById<View>(R.id.layersButton).setOnClickListener {
            sheets.mapAppearance(preferences.mapStyle) {
                dataModel.write(this, { preferences.saveMapStyleAwait(it) }, { applyMapStyle() },
                    { message("No se pudo guardar la apariencia: ${it.message}") })
            }
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
            if (routeDraft.editablePoints.isNotEmpty()) {
                saveDraft(routeDraft.edited(routeDraft.editablePoints.dropLast(1)))
                routeDraft.editablePoints.lastOrNull()?.let { select(it, false) }
                refreshStatus()
            }
        }
        findViewById<View>(R.id.finishRouteButton).setOnClickListener { editingRoute = false; refreshStatus(); showRoutes() }
    }

    private fun mapSelected(point: Coordinates) {
        if (editingRoute) {
            if (routeDraft.editablePoints.size >= if (routeDraft.mode == RouteMode.STREET) 20 else RouteNavigator.MAX_POINTS) { message(getString(R.string.route_max_points)); return }
            if (routeDraft.editablePoints.lastOrNull() != point) saveDraft(routeDraft.edited(routeDraft.editablePoints + point))
        } else routeMode = false
        select(point, center = false, reverse = !editingRoute)
        refreshStatus()
    }

    private fun openCoordinateEditor(draft: CoordinateDraft) {
        coordinateDraft = draft
        cancelDeviceLocation()
        lookup.cancelReverse()
        locateOnResume = false
        sheets.coordinates(draft, onDraftChanged = { coordinateDraft = it }, onChoose = { point, complete ->
            // Selecting a point never starts or updates a running simulation.
            // Preserve saved routes, and avoid geocoding so manual entry also works offline.
            select(point, center = true, reverse = false, manualSelection = true, onCommitted = {
                routeMode = false
                editingRoute = false
                refreshStatus()
                complete(true)
            }, onFailed = { complete(false) })
        }, onDismiss = { coordinateDraft = null })
    }

    private fun select(point: Coordinates, center: Boolean, name: String = getString(R.string.selected_point), address: String = "",
                       reverse: Boolean = true, persist: Boolean = true, manualSelection: Boolean = false,
                       onCommitted: () -> Unit = {}, onFailed: () -> Unit = {}) {
        cancelDeviceLocation()
        if (BackupGuard.restoring.get()) { onFailed(); return }
        if (persist) {
            dataModel.write(this, {
                store.saveSelectionAwait(point, name, address)
                if (manualSelection) preferences.routeWorkspace = false
            }, {
                select(point, center, name, address, reverse, persist = false)
                onCommitted()
            }, { message("No se pudo guardar la ubicación: ${it.message}"); onFailed() }, manualSelection = manualSelection)
            return
        }
        selected = point
        selectedName = name
        selectedAddress = address
        workspace.select(point, center)
        displaySelection()
        if (reverse) lookup.reverse(point) { place ->
            if (place != null && selected == point) {
                val title = if (selectedName == getString(R.string.selected_point)) place.title else selectedName
                dataModel.write(this, {
                    store.enrichSelectionAwait(point, title, place.address)
                }, {
                    if (selected != point) return@write
                    selectedName = title; selectedAddress = place.address
                    displaySelection()
                }, { message("No se pudo guardar la dirección: ${it.message}") })
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
                val points = it.editablePoints.ifEmpty { listOf(selected) }
                saveDraft(it.edited(points)); routeMode = true; editingRoute = true
                refreshStatus(); message(getString(R.string.route_edit_hint))
            },
            onStart = { saveDraft(it) { routeMode = true; editingRoute = false; refreshStatus(); requestSimulation(true) } },
            onImport = { saveDraft(it) { importGpx.launch(arrayOf("application/gpx+xml", "application/xml", "text/xml", "application/octet-stream", "*/*")) } },
            onExport = { draft -> saveDraft(draft) { exportPoints = draft.points.toList(); exportSource = draft.source; exportGpx.launch("gps-mocker-ruta.gpx") } },
            onClear = { saveDraft(RouteDraft(speedKmh = routeDraft.speedKmh)); editingRoute = false; routeMode = false; refreshStatus() },
            onCalculate = { draft ->
                if (draft.waypoints.size < 2) message("Elige al menos dos puntos en el mapa.")
                else {
                    routing.cancel(); val expected = ++routeRevision
                    routeMode = true; editingRoute = false; refreshStatus()
                    message("Calculando recorrido por calles…")
                    routing.calculate(draft) { result ->
                        if (expected == routeRevision && !BackupGuard.restoring.get()) result.fold({
                            saveDraft(it) { refreshStatus(); showRoutes() }
                        }, { message(it.message ?: "No se pudo calcular la ruta. Se conserva el recorrido anterior.") })
                    }
                }
            })
    }

    private fun saveDraft(draft: RouteDraft, saved: () -> Unit = {}) {
        routeDraft = draft
        routeRevision++
        routing.cancel()
        dataModel.write(this, { preferences.saveRouteAwait(draft) }, {
            refreshStatus()
            saved()
        }, {
            if (routeDraft == draft) routeDraft = preferences.route()
            refreshStatus()
            message("No se pudo guardar la ruta: ${it.message}")
        })
    }
    private fun reloadRestoredData() {
        cancelDeviceLocation(); lookup.cancelReverse(); routing.cancel(); routeRevision++
        preferences.routeWorkspace = false; routeMode = false; editingRoute = false; locateOnResume = false
        intent.putExtra("skipInitialFix", true)
        intent.putExtra("restoreCamera", true)
        recreate()
    }
    override fun dispatchTouchEvent(event: MotionEvent): Boolean =
        if (BackupGuard.restoring.get()) true else super.dispatchTouchEvent(event)

    private fun requestSimulation(route: Boolean, target: Coordinates = selected) {
        if (dataModel.state.value.pending > 0) { message(getString(R.string.saving_user_data)); return }
        if (BackupGuard.restoring.get()) { message("Espera a que termine la restauración."); return }
        routing.cancel(); routeRevision++
        cancelDeviceLocation()
        if (route) {
            try { require(routeDraft.ready) { "Calcula la ruta por calles antes de comenzar." }; RouteNavigator(routeDraft.points, routeDraft.speedKmh, routeDraft.loop) }
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
            if (route) editingRoute = false
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
            editingRoute -> resources.getQuantityString(R.plurals.route_editor_badge, routeDraft.editablePoints.size, routeDraft.editablePoints.size)
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
        pause.setText(if (state?.paused == true) R.string.resume else R.string.pause)
        findViewById<TextView>(R.id.activePositionText).apply {
            visibility = if (state != null) View.VISIBLE else View.GONE
            if (state != null) text = getString(R.string.simulated_position, coordinatesText(state.coordinates))
        }
        findViewById<MaterialButton>(R.id.startButton).apply {
            setText(if (routeMode) R.string.route_start else if (state != null) R.string.update_location else R.string.begin)
            icon = if (pause.visibility == View.VISIBLE) null else AppCompatResources.getDrawable(this@MainActivity, R.drawable.ic_play)
        }
        findViewById<View>(R.id.routeBanner).visibility = if (editingRoute) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.routeEditHint).text = resources.getQuantityString(R.plurals.route_editor_badge, routeDraft.editablePoints.size, routeDraft.editablePoints.size)
        findViewById<View>(R.id.undoButton).isEnabled = routeDraft.editablePoints.isNotEmpty()
        val progress = state?.routePoints?.isNotEmpty() == true
        findViewById<View>(R.id.routeProgressRow).visibility = if (progress) View.VISIBLE else View.GONE
        if (progress) {
            findViewById<LinearProgressIndicator>(R.id.routeProgress).setProgressCompat((state.fraction * 1000).toInt(), false)
            findViewById<TextView>(R.id.routeProgressText).text = getString(R.string.route_progress,
                (state.fraction * 100).toInt(), String.format(Locale.getDefault(), "%.1f", state.speedKmh))
        }
        val source = if (routeMode || editingRoute) routeDraft.source else preferences.route().source
        findViewById<TextView>(R.id.attributionText).text = Html.fromHtml(
            "© <a href=\"https://www.openstreetmap.org/copyright\">OpenStreetMap</a> · <a href=\"https://openmaptiles.org\">OpenMapTiles</a> · <a href=\"https://openfreemap.org\">OpenFreeMap</a>" +
                if (source == "geoapify") " · <a href=\"https://www.geoapify.com/\">Powered by Geoapify</a>" else "", Html.FROM_HTML_MODE_LEGACY)
        workspace.active(state?.coordinates)
        workspace.route(if (editingRoute) routeDraft.editablePoints else if (routeMode) routeDraft.points else state?.routePoints.orEmpty(), editingRoute)
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
        val adContainer = findViewById<FrameLayout>(R.id.adContainer)
        ads.spaceAvailable(!wide && root.width - bars.left - bars.right >= dp(352) && root.height - bars.top - bars.bottom >= dp(650))
        val adSpace = if (adContainer.visibility == View.VISIBLE) dp(66) else 0
        margins(R.id.adContainer, dp(320), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, bars.left, 0, bars.right, bars.bottom, dp(50))
        val card = findViewById<MaterialCardView>(R.id.locationCard)
        val maximum = if (wide) root.height - bars.top - bars.bottom - dp(24) - adSpace
            else ((root.height - bars.top - bars.bottom - adSpace) * 0.58).toInt()
        findViewById<BoundedScrollView>(R.id.locationScroll).maximumHeight = maximum.coerceAtLeast(dp(200))
        margins(R.id.locationCard, if (wide) dp(360) else -1, Gravity.BOTTOM or if (wide) Gravity.END else Gravity.CENTER_HORIZONTAL,
            bars.left + dp(12), if (wide) bars.top + dp(12) else 0, bars.right + dp(12), bars.bottom + dp(12) + adSpace, -2)
        val searchWidth = if (wide) (root.width - dp(404) - bars.left - bars.right).coerceAtLeast(dp(180)) else -1
        margins(R.id.searchCard, searchWidth, Gravity.TOP or Gravity.START,
            bars.left + dp(16), bars.top + dp(12), bars.right + dp(16), 0)
        margins(R.id.routeBanner, searchWidth, Gravity.TOP or Gravity.START,
            bars.left + dp(16), bars.top + dp(84), bars.right + dp(16), 0)
        val cardHeight = card.height
        margins(R.id.mapControls, dp(56), Gravity.BOTTOM or if (wide) Gravity.START else Gravity.END,
            bars.left + dp(16), 0, bars.right + dp(16), bars.bottom + adSpace + dp(24) + if (wide) 0 else cardHeight)
        margins(R.id.attributionText, -2, Gravity.BOTTOM or Gravity.START, bars.left + dp(16), 0,
            bars.right + dp(90), bars.bottom + adSpace + if (wide) dp(4) else cardHeight + dp(16))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        whenWorkspaceReady { handlePermissionResult(requestCode, grantResults) }
    }

    private fun handlePermissionResult(requestCode: Int, grantResults: IntArray) {
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
        if (!::workspace.isInitialized) return
        registerStatusReceiver()
    }

    private fun registerStatusReceiver() {
        if (receiverRegistered) return
        ContextCompat.registerReceiver(this, receiver, IntentFilter().apply {
            addAction(MockLocationService.ACTION_STATUS); addAction(ACTION_ROUTE_IMPORTED); addAction(BackupController.ACTION_RESTORED)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    override fun onResume() {
        super.onResume()
        if (!::workspace.isInitialized) return
        resumeWorkspace()
    }

    private fun resumeWorkspace() {
        map.onResume()
        val latest = preferences.route()
        if (latest.points != routeDraft.points && preferences.routeWorkspace) {
            editingRoute = false
            latest.points.firstOrNull()?.let { select(it, true) }
        }
        routeDraft = latest
        if (!BackupGuard.restoring.get() && selected != store.selection()) {
            select(store.selection(), true, store.selectionName, store.selectionAddress, reverse = false, persist = false)
        }
        routeMode = preferences.routeWorkspace
        refreshStatus()
        if (locateOnResume) {
            locateOnResume = false
            if (!BackupGuard.restoring.get() && !MockLocationService.isRunning && !store.cleanupNeeded) locateDevice()
        }
    }

    override fun onPause() { if (::map.isInitialized) map.onPause(); super.onPause() }
    override fun onStop() {
        if (!::deviceLocation.isInitialized) { super.onStop(); return }
        if (deviceLocation.searching) locateOnResume = true
        cancelDeviceLocation()
        if (receiverRegistered) unregisterReceiver(receiver)
        receiverRegistered = false
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putLong("manualApplied", renderedManualApplied)
        if (!::workspace.isInitialized) {
            creationState?.let { outState.putAll(it) }
            super.onSaveInstanceState(outState)
            return
        }
        workspace.save(outState)
        outState.putBoolean("editingRoute", editingRoute)
        outState.putBoolean("routeMode", routeMode)
        outState.putBoolean("locateOnResume", locateOnResume || deviceLocation.searching)
        outState.putBoolean("pendingCurrentLocation", pendingCurrentLocation)
        coordinateDraft?.let {
            outState.putString("manualLatitude", it.latitude)
            outState.putString("manualLongitude", it.longitude)
        }
        pendingRoute?.let { outState.putBoolean("pendingRoute", it) }
        pendingPoint?.let { outState.putDouble("pendingLatitude", it.latitude); outState.putDouble("pendingLongitude", it.longitude) }
        outState.putString("exportSource", exportSource)
        if (exportPoints.isNotEmpty()) outState.putDoubleArray("exportPoints", exportPoints.flatMap { listOf(it.latitude, it.longitude) }.toDoubleArray())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        readyActions.clear()
        if (!::workspace.isInitialized) { backups.close(); routing.close(); io.shutdown(); super.onDestroy(); return }
        deviceLocation.cancel()
        ads.close(); routing.close(); backups.close(); sheets.close(); lookup.close(); io.shutdown(); workspace.close()
        super.onDestroy()
    }

    companion object {
        const val ACTION_ROUTE_IMPORTED = "com.diegoca.gpsmocker.ROUTE_IMPORTED"
        private const val LOCATION_REQUEST = 100
        private const val NOTIFICATION_REQUEST = 101
        private const val CURRENT_LOCATION_REQUEST = 102
    }
}
