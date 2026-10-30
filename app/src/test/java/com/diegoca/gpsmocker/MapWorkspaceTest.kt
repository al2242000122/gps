package com.diegoca.gpsmocker

import android.content.Context
import android.net.Uri
import android.webkit.ValueCallback
import android.webkit.WebView
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35, 36])
class MapWorkspaceTest {
    class RecordingMap(context: Context) : WebView(context) {
        val scripts = mutableListOf<String>()
        override fun evaluateJavascript(script: String, callback: ValueCallback<String>?) { scripts += script }
    }

    @Test fun `position ticks omit unchanged geometry and keep bootstrap complete`() {
        val map = RecordingMap(RuntimeEnvironment.getApplication())
        val workspace = MapWorkspace(map.context, map, {}, { _, _ -> })
        val bridge = workspace.Bridge()
        bridge.onReady()
        val route = (0 until 2000).map { Coordinates(19.0 + it * 0.00001, -99.0) }
        workspace.route(route, false)
        val full = bridge.initialState()
        assertEquals(2000, JSONObject(full).getJSONArray("points").length())
        workspace.active(Coordinates(19.001, -99.0))
        val delta = map.scripts.last()
        assertTrue(delta.contains("\"active\""))
        assertFalse(delta.contains("\"points\""))
        assertTrue(delta.length < 300)
        assertEquals(2000, JSONObject(bridge.initialState()).getJSONArray("points").length())
        assertEquals(19.001, JSONObject(bridge.initialState()).getJSONArray("active").getDouble(1), 0.000001)
        val count = map.scripts.size
        workspace.route(route, false)
        assertEquals(count, map.scripts.size)
        workspace.close()
    }

    @Test fun `only the HTTPS tile origin is allowed as a remote resource`() {
        assertTrue(MapWorkspace.allowsRemoteMapRequest(Uri.parse("https://tiles.openfreemap.org/styles/liberty")))
        for (url in listOf("http://tiles.openfreemap.org/a", "https://tiles.openfreemap.org.evil.test/a",
            "https://evil.test/a", "https://user@tiles.openfreemap.org/a", "https://tiles.openfreemap.org:8443/a")) {
            assertFalse(url, MapWorkspace.allowsRemoteMapRequest(Uri.parse(url)))
        }
    }
}
