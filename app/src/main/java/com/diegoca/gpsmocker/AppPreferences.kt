package com.diegoca.gpsmocker

import android.content.Context
import org.json.JSONArray

data class RouteDraft(val points: List<Coordinates> = emptyList(), val speedKmh: Double = 5.0, val loop: Boolean = false)

class AppPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)

    init {
        // Earlier builds defaulted to following the system, producing an unreadable night map.
        // Migrate that default once; subsequent explicit appearance choices remain persistent.
        if (!preferences.getBoolean("light_map_default_v1", false)) {
            val editor = preferences.edit().putBoolean("light_map_default_v1", true)
            if (preferences.getString("map_style", "auto") == "auto") editor.putString("map_style", "light")
            editor.apply()
        }
    }

    fun options(): SimulationOptions = runCatching {
        SimulationOptions(preferences.getString("altitude", "0")!!.toDouble(),
            preferences.getFloat("accuracy", 3f), preferences.getLong("interval", 1000L))
    }.getOrDefault(SimulationOptions())

    fun saveOptions(options: SimulationOptions) {
        preferences.edit().putString("altitude", options.altitudeMeters.toString())
            .putFloat("accuracy", options.accuracyMeters).putLong("interval", options.intervalMillis).apply()
    }

    var dynamicColor: Boolean
        get() = preferences.getBoolean("dynamic_color", false)
        set(value) { preferences.edit().putBoolean("dynamic_color", value).apply() }

    var notificationsPrompted: Boolean
        get() = preferences.getBoolean("notifications_prompted", false)
        set(value) { preferences.edit().putBoolean("notifications_prompted", value).apply() }

    var routeWorkspace: Boolean
        get() = preferences.getBoolean("route_workspace", false)
        set(value) { preferences.edit().putBoolean("route_workspace", value).apply() }

    var mapStyle: String
        get() = preferences.getString("map_style", "light") ?: "light"
        set(value) { require(value in setOf("auto", "light", "dark")); preferences.edit().putString("map_style", value).apply() }

    fun route(): RouteDraft = runCatching {
        val array = JSONArray(preferences.getString("route", "[]"))
        require(array.length() <= RouteNavigator.MAX_POINTS)
        val points = (0 until array.length()).map { i ->
            val pair = array.getJSONArray(i)
            Coordinates(pair.getDouble(0), pair.getDouble(1))
        }
        val speed = preferences.getString("speed", "5")!!.toDouble()
        require(speed.isFinite() && speed in 0.5..300.0)
        RouteDraft(points, speed, preferences.getBoolean("loop", false))
    }.getOrDefault(RouteDraft())

    fun saveRoute(route: RouteDraft) {
        require(route.points.size <= RouteNavigator.MAX_POINTS)
        require(route.speedKmh.isFinite() && route.speedKmh in 0.5..300.0)
        val points = JSONArray()
        route.points.forEach { points.put(JSONArray().put(it.latitude).put(it.longitude)) }
        preferences.edit().putString("route", points.toString()).putString("speed", route.speedKmh.toString())
            .putBoolean("loop", route.loop).apply()
    }
}
