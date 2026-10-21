package com.diegoca.gpsmocker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class MockLocationEngineTest {
    private val backend = FakeBackend()
    private val engine = MockLocationEngine(backend)
    private val first = Coordinates(19.432608, -99.133209)
    private val second = Coordinates(40.4168, -3.7038)

    @Test fun `updates reuse the provider and send the latest target`() {
        engine.publish(first)
        engine.publish(second)
        assertEquals(listOf("install", "enable", "publish", "publish"), backend.events)
        assertEquals(listOf(first, second), backend.locations)
    }

    @Test fun `successful stop releases ownership and permits a fresh start`() {
        engine.publish(first)
        engine.stop()
        assertFalse(engine.installed)
        engine.publish(second)
        assertEquals(listOf("install", "enable", "publish", "remove", "install", "enable", "publish"), backend.events)
    }

    @Test fun `failed removal remains retryable and is never reported as success`() {
        engine.publish(first)
        backend.failRemove = true
        assertThrows(SecurityException::class.java) { engine.stop() }
        assertTrue(engine.installed)
        backend.failRemove = false
        engine.stop()
        assertFalse(engine.installed)
        assertEquals(2, backend.events.count { it == "remove" })
    }

    @Test fun `partially installed provider can be removed after enabling fails`() {
        backend.failEnable = true
        assertThrows(SecurityException::class.java) { engine.publish(first) }
        assertTrue(engine.installed)
        engine.stop()
        assertEquals(listOf("install", "enable", "remove"), backend.events)
    }

    @Test fun `publication failure does not lose ownership of provider`() {
        backend.failPublish = true
        assertThrows(SecurityException::class.java) { engine.publish(first) }
        assertTrue(engine.installed)
        engine.stop()
        assertFalse(engine.installed)
    }

    @Test fun `fresh process can remove a provider installed by a previous process`() {
        engine.stop()
        assertEquals(listOf("remove"), backend.events)
    }

    @Test fun `failed installation can be retried`() {
        backend.failInstall = true
        assertThrows(SecurityException::class.java) { engine.publish(first) }
        assertFalse(engine.installed)
        backend.failInstall = false
        engine.publish(second)
        assertTrue(engine.installed)
        assertEquals(listOf(second), backend.locations)
    }

    private class FakeBackend : MockLocationBackend {
        val events = mutableListOf<String>()
        val locations = mutableListOf<Coordinates>()
        var failInstall = false
        var failEnable = false
        var failPublish = false
        var failRemove = false

        override fun install() { events += "install"; if (failInstall) throw SecurityException() }
        override fun enable() { events += "enable"; if (failEnable) throw SecurityException() }
        override fun publish(coordinates: Coordinates) {
            events += "publish"
            if (failPublish) throw SecurityException()
            locations += coordinates
        }
        override fun remove() { events += "remove"; if (failRemove) throw SecurityException() }
    }
}
