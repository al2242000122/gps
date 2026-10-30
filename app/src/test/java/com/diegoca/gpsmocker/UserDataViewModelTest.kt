package com.diegoca.gpsmocker

import android.app.Application
import android.os.Looper
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 35, 36])
class UserDataViewModelTest {
    @get:org.junit.Rule val userDataRule = UserDataTestRule()
    private val application: Application get() = RuntimeEnvironment.getApplication()
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.CREATED }
        override val lifecycle: Lifecycle get() = registry
    }

    @Test fun `slow durable work does not block the main looper and survives its screen`() {
        val model = UserDataViewModel(application)
        settleDataWork()
        assertTrue(model.state.value.ready)
        val owner = Owner()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        var acknowledged = false
        var wrongThread = false
        val target = Coordinates(10.0, 20.0)
        model.write(owner, {
            wrongThread = Thread.currentThread() == Looper.getMainLooper().thread
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
            SimulationStore(application).saveSelectionAwait(target, "Durable", "")
        }, { acknowledged = true })
        try {
            assertTrue(entered.await(2, TimeUnit.SECONDS))
            assertEquals(1, model.state.value.pending)
            var uiResponsive = false
            android.os.Handler(Looper.getMainLooper()).post { uiResponsive = true }
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(uiResponsive)
            assertEquals(Coordinates.DEFAULT, SimulationStore(application).selection())
            owner.registry.currentState = Lifecycle.State.DESTROYED
        } finally { release.countDown() }
        settleDataWork()
        assertFalse(wrongThread)
        assertFalse(acknowledged)
        assertEquals(0, model.state.value.pending)
        assertEquals(target, SimulationStore(application).selection())
    }

    @Test fun `queued writes keep order and a failure preserves durable data`() {
        val model = UserDataViewModel(application)
        settleDataWork()
        val owner = Owner()
        val events = mutableListOf<String>()
        model.write(owner, { SimulationStore(application).saveSelectionAwait(Coordinates(1.0, 2.0), "First", "") }, { events += "first" })
        model.write(owner, { error("disk failure") }, failure = { events += "failed" })
        model.write(owner, { SimulationStore(application).saveSelectionAwait(Coordinates(3.0, 4.0), "Last", "") }, { events += "last" })
        settleDataWork()
        assertEquals(listOf("first", "failed", "last"), events)
        assertEquals(Coordinates(3.0, 4.0), SimulationStore(application).selection())
        assertEquals("Last", SimulationStore(application).selectionName)
        assertEquals(0, model.state.value.pending)
    }

    @Test fun `late reverse geocoding cannot overwrite a newer selection`() {
        val model = UserDataViewModel(application)
        settleDataWork()
        val store = SimulationStore(application)
        val old = Coordinates(1.0, 2.0)
        val current = Coordinates(3.0, 4.0)
        val owner = Owner()
        model.write(owner, { store.saveSelectionAwait(old, "Old", "") })
        model.write(owner, { store.saveSelectionAwait(current, "Current", "") })
        model.write(owner, { store.enrichSelectionAwait(old, "Late result", "Stale address") })
        settleDataWork()
        assertEquals(current, store.selection())
        assertEquals("Current", store.selectionName)
        assertEquals("", store.selectionAddress)
    }
}
