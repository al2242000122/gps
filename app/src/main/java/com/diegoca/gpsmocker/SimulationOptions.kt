package com.diegoca.gpsmocker

data class SimulationOptions(
    val altitudeMeters: Double = 0.0,
    val accuracyMeters: Float = 3f,
    val intervalMillis: Long = 1000L
) {
    init {
        require(altitudeMeters.isFinite() && altitudeMeters in -1000.0..50000.0)
        require(accuracyMeters.isFinite() && accuracyMeters in 1f..1000f)
        require(intervalMillis in 250L..10000L)
    }
}

data class SimulatedFix(
    val coordinates: Coordinates,
    val options: SimulationOptions = SimulationOptions(),
    val speedMetersPerSecond: Float = 0f,
    val bearingDegrees: Float = 0f
)
