package com.diegoca.gpsmocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.*

data class SimulationSnapshot(
    val coordinates: Coordinates,
    val routePoints: List<Coordinates> = emptyList(),
    val paused: Boolean = false,
    val completed: Boolean = false,
    val fraction: Double = 0.0,
    val speedKmh: Double = 0.0
)

open class MockLocationService : Service() {
    private lateinit var engine: MockLocationEngine
    private lateinit var store: SimulationStore
    private val handler = Handler(Looper.getMainLooper())
    private var coordinates: Coordinates? = null
    private var navigator: RouteNavigator? = null
    private var options = SimulationOptions()
    private var paused = false
    private var lastTick = 0L
    private var lastNotification = 0L
    private var shuttingDown = false
    private val wakeLock by lazy {
        getSystemService(PowerManager::class.java).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "$packageName:simulation"
        ).apply { setReferenceCounted(false) }
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!isRunning) return
            try {
                publishPosition(advance = true)
                handler.postDelayed(this, options.intervalMillis)
            } catch (error: Exception) { fail(error) }
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = SimulationStore(this)
        engine = MockLocationEngine(createBackend())
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    protected open fun createBackend(): MockLocationBackend =
        AndroidMockLocationBackend(getSystemService(LocationManager::class.java))

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            when (intent?.action) {
                ACTION_STOP -> finishSimulation(getString(R.string.location_stopped), false)
                ACTION_START, ACTION_START_ROUTE -> startSimulation(intent)
                ACTION_TOGGLE_PAUSE -> {
                    if (isRunning && navigator?.completed == false) {
                        // Account for the final moving interval before freezing the route.
                        if (!paused) publishPosition(advance = true)
                        paused = !paused
                        lastTick = SystemClock.elapsedRealtime()
                        publishPosition(advance = false)
                        showForeground()
                        store.writeStatus(statusMessage())
                    } else if (!isRunning) stopSelf()
                }
                ACTION_OPTIONS -> {
                    if (isRunning) {
                        options = AppPreferences(this).options()
                        handler.removeCallbacks(tick)
                        tick.run()
                    } else stopSelf()
                }
                else -> if (!isRunning) stopSelf()
            }
        } catch (error: Exception) { fail(error) }
        return START_NOT_STICKY
    }

    private fun startSimulation(intent: Intent) {
        if (!MockPermissions.hasLocation(this) || !MockPermissions.isMockApp(this)) {
            throw SecurityException(getString(R.string.permission_or_mock_error))
        }
        check(MockPermissions.locationEnabled(this)) { getString(R.string.enable_location) }
        options = AppPreferences(this).options()
        val route = if (intent.action == ACTION_START_ROUTE) AppPreferences(this).route() else null
        val newNavigator = route?.let { RouteNavigator(it.points, it.speedKmh, it.loop) }
        val target = newNavigator?.position() ?: Coordinates.from(
            intent.getDoubleExtra(EXTRA_LATITUDE, Double.NaN), intent.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN)
        ) ?: throw IllegalArgumentException(getString(R.string.invalid_coordinates))
        handler.removeCallbacks(tick)
        navigator = newNavigator
        coordinates = target
        paused = false
        lastTick = SystemClock.elapsedRealtime()
        showForeground()
        store.cleanupNeeded = true
        // Publish before announcing success, including validation of provider access.
        publishPosition(advance = false)
        isRunning = true
        store.writeStatus(statusMessage())
        val name = if (route != null) getString(R.string.route_history_name) else store.selectionName
        PlaceLibrary(this).record(name, target)
        broadcastStatus()
        handler.postDelayed(tick, options.intervalMillis)
    }

    private fun publishPosition(advance: Boolean) {
        val now = SystemClock.elapsedRealtime()
        val route = navigator
        val wasCompleted = route?.completed == true
        if (advance && !paused && route != null) {
            coordinates = route.advance(((now - lastTick).coerceAtLeast(0)) / 1000.0)
        }
        lastTick = now
        val point = coordinates ?: return
        val moving = route != null && !paused && !route.completed
        engine.publish(point, options, if (moving) (route!!.speedKmh / 3.6).toFloat() else 0f,
            if (moving) route!!.bearing() else 0f)
        wakeLock.acquire((options.intervalMillis * 3).coerceAtLeast(10000))
        snapshot = SimulationSnapshot(point, route?.points.orEmpty(), paused, route?.completed == true,
            route?.fraction ?: 0.0, if (moving) route!!.speedKmh else 0.0)
        if (route?.completed == true && !wasCompleted) store.writeStatus(statusMessage())
        if (now - lastNotification >= 5000 || route?.completed == true && !wasCompleted) showForeground()
        broadcastStatus()
    }

    private fun statusMessage(): String {
        val route = navigator
        return when {
            route?.completed == true -> getString(R.string.route_completed)
            paused -> getString(R.string.route_paused)
            route != null -> getString(R.string.route_running)
            else -> coordinates?.let { getString(R.string.location_active, it.latitude, it.longitude) }
                ?: getString(R.string.initial_status)
        }
    }

    private fun showForeground() {
        val notification = notification()
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        else startForeground(NOTIFICATION_ID, notification)
        lastNotification = SystemClock.elapsedRealtime()
    }

    private fun fail(error: Exception) {
        val message = if (error is SecurityException) getString(R.string.permission_or_mock_error)
        else error.message ?: getString(R.string.generic_error)
        finishSimulation(message, true)
    }

    private fun finishSimulation(message: String, error: Boolean) {
        handler.removeCallbacks(tick)
        coordinates = null
        navigator = null
        snapshot = null
        isRunning = false
        if (wakeLock.isHeld) wakeLock.release()
        val cleanupError = runCatching {
            if (engine.installed || store.cleanupNeeded) engine.stop()
            store.cleanupNeeded = false
        }.exceptionOrNull()
        if (cleanupError == null) store.writeStatus(message, error)
        else {
            store.cleanupNeeded = true
            store.writeStatus(getString(R.string.cleanup_failed), true)
        }
        shuttingDown = true
        stopForeground(STOP_FOREGROUND_REMOVE)
        broadcastStatus()
        stopSelf()
    }

    private fun notification(): Notification {
        fun pending(action: String, id: Int) = PendingIntent.getService(this, id,
            Intent(this, MockLocationService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val open = PendingIntent.getActivity(this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_location)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(statusMessage())
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setCategory(Notification.CATEGORY_SERVICE)
        if (navigator?.completed == false) builder.addAction(Notification.Action.Builder(null,
            getString(if (paused) R.string.resume else R.string.pause), pending(ACTION_TOGGLE_PAUSE, 3)).build())
        builder.addAction(Notification.Action.Builder(null, getString(R.string.stop), pending(ACTION_STOP, 2)).build())
        return builder.build()
    }

    private fun broadcastStatus() { sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName)) }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        if (wakeLock.isHeld) wakeLock.release()
        isRunning = false
        snapshot = null
        if (!shuttingDown && store.cleanupNeeded) {
            val restored = runCatching { engine.stop() }.isSuccess
            store.cleanupNeeded = !restored
            store.writeStatus(getString(if (restored) R.string.simulation_interrupted else R.string.cleanup_failed), true)
            broadcastStatus()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_START = "com.diegoca.gpsmocker.START"
        const val ACTION_START_ROUTE = "com.diegoca.gpsmocker.START_ROUTE"
        const val ACTION_STOP = "com.diegoca.gpsmocker.STOP"
        const val ACTION_TOGGLE_PAUSE = "com.diegoca.gpsmocker.TOGGLE_PAUSE"
        const val ACTION_OPTIONS = "com.diegoca.gpsmocker.OPTIONS"
        const val ACTION_STATUS = "com.diegoca.gpsmocker.STATUS"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        private const val CHANNEL_ID = "gps_simulation"
        private const val NOTIFICATION_ID = 1
        @Volatile var isRunning = false
            private set
        @Volatile var snapshot: SimulationSnapshot? = null
            private set
    }
}
