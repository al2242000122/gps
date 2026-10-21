package com.diegoca.gpsmocker

import org.junit.Assert.*
import org.junit.Test

class RouteNavigatorTest {
    private val a = Coordinates(0.0, 0.0)
    private val b = Coordinates(0.0, 0.001)

    @Test fun `distance follows elapsed time at the requested speed`() {
        val route = RouteNavigator(listOf(a, b), 36.0, false)
        val position = route.advance(5.0)
        assertEquals(50.0, RouteNavigator.distance(a, position), 0.01)
        assertEquals(50.0, route.traveledMeters, 0.0001)
        assertFalse(route.completed)
    }

    @Test fun `changing update cadence does not change position or speed`() {
        val fastUpdates = RouteNavigator(listOf(a, b), 5.0, false)
        val slowUpdates = RouteNavigator(listOf(a, b), 5.0, false)
        repeat(20) { fastUpdates.advance(0.5) }
        slowUpdates.advance(10.0)
        assertTrue(RouteNavigator.distance(fastUpdates.position(), slowUpdates.position()) < 0.001)
    }

    @Test fun `completion clamps to the last point and remains complete`() {
        val route = RouteNavigator(listOf(a, b), 36.0, false)
        assertEquals(b, route.advance(1000.0))
        assertTrue(route.completed)
        assertEquals(1.0, route.fraction, 0.0)
        assertEquals(b, route.advance(1000.0))
    }

    @Test fun `loop returns through a closing segment without teleporting`() {
        val route = RouteNavigator(listOf(a, b), 36.0, true)
        val oneWay = RouteNavigator.distance(a, b)
        assertEquals(oneWay * 2, route.totalMeters, 0.001)
        route.advance(oneWay * 1.5 / 10)
        assertEquals(oneWay * 0.5, RouteNavigator.distance(a, route.position()), 0.01)
        assertFalse(route.completed)
        route.advance(oneWay * 0.5 / 10 + 1)
        assertEquals(10.0, RouteNavigator.distance(a, route.position()), 0.01)
    }

    @Test fun `crosses the date line along the short path`() {
        val halfway = RouteNavigator.interpolate(Coordinates(0.0, 179.0), Coordinates(0.0, -179.0), 0.5)
        assertEquals(180.0, kotlin.math.abs(halfway.longitude), 0.001)
        assertEquals(0.0, halfway.latitude, 0.001)
    }

    @Test fun `skips duplicate adjacent points`() {
        val route = RouteNavigator(listOf(a, a, b, b), 36.0, false)
        assertEquals(90f, route.bearing(), 0.001f)
        assertEquals(50.0, RouteNavigator.distance(a, route.advance(5.0)), 0.01)
    }

    @Test fun `rejects invalid routes and non finite speed`() {
        for (points in listOf(emptyList(), listOf(a), listOf(a, a))) {
            assertThrows(IllegalArgumentException::class.java) { RouteNavigator(points, 5.0, false) }
        }
        for (speed in listOf(Double.NaN, Double.POSITIVE_INFINITY, 0.0, 301.0)) {
            assertThrows(IllegalArgumentException::class.java) { RouteNavigator(listOf(a, b), speed, false) }
        }
    }

    @Test fun `simulation settings reject unsafe numeric values`() {
        assertThrows(IllegalArgumentException::class.java) { SimulationOptions(Double.NaN) }
        assertThrows(IllegalArgumentException::class.java) { SimulationOptions(accuracyMeters = Float.POSITIVE_INFINITY) }
        assertThrows(IllegalArgumentException::class.java) { SimulationOptions(intervalMillis = 0) }
        assertThrows(IllegalArgumentException::class.java) { SimulationOptions(altitudeMeters = 50001.0) }
    }

    @Test fun `loop accepts the maximum number of input points`() {
        val points = List(RouteNavigator.MAX_POINTS) { if (it % 2 == 0) a else b }
        val route = RouteNavigator(points, 5.0, true)
        assertTrue(route.totalMeters > 0)
    }
}
