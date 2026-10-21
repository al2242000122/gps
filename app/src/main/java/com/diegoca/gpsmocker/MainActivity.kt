package com.diegoca.gpsmocker

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.MotionEvent
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var latitudeInput: EditText
    private lateinit var longitudeInput: EditText
    private lateinit var statusText: TextView
    private lateinit var stopButton: Button
    private lateinit var map: MapView
    private lateinit var marker: Marker
    private lateinit var store: SimulationStore
    private var selected = Coordinates.DEFAULT
    private var pendingStart: Coordinates? = null
    private var receiverRegistered = false

    private val statusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { refreshStatus() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SimulationStore(this)
        Configuration.getInstance().apply {
            userAgentValue = "$packageName/${packageManager.getPackageInfo(packageName, 0).versionName}"
            osmdroidBasePath = File(filesDir, "maps")
            osmdroidTileCache = File(cacheDir, "map-tiles")
        }
        setContentView(R.layout.activity_main)
        applyWindowInsets()
        latitudeInput = findViewById(R.id.latitudeInput)
        longitudeInput = findViewById(R.id.longitudeInput)
        statusText = findViewById(R.id.statusText)
        stopButton = findViewById(R.id.stopButton)
        map = findViewById(R.id.map)
        selected = savedInstanceState?.let {
            Coordinates.from(it.getDouble("latitude", Double.NaN), it.getDouble("longitude", Double.NaN))
        } ?: store.selection()
        pendingStart = savedInstanceState?.let {
            Coordinates.from(it.getDouble("pendingLatitude", Double.NaN), it.getDouble("pendingLongitude", Double.NaN))
        }
        configureMap(savedInstanceState)
        updateSelection(selected, center = false)

        findViewById<Button>(R.id.centerButton).setOnClickListener {
            readCoordinates()?.let { updateSelection(it, center = true) }
        }
        findViewById<Button>(R.id.startButton).setOnClickListener {
            readCoordinates()?.let { target ->
                updateSelection(target, center = true)
                requestSimulation(target)
            }
        }
        stopButton.setOnClickListener {
            try {
                startService(Intent(this, MockLocationService::class.java).setAction(MockLocationService.ACTION_STOP))
            } catch (_: Exception) {
                showStatus(getString(R.string.cleanup_failed), true)
            }
        }
        findViewById<Button>(R.id.settingsButton).setOnClickListener { openDeveloperOptions() }
        refreshStatus()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun configureMap(savedState: Bundle?) {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.minZoomLevel = 2.0
        map.maxZoomLevel = 19.0
        map.controller.setZoom(savedState?.getDouble("zoom", 14.0) ?: 14.0)
        val center = savedState?.let {
            Coordinates.from(it.getDouble("centerLatitude", Double.NaN), it.getDouble("centerLongitude", Double.NaN))
        } ?: selected
        map.controller.setCenter(GeoPoint(center.latitude, center.longitude))
        map.setOnTouchListener { view, event ->
            view.parent.requestDisallowInterceptTouchEvent(
                event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL
            )
            false
        }
        map.overlays.add(MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(point: GeoPoint): Boolean {
                selectPoint(point)
                return true
            }
            override fun longPressHelper(point: GeoPoint): Boolean {
                selectPoint(point)
                return true
            }
        }))
        marker = Marker(map).apply {
            title = getString(R.string.marker_title)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            isDraggable = true
            setOnMarkerDragListener(object : Marker.OnMarkerDragListener {
                override fun onMarkerDragStart(marker: Marker) { marker.closeInfoWindow() }
                override fun onMarkerDrag(marker: Marker) = Unit
                override fun onMarkerDragEnd(marker: Marker) { selectPoint(marker.position) }
            })
        }
        map.overlays.add(marker)
    }

    private fun selectPoint(point: GeoPoint) {
        val longitude = ((point.longitude + 180.0) % 360.0 + 360.0) % 360.0 - 180.0
        Coordinates.from(point.latitude, longitude)?.let { updateSelection(it, center = false) }
    }

    private fun updateSelection(coordinates: Coordinates, center: Boolean) {
        selected = coordinates
        latitudeInput.setText(String.format(Locale.US, "%.6f", coordinates.latitude))
        longitudeInput.setText(String.format(Locale.US, "%.6f", coordinates.longitude))
        marker.position = GeoPoint(coordinates.latitude, coordinates.longitude)
        marker.closeInfoWindow()
        if (center) map.controller.animateTo(marker.position)
        map.invalidate()
        store.saveSelection(coordinates)
    }

    private fun readCoordinates(): Coordinates? {
        val result = Coordinates.parse(latitudeInput.text.toString(), longitudeInput.text.toString())
        if (result == null) showStatus(getString(R.string.invalid_coordinates), true)
        return result
    }

    private fun requestSimulation(target: Coordinates) {
        if (!MockPermissions.isMockApp(this)) {
            showStatus(getString(R.string.select_mock_app), true)
            openDeveloperOptions()
            return
        }
        if (!MockPermissions.hasLocation(this)) {
            pendingStart = target
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                LOCATION_REQUEST
            )
            return
        }
        if (!MockPermissions.locationEnabled(this)) {
            showStatus(getString(R.string.enable_location), true)
            runCatching { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            return
        }
        try {
            startForegroundService(
                Intent(this, MockLocationService::class.java)
                    .setAction(MockLocationService.ACTION_START)
                    .putExtra(MockLocationService.EXTRA_LATITUDE, target.latitude)
                    .putExtra(MockLocationService.EXTRA_LONGITUDE, target.longitude)
            )
            if (Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NOTIFICATION_REQUEST)
            }
        } catch (_: Exception) {
            showStatus(getString(R.string.start_failed), true)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_REQUEST) {
            val target = pendingStart
            pendingStart = null
            if (MockPermissions.hasLocation(this) && target != null) requestSimulation(target)
            else showStatus(getString(R.string.location_permission_needed), true)
        } else if (requestCode == NOTIFICATION_REQUEST &&
            grantResults.firstOrNull() != PackageManager.PERMISSION_GRANTED
        ) {
            Toast.makeText(this, R.string.notification_permission_optional, Toast.LENGTH_LONG).show()
        }
    }

    private fun refreshStatus() {
        val interrupted = store.cleanupNeeded && !MockLocationService.isRunning && !store.hasError
        val message = if (interrupted) getString(R.string.simulation_interrupted)
        else store.message ?: getString(R.string.initial_status)
        statusText.text = message
        statusText.setTextColor(if (interrupted || store.hasError) Color.rgb(185, 28, 28) else Color.rgb(22, 101, 52))
        stopButton.isEnabled = MockLocationService.isRunning || store.cleanupNeeded
    }

    private fun showStatus(message: String, error: Boolean) {
        statusText.text = message
        statusText.setTextColor(if (error) Color.rgb(185, 28, 28) else Color.rgb(22, 101, 52))
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun openDeveloperOptions() {
        runCatching { startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)) }
            .recoverCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            .onFailure { showStatus(getString(R.string.settings_unavailable), true) }
    }

    @Suppress("DEPRECATION")
    private fun applyWindowInsets() {
        findViewById<android.view.View>(R.id.contentRoot).setOnApplyWindowInsetsListener { view, insets ->
            if (Build.VERSION.SDK_INT >= 30) {
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            } else {
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            }
            insets
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(MockLocationService.ACTION_STATUS)
        ContextCompat.registerReceiver(this, statusReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        receiverRegistered = true
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        refreshStatus()
    }

    override fun onPause() {
        Coordinates.parse(latitudeInput.text.toString(), longitudeInput.text.toString())?.let(store::saveSelection)
        map.onPause()
        super.onPause()
    }

    override fun onStop() {
        if (receiverRegistered) unregisterReceiver(statusReceiver)
        receiverRegistered = false
        super.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        val draft = Coordinates.parse(latitudeInput.text.toString(), longitudeInput.text.toString()) ?: selected
        outState.putDouble("latitude", draft.latitude)
        outState.putDouble("longitude", draft.longitude)
        outState.putDouble("zoom", map.zoomLevelDouble)
        outState.putDouble("centerLatitude", map.mapCenter.latitude)
        outState.putDouble("centerLongitude", map.mapCenter.longitude)
        pendingStart?.let {
            outState.putDouble("pendingLatitude", it.latitude)
            outState.putDouble("pendingLongitude", it.longitude)
        }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        map.onDetach()
        super.onDestroy()
    }

    companion object {
        private const val LOCATION_REQUEST = 100
        private const val NOTIFICATION_REQUEST = 101
    }
}
