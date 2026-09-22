package com.diegoca.gpsmocker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private lateinit var locationManager: LocationManager
    private lateinit var latitudeInput: EditText
    private lateinit var longitudeInput: EditText
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        latitudeInput = findViewById(R.id.latitudeInput)
        longitudeInput = findViewById(R.id.longitudeInput)
        statusText = findViewById(R.id.statusText)

        findViewById<Button>(R.id.startButton).apply {
            backgroundTintList = ColorStateList.valueOf(Color.rgb(43, 108, 176))
            setOnClickListener { applyMockLocation() }
        }

        findViewById<Button>(R.id.stopButton).apply {
            backgroundTintList = ColorStateList.valueOf(Color.rgb(190, 55, 55))
            setOnClickListener { stopMockLocation() }
        }

        findViewById<View>(R.id.settingsButton).setOnClickListener {
            openDeveloperOptions()
        }
    }

    private fun applyMockLocation() {
        val latitude = latitudeInput.text.toString().toDoubleOrNull()
        val longitude = longitudeInput.text.toString().toDoubleOrNull()

        if (latitude == null || longitude == null ||
            latitude !in -90.0..90.0 || longitude !in -180.0..180.0
        ) {
            showStatus(getString(R.string.invalid_coordinates), false)
            return
        }

        try {
            runCatching { locationManager.removeTestProvider(LocationManager.GPS_PROVIDER) }

            locationManager.addTestProvider(
                LocationManager.GPS_PROVIDER,
                false,
                true,
                false,
                false,
                true,
                true,
                true,
                Criteria.POWER_LOW,
                Criteria.ACCURACY_FINE
            )
            locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)

            val mockLocation = Location(LocationManager.GPS_PROVIDER).apply {
                this.latitude = latitude
                this.longitude = longitude
                altitude = 0.0
                accuracy = 3f
                speed = 0f
                bearing = 0f
                time = System.currentTimeMillis()
                elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
            }

            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, mockLocation)
            showStatus(getString(R.string.location_active, latitude, longitude), true)
        } catch (_: SecurityException) {
            showStatus(getString(R.string.select_mock_app), false)
        } catch (error: IllegalArgumentException) {
            showStatus(error.message ?: getString(R.string.generic_error), false)
        }
    }

    private fun stopMockLocation() {
        try {
            runCatching {
                locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
            }
            runCatching { locationManager.removeTestProvider(LocationManager.GPS_PROVIDER) }
            showStatus(getString(R.string.location_stopped), true)
        } catch (error: Exception) {
            showStatus(error.message ?: getString(R.string.generic_error), false)
        }
    }

    private fun openDeveloperOptions() {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        runCatching { startActivity(intent) }
            .onFailure { startActivity(Intent(Settings.ACTION_SETTINGS)) }
    }

    private fun showStatus(message: String, success: Boolean) {
        statusText.text = message
        statusText.setTextColor(
            if (success) Color.rgb(22, 101, 52) else Color.rgb(185, 28, 28)
        )
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
