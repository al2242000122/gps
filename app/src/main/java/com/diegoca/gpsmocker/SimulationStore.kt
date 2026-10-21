package com.diegoca.gpsmocker

import android.content.Context
import android.annotation.SuppressLint

class SimulationStore(context: Context) {
    private val preferences = context.getSharedPreferences("simulation", Context.MODE_PRIVATE)

    var cleanupNeeded: Boolean
        get() = preferences.getBoolean("cleanup_needed", false)
        // Persist ownership before installing GPS so a killed process can recover it.
        @SuppressLint("ApplySharedPref")
        set(value) { preferences.edit().putBoolean("cleanup_needed", value).commit() }

    val message: String? get() = preferences.getString("message", null)
    val hasError: Boolean get() = preferences.getBoolean("error", false)

    fun writeStatus(message: String, error: Boolean = false) {
        preferences.edit().putString("message", message).putBoolean("error", error).apply()
    }

    fun selection(): Coordinates = Coordinates.parse(
        preferences.getString("latitude", null).orEmpty(),
        preferences.getString("longitude", null).orEmpty()
    ) ?: Coordinates.DEFAULT

    fun saveSelection(coordinates: Coordinates) {
        preferences.edit()
            .putString("latitude", coordinates.latitude.toString())
            .putString("longitude", coordinates.longitude.toString())
            .apply()
    }

    var selectionName: String
        get() = preferences.getString("selection_name", "Punto seleccionado") ?: "Punto seleccionado"
        set(value) { preferences.edit().putString("selection_name", value.take(200)).apply() }

    var selectionAddress: String
        get() = preferences.getString("selection_address", "").orEmpty()
        set(value) { preferences.edit().putString("selection_address", value.take(500)).apply() }
}
