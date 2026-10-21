package com.diegoca.gpsmocker

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import org.json.JSONArray
import org.json.JSONObject

/** MapLibre vector map with bundled JS/CSS. Only map data is requested over the network. */
class MapWorkspace(
    private val context: Context,
    val map: WebView,
    private val onSelect: (Coordinates) -> Unit,
    private val onMoveRoutePoint: (Int, Coordinates) -> Unit,
    private val onError: (String) -> Unit = {}
) {
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var closed = false
    private var selection = Coordinates.DEFAULT
    private var active: Coordinates? = null
    private var points = emptyList<Coordinates>()
    private var editing = false
    private var style = styleFor(context, "light")
    private var center = Coordinates.DEFAULT
    private var zoom = 15.0
    private var cameraCommand: JSONObject? = null
    private var lastErrorTime = 0L
    @Volatile private var state = "{}"

    init { configure() }

    @SuppressLint("SetJavaScriptEnabled")
    @Suppress("DEPRECATION")
    private fun configure() {
        val loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context)).build()
        map.setBackgroundColor(context.getColor(R.color.surface_soft))
        if (Build.VERSION.SDK_INT >= 29) map.isForceDarkAllowed = false
        map.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            userAgentString = "$userAgentString GPSMocker/0.3.1 (+https://github.com/al2242000122/gps)"
        }
        // MapLibre supplies its own light/dark palette. WebView must not recolor the canvas.
        if (Build.VERSION.SDK_INT >= 33) map.settings.isAlgorithmicDarkeningAllowed = false
        else if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING))
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(map.settings, false)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK))
            WebSettingsCompat.setForceDark(map.settings, WebSettingsCompat.FORCE_DARK_OFF)
        map.addJavascriptInterface(Bridge(), BRIDGE_NAME)
        map.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse? = loader.shouldInterceptRequest(request.url)
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean =
                request.isForMainFrame && request.url.toString() != ASSET_URL
            override fun onPageFinished(view: WebView?, url: String?) { if (url == ASSET_URL) { ready = true; render() } }
        }
        render()
        map.loadUrl(ASSET_URL)
    }

    fun style(preference: String) { style = styleFor(context, preference); render() }

    fun restore(saved: Bundle?, selection: Coordinates) {
        center = saved?.let { Coordinates.from(it.getDouble("centerLatitude", Double.NaN), it.getDouble("centerLongitude", Double.NaN)) } ?: selection
        zoom = saved?.getDouble("zoom", 15.0)?.takeIf { it.isFinite() && it in 2.0..19.0 } ?: 15.0
        this.selection = selection
        cameraCommand = camera(false)
        render()
    }

    fun select(point: Coordinates, center: Boolean) {
        selection = point
        if (center) { this.center = point; cameraCommand = camera(true) }
        render()
    }

    fun center(point: Coordinates) { center = point; cameraCommand = camera(true); render() }
    fun zoom(value: Double) { zoom = value.coerceIn(2.0, 19.0); cameraCommand = camera(true); render() }

    fun route(points: List<Coordinates>, isEditing: Boolean) {
        if (this.points == points && editing == isEditing) return
        this.points = points.toList(); editing = isEditing; render()
    }

    fun active(point: Coordinates?) { if (active != point) { active = point; render() } }

    fun save(out: Bundle) {
        out.putDouble("zoom", zoom)
        out.putDouble("centerLatitude", center.latitude)
        out.putDouble("centerLongitude", center.longitude)
    }

    fun close() {
        closed = true
        map.stopLoading()
        map.removeJavascriptInterface(BRIDGE_NAME)
        (map.parent as? android.view.ViewGroup)?.removeView(map)
        map.destroy()
    }

    private fun camera(animate: Boolean) = JSONObject().put("center", center.array()).put("zoom", zoom).put("animate", animate)

    private fun render() {
        val route = JSONArray()
        points.forEach { route.put(it.array()) }
        val value = JSONObject().put("selection", selection.array()).put("active", active?.array() ?: JSONObject.NULL)
            .put("points", route).put("editing", editing).put("style", style)
            .put("center", center.array()).put("zoom", zoom)
            .put("camera", cameraCommand ?: JSONObject.NULL)
        state = value.toString()
        if (ready && !closed) map.evaluateJavascript("window.GpsMap && window.GpsMap.render($state);", null)
        cameraCommand = null
    }

    private fun Coordinates.array() = JSONArray().put(longitude).put(latitude)

    private fun ui(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) { if (!closed) action() }
        else main.post { if (!closed) action() }
    }

    inner class Bridge {
        @JavascriptInterface fun initialState(): String = state

        @JavascriptInterface fun onReady() { ui { ready = true; render() } }

        @JavascriptInterface fun onSelect(latitude: Double, longitude: Double) {
            val point = Coordinates.from(latitude, normalize(longitude)) ?: return
            ui { this@MapWorkspace.onSelect(point) }
        }

        @JavascriptInterface fun onMoveRoutePoint(index: Int, latitude: Double, longitude: Double) {
            val point = Coordinates.from(latitude, normalize(longitude)) ?: return
            ui { if (editing && index in points.indices) this@MapWorkspace.onMoveRoutePoint(index, point) }
        }

        @JavascriptInterface fun onCameraChanged(latitude: Double, longitude: Double, zoom: Double) {
            val point = Coordinates.from(latitude, normalize(longitude)) ?: return
            ui {
                if (!closed && zoom.isFinite() && zoom in 2.0..19.0) { center = point; this@MapWorkspace.zoom = zoom }
            }
        }

        @JavascriptInterface fun onError() { ui {
            val now = android.os.SystemClock.elapsedRealtime()
            if (!closed && (lastErrorTime == 0L || now - lastErrorTime > 10000)) {
                lastErrorTime = now
                onError(context.getString(R.string.map_load_error))
            }
        } }
    }

    companion object {
        const val BRIDGE_NAME = "GpsMocker"
        const val ASSET_URL = "https://appassets.androidplatform.net/assets/map/index.html"
        fun styleFor(context: Context, preference: String): String {
            val dark = preference == "dark" || preference == "auto" &&
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
            return "https://tiles.openfreemap.org/styles/" + if (dark) "dark" else "liberty"
        }
        private fun normalize(longitude: Double) = ((longitude + 180) % 360 + 360) % 360 - 180
    }
}
