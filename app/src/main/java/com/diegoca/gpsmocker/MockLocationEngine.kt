package com.diegoca.gpsmocker

/** A separate backend lets us test failed setup and cleanup without a GPS device. */
interface MockLocationBackend {
    fun install()
    fun enable()
    fun publish(fix: SimulatedFix)
    fun remove()
}

class MockLocationEngine(private val backend: MockLocationBackend) {
    var installed = false
        private set

    fun publish(coordinates: Coordinates, options: SimulationOptions = SimulationOptions(),
                speedMetersPerSecond: Float = 0f, bearingDegrees: Float = 0f) {
        if (!installed) {
            backend.install()
            // Keep ownership even if enabling or the first publication fails.
            installed = true
            backend.enable()
        }
        backend.publish(SimulatedFix(coordinates, options, speedMetersPerSecond, bearingDegrees))
    }

    fun stop() {
        // Also clean up a provider left behind by an earlier process.
        backend.remove()
        installed = false
    }
}
