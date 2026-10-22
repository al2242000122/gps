package com.diegoca.gpsmocker

import android.content.Context

enum class RouteMode { DIRECT, STREET, IMPORTED }
enum class RouteProfile(val api: String, val label: String) { WALK("walk", "Caminar"), BICYCLE("bicycle", "Bicicleta"), DRIVE("drive", "Automóvil") }
data class RouteDraft(
    val points: List<Coordinates> = emptyList(), val speedKmh: Double = 5.0, val loop: Boolean = false,
    val mode: RouteMode = RouteMode.DIRECT, val profile: RouteProfile = RouteProfile.WALK,
    val waypoints: List<Coordinates> = emptyList(), val calculatedLoop: Boolean? = null,
    val estimatedSeconds: Double? = null, val source: String = ""
) {
    val ready get() = points.size >= 2 && (mode != RouteMode.STREET || calculatedLoop == loop && waypoints.size >= 2 && source == "geoapify")
    fun edited(values: List<Coordinates>) = if (mode == RouteMode.STREET) copy(points = emptyList(), waypoints = values, calculatedLoop = null, estimatedSeconds = null, source = "") else copy(points = values)
    val editablePoints get() = if (mode == RouteMode.STREET) waypoints else points
    val attribution get() = if (source == "geoapify") "Powered by Geoapify · © OpenStreetMap contributors" else ""
}

class AppPreferences(context: Context) {
    private val data = UserDataRepository.get(context)
    private val operational = context.getSharedPreferences("preferences", Context.MODE_PRIVATE)
    fun options() = data.snapshot().options
    fun saveOptions(options: SimulationOptions) { data.update { it.copy(options = options) } }
    fun saveSettings(options: SimulationOptions, dynamic: Boolean) { data.update { it.copy(options = options, dynamicColor = dynamic) } }
    var dynamicColor: Boolean
        get() = data.snapshot().dynamicColor
        set(value) { data.update { it.copy(dynamicColor = value) } }
    var notificationsPrompted: Boolean
        get() = operational.getBoolean("notifications_prompted", false)
        set(value) { operational.edit().putBoolean("notifications_prompted", value).apply() }
    var routeWorkspace: Boolean
        get() = operational.getBoolean("route_workspace", false)
        set(value) { operational.edit().putBoolean("route_workspace", value).apply() }
    var mapStyle: String
        get() = data.snapshot().mapStyle
        set(value) { require(value in setOf("auto", "light", "dark")); data.update { it.copy(mapStyle = value) } }
    fun route() = data.snapshot().route
    fun saveRoute(route: RouteDraft) { BackupCodec.readRoute(BackupCodec.routeJson(route)); data.update { it.copy(route = route) } }
}
