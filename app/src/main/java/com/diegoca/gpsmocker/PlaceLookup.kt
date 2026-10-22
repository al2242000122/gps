package com.diegoca.gpsmocker

import android.content.Context
import android.location.Geocoder
import android.os.Handler
import android.os.Looper
import java.util.Locale
import java.util.concurrent.Executors

data class PlaceResult(val title: String, val address: String, val coordinates: Coordinates)

/** Uses Android's geocoding provider; no API key and no autocomplete traffic. */
@Suppress("DEPRECATION")
class PlaceLookup(context: Context) {
    private val geocoder = Geocoder(context.applicationContext, Locale("es"))
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private var closed = false
    private var queryId = 0
    private var reverseId = 0
    private var searchTask: java.util.concurrent.Future<*>? = null
    private var reverseTask: java.util.concurrent.Future<*>? = null
    private val reverseCache = object : LinkedHashMap<Coordinates, PlaceResult?>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Coordinates, PlaceResult?>?) = size > 100
    }

    fun search(query: String, result: (Result<List<PlaceResult>>) -> Unit) {
        val id = ++queryId
        searchTask?.cancel(true)
        searchTask = executor.submit {
            val response = runCatching {
                check(Geocoder.isPresent()) { "Este teléfono no dispone de un servicio de búsqueda de lugares. Puedes seleccionar el punto en el mapa." }
                require(query.trim().length in 2..200) { "Escribe al menos dos caracteres." }
                geocoder.getFromLocationName(query.trim(), 8).orEmpty().mapNotNull { address ->
                    val point = Coordinates.from(address.latitude, address.longitude) ?: return@mapNotNull null
                    val line = address.getAddressLine(0) ?: address.locality ?: query
                    PlaceResult(address.featureName ?: line.substringBefore(','), line, point)
                }.distinctBy { it.coordinates }
            }
            main.post { if (!closed && id == queryId) result(response) }
        }
    }

    fun reverse(point: Coordinates, result: (PlaceResult?) -> Unit) {
        val id = ++reverseId
        reverseTask?.cancel(true)
        if (reverseCache.containsKey(point)) { result(reverseCache[point]); return }
        reverseTask = executor.submit {
            val response = runCatching {
                if (!Geocoder.isPresent()) return@runCatching null
                geocoder.getFromLocation(point.latitude, point.longitude, 1)?.firstOrNull()?.let { address ->
                    val line = address.getAddressLine(0) ?: address.locality ?: return@let null
                    PlaceResult(address.featureName ?: line.substringBefore(','), line, point)
                }
            }.getOrNull()
            main.post {
                if (!closed && id == reverseId) {
                    reverseCache[point] = response
                    result(response)
                }
            }
        }
    }

    fun cancelReverse() { reverseId++; reverseTask?.cancel(true) }
    fun cancelSearch() { queryId++; searchTask?.cancel(true) }
    fun close() { closed = true; queryId++; reverseId++; executor.shutdownNow() }
}
