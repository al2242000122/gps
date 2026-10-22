package com.diegoca.gpsmocker

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlaceLibraryTest {
    @get:org.junit.Rule val userDataRule = UserDataTestRule()
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun `favorites persist support rename and deduplicate the same point`() {
        val library = PlaceLibrary(context)
        val point = Coordinates(40.0, -3.0)
        library.saveFavorite("Casa", point)
        library.saveFavorite("Oficina", point)
        val saved = PlaceLibrary(context).favorites().single()
        assertEquals("Oficina", saved.name)
        library.rename(saved.id, "Pruebas")
        assertEquals("Pruebas", library.favoriteAt(point)?.name)
        library.deleteFavorite(saved.id)
        assertTrue(library.favorites().isEmpty())
    }

    @Test fun `history is bounded recent first and can be cleared without losing favorites`() {
        val library = PlaceLibrary(context)
        library.saveFavorite("Casa", Coordinates.DEFAULT)
        repeat(60) { library.record("Punto $it", Coordinates(it.toDouble(), 1.0)) }
        library.record("Repetido", Coordinates(59.0, 1.0))
        assertEquals(50, library.history().size)
        assertEquals("Repetido", library.history().first().name)
        assertEquals(1, library.history().count { it.coordinates == Coordinates(59.0, 1.0) })
        library.clearHistory()
        assertTrue(library.history().isEmpty())
        assertEquals(1, library.favorites().size)
    }

    @Test fun `route and simulation options survive a fresh repository instance`() {
        val route = RouteDraft(listOf(Coordinates.DEFAULT, Coordinates(20.0, -99.0)), 12.0, true)
        val options = SimulationOptions(2240.0, 10f, 500)
        AppPreferences(context).apply { saveRoute(route); saveOptions(options) }
        assertEquals(route, AppPreferences(context).route())
        assertEquals(options, AppPreferences(context).options())
    }
}
