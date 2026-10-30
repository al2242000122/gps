package com.diegoca.gpsmocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoordinatesTest {
    @Test fun `accepts decimal points and Spanish decimal commas`() {
        assertEquals(Coordinates(19.432608, -99.133209), Coordinates.parse(" 19,432608 ", " -99.133209 "))
    }

    @Test fun `accepts exact geographic boundaries`() {
        assertEquals(Coordinates(90.0, -180.0), Coordinates.parse("90", "-180"))
        assertEquals(Coordinates(-90.0, 180.0), Coordinates.parse("-90", "180"))
    }

    @Test fun `rejects missing malformed and out of range coordinates`() {
        for ((lat, lon) in listOf("" to "0", "0" to "", "abc" to "0", "91" to "0",
            "-90.1" to "0", "0" to "180.1", "0" to "-181", "1,2,3" to "0")) {
            assertNull("$lat / $lon", Coordinates.parse(lat, lon))
        }
    }

    @Test fun `rejects non finite values from text and service extras`() {
        for (value in listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY)) {
            assertNull(Coordinates.from(value, 0.0))
            assertNull(Coordinates.from(0.0, value))
            assertNull(Coordinates.parse(value.toString(), "0"))
            assertNull(Coordinates.parse("0", value.toString()))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun `constructor cannot create invalid coordinates`() {
        Coordinates(Double.NaN, 0.0)
    }
}
