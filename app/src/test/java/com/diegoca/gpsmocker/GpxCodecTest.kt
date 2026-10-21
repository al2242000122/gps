package com.diegoca.gpsmocker

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GpxCodecTest {
    @Test fun `exports a GPX 1_1 track that imports without losing coordinates`() {
        val points = listOf(Coordinates(19.504413, -99.182974), Coordinates(19.51, -99.18))
        val output = ByteArrayOutputStream()
        GpxCodec.write(output, points)
        assertEquals(points, GpxCodec.read(ByteArrayInputStream(output.toByteArray())))
        assertTrue(output.toString("UTF-8").contains("http://www.topografix.com/GPX/1/1"))
    }

    @Test fun `imports namespaced routes and removes consecutive duplicate points`() {
        val gpx = """<gpx xmlns="http://www.topografix.com/GPX/1/1"><rte>
            <rtept lat="40" lon="-3"/><rtept lat="40" lon="-3"/><rtept lat="41" lon="-4"/>
            </rte></gpx>"""
        assertEquals(listOf(Coordinates(40.0, -3.0), Coordinates(41.0, -4.0)), GpxCodec.read(gpx.byteInputStream()))
    }

    @Test fun `rejects invalid files coordinates and external entity declarations`() {
        for (gpx in listOf("<other><trkpt lat='1' lon='1'/><trkpt lat='2' lon='2'/></other>",
            "<gpx><trkpt lat='NaN' lon='2'/><trkpt lat='1' lon='2'/></gpx>",
            "<gpx><trkpt lat='1' lon='2'/></gpx>",
            "<!DOCTYPE gpx [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><gpx>&xxe;</gpx>")) {
            assertThrows(Exception::class.java) { GpxCodec.read(gpx.byteInputStream()) }
        }
    }

    @Test fun `rejects excessive file size and point count`() {
        assertThrows(IllegalArgumentException::class.java) { GpxCodec.read(ByteArray(GpxCodec.MAX_BYTES + 1).inputStream()) }
        val gpx = "<gpx><trk>" + (0..RouteNavigator.MAX_POINTS).joinToString("") { "<trkpt lat='1' lon='2'/>" } + "</trk></gpx>"
        assertThrows(IllegalArgumentException::class.java) { GpxCodec.read(gpx.byteInputStream()) }
    }
}
