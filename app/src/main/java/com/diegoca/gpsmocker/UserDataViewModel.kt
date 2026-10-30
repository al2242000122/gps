package com.diegoca.gpsmocker

import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Process-owned writes finish even if their originating screen is destroyed. */
internal object UserDataWork {
    private var worker = newWorker()
    private fun newWorker() = Executors.newSingleThreadExecutor { job ->
        Thread(job, "gps-user-data").apply { isDaemon = true }
    }
    private val count = AtomicInteger()
    val pending get() = count.get()
    /** Tests discard their Android sandbox only after its process work has drained. */
    internal fun resetForTests() {
        check(pending == 0)
        worker.shutdown()
        check(worker.awaitTermination(2, TimeUnit.SECONDS))
        worker = newWorker()
    }
    fun <T> submit(block: suspend () -> T, complete: (Result<T>) -> Unit) {
        val main = Handler(Looper.getMainLooper())
        count.incrementAndGet()
        worker.execute {
            val result = runCatching { runBlocking { block() } }
            main.post {
                try { complete(result) } finally { count.decrementAndGet() }
            }
        }
    }
}

data class UserDataUiState(
    val ready: Boolean = false,
    val pending: Int = 0,
    val revision: Long = 0,
    val manualApplied: Long = 0,
    val loadError: Throwable? = null
)

/** Durable work is ordered off the UI thread; views observe state across recreation. */
class UserDataViewModel(application: Application) : AndroidViewModel(application) {
    private val mutable = MutableStateFlow(UserDataUiState())
    val state: StateFlow<UserDataUiState> = mutable
    init {
        UserDataWork.submit({ UserDataRepository.get(application) }) { result ->
            mutable.value = result.fold(
                { mutable.value.copy(ready = true) },
                { mutable.value.copy(loadError = it) }
            )
        }
    }
    fun <T> write(owner: LifecycleOwner, block: suspend () -> T, success: (T) -> Unit = {}, failure: (Throwable) -> Unit = {}, manualSelection: Boolean = false) {
        mutable.value = mutable.value.copy(pending = mutable.value.pending + 1)
        UserDataWork.submit(block) { result ->
            mutable.value = mutable.value.copy(pending = mutable.value.pending - 1, revision = mutable.value.revision + 1,
                manualApplied = mutable.value.manualApplied + if (manualSelection && result.isSuccess) 1 else 0)
            if (owner.lifecycle.currentState != Lifecycle.State.DESTROYED) result.fold(success, failure)
        }
    }
}
