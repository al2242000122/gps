package com.diegoca.gpsmocker

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28,36])
class RoutingClientTest {
    @get:org.junit.Rule val userDataRule=UserDataTestRule()
    private val a=Coordinates(19.43,-99.13)
    private val b=Coordinates(19.431,-99.129)
    private fun response(lines:List<List<Coordinates>>, distance:Double=200.0):JSONObject {
        val coordinates=JSONArray().apply { lines.forEach { line -> put(JSONArray().apply { line.forEach { put(JSONArray().put(it.longitude).put(it.latitude)) } }) } }
        return JSONObject().put("features",JSONArray().put(JSONObject().put("geometry",JSONObject().put("type","MultiLineString").put("coordinates",coordinates))
            .put("properties",JSONObject().put("distance",distance).put("time",120))))
    }
    @Test fun `geojson order profiles and connected segments are preserved`() {
        for (profile in RouteProfile.entries) {
            val middle=Coordinates(19.4305,-99.1295)
            val route=RoutingClient.decode(response(listOf(listOf(a,middle),listOf(middle,b))),RouteDraft(mode=RouteMode.STREET,profile=profile,waypoints=listOf(a,b)))
            assertTrue(route.ready);assertEquals(a,route.points.first());assertEquals(b,route.points.last())
            assertEquals(profile,route.profile);assertEquals("geoapify",route.source);assertEquals(120.0,route.estimatedSeconds!!,0.0)
        }
    }
    @Test fun `loop uses provider return geometry and survives backup`() {
        val c=Coordinates(19.431,-99.13)
        val route=RoutingClient.decode(response(listOf(listOf(a,b,c,a)),400.0),RouteDraft(loop=true,mode=RouteMode.STREET,waypoints=listOf(a,b)))
        assertTrue(route.ready);assertEquals(route.points.first(),route.points.last())
        assertEquals(route.points,RouteNavigator(route.points,5.0,true).points)
        assertEquals(route,BackupCodec.readRoute(BackupCodec.routeJson(route)))
        assertFalse(route.copy(loop=false).ready)
        assertFalse(route.edited(listOf(a,c)).ready)
    }
    @Test fun `invalid geometry disconnection distance and offroad endpoints are rejected`() {
        val draft=RouteDraft(mode=RouteMode.STREET,waypoints=listOf(a,b))
        listOf(response(listOf(listOf(a,b)),100001.0),
            response(listOf(listOf(a,b),listOf(Coordinates(20.0,-99.0),b))),
            response(listOf(listOf(Coordinates(20.0,-99.0),b))))
            .forEach { assertTrue(runCatching { RoutingClient.decode(it,draft) }.isFailure) }
        assertTrue(runCatching { RoutingClient.decode(response(listOf(listOf(a,b))),draft.copy(loop=true)) }.isFailure)
    }
    @Test fun `simplification preserves selected anchors and rejects excessive complex geometry`() {
        val line=(0..2500).map { i -> Coordinates(19.43+i*.000001,-99.13) }
        val reduced=RoutingClient.simplify(line,listOf(0,1234,2500),2.0)
        assertEquals(listOf(line.first(),line[1234],line.last()),reduced)
        val zigzag=(0..4000).map { i -> Coordinates(19.43+i*.00001,-99.13+(i%2)*.00006) }
        val draft=RouteDraft(mode=RouteMode.STREET,waypoints=listOf(zigzag.first(),zigzag.last()))
        assertTrue(runCatching { RoutingClient.decode(response(listOf(zigzag),50000.0),draft) }.isFailure)
    }

    @Test fun `obsolete queued calculations never open another billed connection`() {
        val executor=java.util.concurrent.Executors.newSingleThreadExecutor()
        val unblock=java.util.concurrent.CountDownLatch(1)
        val finished=java.util.concurrent.CountDownLatch(1)
        executor.execute { unblock.await() }
        val calls=java.util.concurrent.atomic.AtomicInteger()
        val client=RoutingClient("https://example.com/v1/route",executor) { uri ->
            calls.incrementAndGet()
            object : java.net.HttpURLConnection(uri.toURL()) {
                override fun connect() { }
                override fun disconnect() { }
                override fun usingProxy()=false
                override fun getOutputStream():java.io.OutputStream=java.io.ByteArrayOutputStream()
                override fun getResponseCode()=200
                override fun getInputStream():java.io.InputStream=response(listOf(listOf(a,b))).toString().byteInputStream()
            }
        }
        var received:RouteProfile?=null
        val draft=RouteDraft(mode=RouteMode.STREET,waypoints=listOf(a,b))
        try {
            client.calculate(draft) { fail("La respuesta obsoleta no debe entregarse") }
            client.calculate(draft.copy(profile=RouteProfile.DRIVE)) { received=it.getOrThrow().profile }
            executor.execute { finished.countDown() }
            unblock.countDown()
            assertTrue(finished.await(5,java.util.concurrent.TimeUnit.SECONDS))
            org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
            assertEquals(1,calls.get());assertEquals(RouteProfile.DRIVE,received)
        } finally { unblock.countDown();client.close() }
    }
}
