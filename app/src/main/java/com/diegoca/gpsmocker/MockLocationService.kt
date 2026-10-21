package com.diegoca.gpsmocker

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager

class MockLocationService : Service() {
    private lateinit var engine: MockLocationEngine
    private lateinit var store: SimulationStore
    private val handler = Handler(Looper.getMainLooper())
    private var coordinates: Coordinates? = null
    private var shuttingDown = false
    private val wakeLock by lazy {
        getSystemService(PowerManager::class.java).newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "$packageName:simulation"
        ).apply { setReferenceCounted(false) }
    }

    private val tick = object : Runnable {
        override fun run() {
            val target = coordinates ?: return
            try {
                engine.publish(target)
                wakeLock.acquire(UPDATE_INTERVAL_MS * 10)
                handler.postDelayed(this, UPDATE_INTERVAL_MS)
            } catch (error: Exception) {
                fail(error)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = SimulationStore(this)
        engine = MockLocationEngine(AndroidMockLocationBackend(getSystemService(LocationManager::class.java)))
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> finishSimulation(getString(R.string.location_stopped), false)
            ACTION_START -> {
                val target = Coordinates.from(
                    intent.getDoubleExtra(EXTRA_LATITUDE, Double.NaN),
                    intent.getDoubleExtra(EXTRA_LONGITUDE, Double.NaN)
                )
                if (target == null) {
                    finishSimulation(getString(R.string.invalid_coordinates), true)
                    return START_NOT_STICKY
                }
                try {
                    if (!MockPermissions.hasLocation(this) || !MockPermissions.isMockApp(this)) {
                        throw SecurityException(getString(R.string.permission_or_mock_error))
                    }
                    check(MockPermissions.locationEnabled(this)) { getString(R.string.enable_location) }
                    val notification = notification(target)
                    if (Build.VERSION.SDK_INT >= 29) {
                        startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                    } else {
                        startForeground(NOTIFICATION_ID, notification)
                    }
                    handler.removeCallbacks(tick)
                    // Persist ownership before touching GPS, including partial setup failures.
                    store.cleanupNeeded = true
                    engine.publish(target)
                    wakeLock.acquire(UPDATE_INTERVAL_MS * 10)
                    coordinates = target
                    isRunning = true
                    store.saveSelection(target)
                    store.writeStatus(getString(R.string.location_active, target.latitude, target.longitude))
                    broadcastStatus()
                    handler.postDelayed(tick, UPDATE_INTERVAL_MS)
                } catch (error: Exception) {
                    fail(error)
                }
            }
            else -> if (!isRunning) stopSelf()
        }
        // Never restart a simulation silently after Android kills the process.
        return START_NOT_STICKY
    }

    private fun fail(error: Exception) {
        val message = if (error is SecurityException) getString(R.string.permission_or_mock_error)
        else error.message ?: getString(R.string.generic_error)
        finishSimulation(message, true)
    }

    private fun finishSimulation(message: String, error: Boolean) {
        handler.removeCallbacks(tick)
        coordinates = null
        isRunning = false
        if (wakeLock.isHeld) wakeLock.release()
        val cleanupError = runCatching {
            // Run even in a newly created service to recover a previous process's provider.
            if (engine.installed || store.cleanupNeeded) engine.stop()
            store.cleanupNeeded = false
        }.exceptionOrNull()
        if (cleanupError == null) {
            store.writeStatus(message, error)
        } else {
            store.cleanupNeeded = true
            store.writeStatus(getString(R.string.cleanup_failed), true)
        }
        shuttingDown = true
        stopForeground(STOP_FOREGROUND_REMOVE)
        broadcastStatus()
        stopSelf()
    }

    private fun notification(target: Coordinates): Notification {
        val open = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, MockLocationService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_location)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.location_active, target.latitude, target.longitude))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(Notification.Action.Builder(null, getString(R.string.stop), stop).build())
            .build()
    }

    private fun broadcastStatus() {
        sendBroadcast(Intent(ACTION_STATUS).setPackage(packageName))
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        if (wakeLock.isHeld) wakeLock.release()
        isRunning = false
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
        const val ACTION_STOP = "com.diegoca.gpsmocker.STOP"
        const val ACTION_STATUS = "com.diegoca.gpsmocker.STATUS"
        const val EXTRA_LATITUDE = "latitude"
        const val EXTRA_LONGITUDE = "longitude"
        private const val CHANNEL_ID = "gps_simulation"
        private const val NOTIFICATION_ID = 1
        private const val UPDATE_INTERVAL_MS = 1000L

        @Volatile var isRunning: Boolean = false
            private set
    }
}
