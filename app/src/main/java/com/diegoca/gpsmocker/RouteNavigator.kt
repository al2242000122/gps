package com.diegoca.gpsmocker

import kotlin.math.*

/** Advances by elapsed time, not by number of updates, so interval changes keep the same speed. */
class RouteNavigator(points: List<Coordinates>, val speedKmh: Double, val loop: Boolean) {
    val points: List<Coordinates> = if (loop && points.firstOrNull() != points.lastOrNull()) points + points.first() else points.toList()
    private val lengths = this.points.zipWithNext { a, b -> distance(a, b) }
    val totalMeters = lengths.sum()
    var traveledMeters = 0.0
        private set
    val completed: Boolean get() = !loop && traveledMeters >= totalMeters
    val fraction: Double get() = (traveledMeters / totalMeters).coerceIn(0.0, 1.0)
    val durationSeconds: Double get() = totalMeters / (speedKmh / 3.6)

    init {
        require(points.size in 2..MAX_POINTS) { "La ruta necesita entre 2 y $MAX_POINTS puntos." }
        require(speedKmh.isFinite() && speedKmh in 0.5..300.0) { "La velocidad debe estar entre 0,5 y 300 km/h." }
        require(totalMeters.isFinite() && totalMeters >= 1.0) { "La ruta debe medir al menos un metro." }
    }

    fun advance(seconds: Double): Coordinates {
        require(seconds.isFinite() && seconds >= 0)
        val next = traveledMeters + seconds * speedKmh / 3.6
        traveledMeters = if (loop) next % totalMeters else next.coerceAtMost(totalMeters)
        return position()
    }

    fun position(): Coordinates {
        var remaining = traveledMeters
        for (i in lengths.indices) {
            val length = lengths[i]
            if (length > 0 && remaining < length) return interpolate(points[i], points[i + 1], remaining / length)
            remaining -= length
        }
        return points.last()
    }

    fun bearing(): Float {
        if (completed) return 0f
        var remaining = traveledMeters
        for (i in lengths.indices) {
            if (lengths[i] > 0 && remaining < lengths[i]) return bearing(position(), points[i + 1])
            remaining -= lengths[i]
        }
        return 0f
    }

    companion object {
        const val MAX_POINTS = 2000
        private const val EARTH_RADIUS = 6371008.8

        fun distance(a: Coordinates, b: Coordinates): Double {
            val lat = Math.toRadians(b.latitude - a.latitude)
            val lon = Math.toRadians(b.longitude - a.longitude)
            val h = sin(lat / 2).pow(2) + cos(Math.toRadians(a.latitude)) *
                cos(Math.toRadians(b.latitude)) * sin(lon / 2).pow(2)
            return 2 * EARTH_RADIUS * asin(sqrt(h.coerceIn(0.0, 1.0)))
        }

        fun bearing(a: Coordinates, b: Coordinates): Float {
            val lat1 = Math.toRadians(a.latitude)
            val lat2 = Math.toRadians(b.latitude)
            val lon = Math.toRadians(b.longitude - a.longitude)
            return ((Math.toDegrees(atan2(sin(lon) * cos(lat2),
                cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(lon))) + 360) % 360).toFloat()
        }

        fun interpolate(a: Coordinates, b: Coordinates, fraction: Double): Coordinates {
            val f = fraction.coerceIn(0.0, 1.0)
            if (f == 0.0) return a
            if (f == 1.0) return b
            val angle = distance(a, b) / EARTH_RADIUS
            if (abs(sin(angle)) < 1e-9) {
                val delta = ((b.longitude - a.longitude + 540) % 360) - 180
                return Coordinates(a.latitude + (b.latitude - a.latitude) * f,
                    ((a.longitude + delta * f + 540) % 360) - 180)
            }
            val k1 = sin((1 - f) * angle) / sin(angle)
            val k2 = sin(f * angle) / sin(angle)
            val lat1 = Math.toRadians(a.latitude); val lat2 = Math.toRadians(b.latitude)
            val lon1 = Math.toRadians(a.longitude); val lon2 = Math.toRadians(b.longitude)
            val x = k1 * cos(lat1) * cos(lon1) + k2 * cos(lat2) * cos(lon2)
            val y = k1 * cos(lat1) * sin(lon1) + k2 * cos(lat2) * sin(lon2)
            val z = k1 * sin(lat1) + k2 * sin(lat2)
            return Coordinates(Math.toDegrees(atan2(z, sqrt(x * x + y * y))), Math.toDegrees(atan2(y, x)))
        }
    }
}
