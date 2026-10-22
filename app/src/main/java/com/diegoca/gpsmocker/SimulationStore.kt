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

    private val data by lazy { UserDataRepository.get(context.applicationContext) }
    fun selection(): Coordinates = data.snapshot().selection
    fun saveSelection(coordinates: Coordinates) { data.update { it.copy(selection = coordinates) } }
    fun saveSelection(coordinates: Coordinates, name: String, address: String) {
        data.update { it.copy(selection = coordinates, selectionName = name.take(200), selectionAddress = address.take(500)) }
    }
    var selectionName: String
        get() = data.snapshot().selectionName
        set(value) { data.update { it.copy(selectionName = value.take(200)) } }
    var selectionAddress: String
        get() = data.snapshot().selectionAddress
        set(value) { data.update { it.copy(selectionAddress = value.take(500)) } }
}
