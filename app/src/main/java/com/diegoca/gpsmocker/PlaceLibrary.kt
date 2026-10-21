package com.diegoca.gpsmocker

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs

data class SavedPlace(val id: String, val name: String, val coordinates: Coordinates, val timestamp: Long)

class PlaceLibrary(context: Context) {
    private val preferences = context.getSharedPreferences("places", Context.MODE_PRIVATE)

    fun favorites(): List<SavedPlace> = read("favorites")
    fun history(): List<SavedPlace> = read("history")
    fun favoriteAt(point: Coordinates): SavedPlace? = favorites().firstOrNull { samePoint(it.coordinates, point) }

    fun saveFavorite(name: String, point: Coordinates) {
        val title = name.trim().take(100)
        require(title.isNotEmpty()) { "Escribe un nombre para el favorito." }
        val previous = favorites()
        val existing = previous.firstOrNull { samePoint(it.coordinates, point) }
        val place = SavedPlace(existing?.id ?: UUID.randomUUID().toString(), title, point, System.currentTimeMillis())
        write("favorites", listOf(place) + previous.filterNot { it.id == existing?.id })
    }

    fun rename(id: String, name: String) {
        val title = name.trim().take(100)
        require(title.isNotEmpty()) { "El nombre no puede estar vacío." }
        write("favorites", favorites().map { if (it.id == id) it.copy(name = title) else it })
    }

    fun deleteFavorite(id: String) = write("favorites", favorites().filterNot { it.id == id })
    fun clearHistory() = write("history", emptyList())

    fun record(name: String, point: Coordinates) {
        val place = SavedPlace(UUID.randomUUID().toString(), name.trim().take(100).ifBlank { "Punto del mapa" }, point, System.currentTimeMillis())
        write("history", (listOf(place) + history().filterNot { samePoint(it.coordinates, point) }).take(50))
    }

    private fun read(key: String): List<SavedPlace> = runCatching {
        val array = JSONArray(preferences.getString(key, "[]"))
        (0 until array.length()).mapNotNull { index ->
            runCatching {
                val value = array.getJSONObject(index)
                val point = Coordinates.from(value.getDouble("lat"), value.getDouble("lon")) ?: return@runCatching null
                SavedPlace(value.getString("id"), value.getString("name"), point, value.optLong("time"))
            }.getOrNull()
        }
    }.getOrDefault(emptyList())

    private fun write(key: String, places: List<SavedPlace>) {
        val array = JSONArray()
        places.forEach { place -> array.put(JSONObject().put("id", place.id).put("name", place.name)
            .put("lat", place.coordinates.latitude).put("lon", place.coordinates.longitude).put("time", place.timestamp)) }
        preferences.edit().putString(key, array.toString()).apply()
    }

    private fun samePoint(a: Coordinates, b: Coordinates) = abs(a.latitude - b.latitude) < 0.000001 && abs(a.longitude - b.longitude) < 0.000001
}
