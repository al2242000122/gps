package com.diegoca.gpsmocker

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.Executors
import android.os.Handler
import android.os.Looper
import kotlin.math.*

class RoutingException(val reason: String, message: String) : Exception(message)

/** Coordinates only, no device identifiers or advertising IDs. Never embeds a provider API key. */
class RoutingClient(private val endpoint: String = BuildConfig.ROUTING_URL,
                    private val io: java.util.concurrent.ExecutorService = Executors.newSingleThreadExecutor(),
                    private val openConnection: (URI) -> HttpURLConnection = { it.toURL().openConnection() as HttpURLConnection }) {
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var revision = 0
    @Volatile private var connection: HttpURLConnection? = null
    private var closed = false
    fun calculate(draft: RouteDraft, callback: (Result<RouteDraft>) -> Unit) {
        cancel(); val id = revision
        io.execute {
            if (closed || id != revision) return@execute
            val result = runCatching { fetch(draft, id) }
            main.post { if (!closed && id == revision) callback(result) }
        }
    }
    private fun fetch(draft: RouteDraft, id: Int): RouteDraft {
        if (endpoint.isBlank()) throw RoutingException("UNAVAILABLE", "El servicio de rutas aún no está disponible. Usa una ruta directa o importa GPX.")
        val uri = URI(endpoint)
        require(uri.scheme == "https" && uri.host != null && uri.userInfo == null && uri.fragment == null)
        val waypoints = draft.waypoints
        val requested = if (draft.loop && waypoints.firstOrNull() != waypoints.lastOrNull()) waypoints + waypoints.first() else waypoints
        require(requested.size in 2..20) { "Elige entre 2 y 20 puntos, contando el regreso al inicio." }
        require(requested.zipWithNext { a,b -> RouteNavigator.distance(a,b) }.sum() <= 100_000) { "La ruta admite hasta 100 km." }
        val http = openConnection(uri)
        connection = http
        try {
            check(!closed && id == revision) { "Cálculo cancelado." }
            http.requestMethod = "POST"; http.connectTimeout = 10_000; http.readTimeout = 20_000
            http.instanceFollowRedirects = false; http.doOutput = true
            http.setRequestProperty("Content-Type", "application/json")
            val body = JSONObject().put("points", BackupCodec.points(waypoints)).put("profile", draft.profile.api).put("loop", draft.loop)
            http.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            when (http.responseCode) {
                200 -> return decode(JSONObject(BackupCodec.boundedRead(http.inputStream, BackupCodec.MAX_BYTES).toString(Charsets.UTF_8)), draft)
                429 -> throw RoutingException("QUOTA_EXHAUSTED", "Se agotó el cupo compartido de rutas. Intenta más tarde o usa GPX/ruta directa.")
                400, 404, 422 -> throw RoutingException("NO_ROUTE", "No se encontró un recorrido válido para esos puntos y perfil.")
                else -> throw RoutingException("UNAVAILABLE", "No se pudo calcular la ruta. Se conserva el recorrido anterior.")
            }
        } finally { http.disconnect(); if (connection === http) connection = null }
    }
    fun cancel() { revision++; connection?.disconnect(); connection = null }
    fun close() { closed = true; cancel(); io.shutdownNow() }
    companion object {
        fun decode(root: JSONObject, draft: RouteDraft): RouteDraft {
            val feature = root.getJSONArray("features").also { require(it.length() == 1) }.getJSONObject(0)
            val geometry = feature.getJSONObject("geometry")
            require(geometry.getString("type") == "MultiLineString") { "Geometría de ruta inválida." }
            val lines = geometry.getJSONArray("coordinates")
            val points = mutableListOf<Coordinates>()
            for (i in 0 until lines.length()) {
                val line = lines.getJSONArray(i); require(line.length() >= 2)
                for (j in 0 until line.length()) {
                    val pair = line.getJSONArray(j); require(pair.length() >= 2)
                    val point = Coordinates(pair.getDouble(1), pair.getDouble(0))
                    if (j == 0 && points.isNotEmpty()) require(RouteNavigator.distance(points.last(), point) <= 1.0) { "El proveedor devolvió segmentos desconectados." }
                    if (points.lastOrNull() != point) points += point
                    require(points.size <= 20_000) { "La ruta es demasiado compleja." }
                }
            }
            require(points.size >= 2)
            val properties = feature.getJSONObject("properties")
            val distance = properties.getDouble("distance"); val duration = properties.getDouble("time")
            require(distance.isFinite() && distance in 1.0..100_000.0 && duration.isFinite() && duration >= 0)
            require(points.zipWithNext { a,b -> RouteNavigator.distance(a,b) }.sum() <= 100_000)
            val expected = draft.waypoints
            require(expected.size >= 2)
            val end = if (draft.loop) expected.first() else expected.last()
            require(RouteNavigator.distance(points.first(), expected.first()) <= 100 && RouteNavigator.distance(points.last(), end) <= 100) { "No hay una calle cercana a los puntos elegidos." }
            if (draft.loop) {
                require(RouteNavigator.distance(points.last(), points.first()) <= 1.0) { "El circuito no regresa al origen por calles." }
                points[points.lastIndex] = points.first()
            }
            // Keep every selected waypoint's nearest vertex. Simplification may deviate at most 2 m.
            val anchors = mutableSetOf(0, points.lastIndex)
            for (waypoint in expected) anchors += points.indices.minBy { RouteNavigator.distance(points[it], waypoint) }
            val simplified = simplify(points, anchors.sorted(), 2.0)
            require(simplified.size <= RouteNavigator.MAX_POINTS) { "La ruta supera 2000 puntos. Elige un recorrido más corto." }
            return draft.copy(points = simplified, mode = RouteMode.STREET, calculatedLoop = draft.loop,
                estimatedSeconds = duration, source = "geoapify")
        }
        internal fun simplify(points: List<Coordinates>, anchors: List<Int>, tolerance: Double): List<Coordinates> {
            val keep = anchors.toMutableSet()
            val stack = java.util.ArrayDeque<Pair<Int,Int>>()
            anchors.zipWithNext().forEach { stack.add(it) }
            while (stack.isNotEmpty()) {
                val (a,b) = stack.removeLast(); if (b <= a + 1) continue
                var maximum = tolerance; var index = -1
                for (i in a+1 until b) {
                    val distance = segmentDistance(points[i], points[a], points[b])
                    if (distance > maximum) { maximum = distance; index = i }
                }
                if (index >= 0) { keep += index; stack.add(a to index); stack.add(index to b) }
            }
            return keep.sorted().map { points[it] }
        }
        private fun segmentDistance(p: Coordinates, a: Coordinates, b: Coordinates): Double {
            val angle = RouteNavigator.distance(a, p) / 6371008.8
            val delta = Math.toRadians((RouteNavigator.bearing(a,p) - RouteNavigator.bearing(a,b)).toDouble())
            val along = atan2(sin(angle) * cos(delta), cos(angle)) * 6371008.8
            return if (along < 0) RouteNavigator.distance(p,a)
            else if (along > RouteNavigator.distance(a,b)) RouteNavigator.distance(p,b)
            else abs(asin((sin(angle) * sin(delta)).coerceIn(-1.0,1.0))) * 6371008.8
        }
    }
}
