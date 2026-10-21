package com.diegoca.gpsmocker

data class Coordinates(val latitude: Double, val longitude: Double) {
    init {
        require(latitude.isFinite() && latitude in -90.0..90.0)
        require(longitude.isFinite() && longitude in -180.0..180.0)
    }

    companion object {
        val DEFAULT = Coordinates(19.432608, -99.133209)

        fun parse(latitude: String, longitude: String): Coordinates? {
            fun number(value: String) = value.trim().replace(',', '.').toDoubleOrNull()
            val lat = number(latitude) ?: return null
            val lon = number(longitude) ?: return null
            return from(lat, lon)
        }

        fun from(latitude: Double, longitude: Double): Coordinates? =
            if (latitude.isFinite() && longitude.isFinite() &&
                latitude in -90.0..90.0 && longitude in -180.0..180.0
            ) Coordinates(latitude, longitude) else null
    }
}
