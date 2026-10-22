package com.diegoca.gpsmocker

import android.content.Context
import java.util.UUID
import kotlin.math.abs

data class SavedPlace(val id: String, val name: String, val coordinates: Coordinates, val timestamp: Long)

class PlaceLibrary(context: Context) {
    private val data = UserDataRepository.get(context)
    fun favorites() = data.snapshot().favorites
    fun history() = data.snapshot().history
    fun favoriteAt(point: Coordinates) = favorites().firstOrNull { samePoint(it.coordinates, point) }
    fun saveFavorite(name: String, point: Coordinates) {
        val title = title(name)
        data.update { state ->
            val existing = state.favorites.firstOrNull { samePoint(it.coordinates, point) }
            val place = SavedPlace(existing?.id ?: UUID.randomUUID().toString(), title, point, System.currentTimeMillis())
            state.copy(favorites = listOf(place) + state.favorites.filterNot { it.id == existing?.id })
        }
    }
    fun rename(id: String, name: String) {
        val title = title(name)
        data.update { it.copy(favorites = it.favorites.map { p -> if (p.id == id) p.copy(name = title) else p }) }
    }
    fun deleteFavorite(id: String) { data.update { it.copy(favorites = it.favorites.filterNot { p -> p.id == id }) } }
    fun clearHistory() { data.update { it.copy(history = emptyList()) } }
    fun record(name: String, point: Coordinates) {
        val place = SavedPlace(UUID.randomUUID().toString(), name.trim().take(100).ifBlank { "Punto del mapa" }, point, System.currentTimeMillis())
        data.update { it.copy(history = (listOf(place) + it.history.filterNot { p -> samePoint(p.coordinates, point) }).take(50)) }
    }
    private fun title(name: String) = name.trim().take(100).also { require(it.isNotEmpty()) { "Escribe un nombre para el favorito." } }
    private fun samePoint(a: Coordinates, b: Coordinates) = abs(a.latitude - b.latitude) < 0.000001 && abs(a.longitude - b.longitude) < 0.000001
}
