package com.vislupus.stepanalyzer.recording

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.vislupus.stepanalyzer.MainActivity
import com.vislupus.stepanalyzer.R
import com.vislupus.stepanalyzer.StepAnalyzerApp
import com.vislupus.stepanalyzer.config.AppConfig
import com.vislupus.stepanalyzer.core.gpx.Gpx
import com.vislupus.stepanalyzer.data.db.GpsPoint
import com.vislupus.stepanalyzer.data.db.Session
import com.vislupus.stepanalyzer.data.files.CsvSampleWriter
import com.vislupus.stepanalyzer.data.files.GpxWriter
import com.vislupus.stepanalyzer.util.Formatters
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Foreground service that records the motion sensors (and optionally GPS) into the session
 * folder. It owns a dedicated handler thread; sensor callbacks, file writes and DB updates
 * all happen there, and a periodic tick publishes a [RecordingState] to [RecordingBus].
 */
class RecordingService : Service() {

    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager
    private var wakeLock: PowerManager.WakeLock? = null

    private val app get() = application as StepAnalyzerApp
    private val container get() = app.container

    // ---- per-recording state (handler thread only) ----
    private var sessionId = 0L
    private var startedAtMs = 0L
    private var writer: CsvSampleWriter? = null
    private val counts = HashMap<String, Int>()
    private val availableSensors = HashSet<String>()
    private var bootEpochOffsetMs = 0L
    private val liveDetector = LiveStepDetector()
    private val liveBuffer = FloatArray(AppConfig.Recording.LIVE_CHART_SECONDS * 100)
    private var liveBufferPos = 0
    private var liveBufferFilled = 0
    private var liveLastPushNs = 0L
    private var accelCountWindow = 0
    private var accelWindowStartMs = 0L
    private var accelRateHz = 0.0
    private var hwCounterBase = -1f
    private var hwCounter = -1
    private var hwDetector = -1
    private val gpsPoints = ArrayList<GpsPoint>()
    private var gpsDistanceM = 0.0
    private var gpsAccuracy = Float.NaN
    private var gpsEnabled = false
    private var gpsHasFix = false
    private var sensorPeriodUs = AppConfig.Recording.SENSOR_PERIOD_US
    private var recording = false

    private val sensorNames = HashMap<Int, String>()

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) = onSensor(event)
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) = onLocation(location)
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!recording) return
            publish()
            handler.postDelayed(this, AppConfig.Recording.UI_UPDATE_MS)
        }
    }

    private var lastNotificationUpdateMs = 0L

    override fun onCreate() {
        super.onCreate()
        thread = HandlerThread("recording", android.os.Process.THREAD_PRIORITY_MORE_FAVORABLE).also { it.start() }
        handler = Handler(thread.looper)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        for ((type, name) in AppConfig.Recording.SENSORS) sensorNames[type] = name
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                if (!recording) {
                    startForegroundWithType()
                    handler.post { startRecording() }
                }
            }
            ACTION_STOP -> handler.post { stopRecording() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (recording) stopRecording()
        thread.quitSafely()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ start / stop

    private fun startForegroundWithType() {
        val hasLocation = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (hasLocation && container.settings.current.recordGps) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            }
        } else 0
        ServiceCompat.startForeground(this, AppConfig.Recording.NOTIFICATION_ID, buildNotification(0, 0), type)
    }

    private fun startRecording() {
        val settings = container.settings.current
        sensorPeriodUs = settings.sensorPeriodUs
        startedAtMs = System.currentTimeMillis()
        bootEpochOffsetMs = System.currentTimeMillis() - SystemClock.elapsedRealtimeNanos() / 1_000_000
        counts.clear()
        availableSensors.clear()
        gpsPoints.clear()
        gpsDistanceM = 0.0
        gpsAccuracy = Float.NaN
        gpsHasFix = false
        hwCounterBase = -1f
        hwCounter = -1
        hwDetector = -1
        liveDetector.reset()
        liveBufferPos = 0
        liveBufferFilled = 0
        accelCountWindow = 0
        accelWindowStartMs = startedAtMs
        accelRateHz = 0.0

        val name = DateTimeFormatter.ofPattern(AppConfig.Storage.SESSION_NAME_PATTERN)
            .format(Instant.ofEpochMilli(startedAtMs).atZone(ZoneId.systemDefault()))
        sessionId = container.sessions.insert(
            Session(
                name = name,
                source = Session.SOURCE_RECORDED,
                createdAt = startedAtMs,
                startedAt = startedAtMs,
                sensorPeriodUs = sensorPeriodUs,
                notes = "${Build.MANUFACTURER} ${Build.MODEL} / Android ${Build.VERSION.RELEASE}",
            )
        )
        val csvFile = container.sessions.files.csvFile(sessionId)
        writer = CsvSampleWriter(csvFile)

        // motion sensors
        for ((type, csvName) in AppConfig.Recording.SENSORS) {
            val sensor = sensorManager.getDefaultSensor(type) ?: continue
            availableSensors.add(csvName)
            sensorManager.registerListener(sensorListener, sensor, sensorPeriodUs, handler)
        }
        // hardware step sensors (reference only)
        if (settings.useHardwareStepSensors && (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || hasPermission(Manifest.permission.ACTIVITY_RECOGNITION))) {
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.let {
                hwCounter = 0
                sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_NORMAL, handler)
            }
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)?.let {
                hwDetector = 0
                sensorManager.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_FASTEST, handler)
            }
        }
        // GPS
        gpsEnabled = false
        if (AppConfig.Features.ENABLE_GPS && settings.recordGps && hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    settings.gpsIntervalMs,
                    AppConfig.Recording.GPS_MIN_DISTANCE_M,
                    locationListener,
                    thread.looper,
                )
                gpsEnabled = true
            } catch (e: SecurityException) {
                gpsEnabled = false
            } catch (e: IllegalArgumentException) {
                gpsEnabled = false // no GPS provider on this device
            }
        }
        // keep the CPU awake while the screen is off
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "stepanalyzer:recording").also { it.acquire(12 * 60 * 60 * 1000L) }

        recording = true
        RecordingBus.publish(
            RecordingState(
                status = RecordingStatus.RECORDING,
                sessionId = sessionId,
                startedAtMs = startedAtMs,
                gpsEnabled = gpsEnabled,
                hwStepCounter = hwCounter,
                hwStepDetector = hwDetector,
                availableSensors = availableSensors.toSet(),
            )
        )
        handler.post(tick)
    }

    private fun stopRecording() {
        if (!recording) {
            stopSelfSafely()
            return
        }
        recording = false
        RecordingBus.update { it.copy(status = RecordingStatus.STOPPING) }
        handler.removeCallbacks(tick)
        sensorManager.unregisterListener(sensorListener)
        try { locationManager.removeUpdates(locationListener) } catch (_: SecurityException) { }
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null

        val endedAt = System.currentTimeMillis()
        var error: String? = null
        try {
            writer?.close()
        } catch (e: Exception) {
            error = e.message
        }
        writer = null

        val session = container.sessions.get(sessionId)
        if (session != null) {
            var gpxPath: String? = null
            if (gpsPoints.size >= 2) {
                val gpxFile = container.sessions.files.gpxFile(sessionId)
                try {
                    GpxWriter.write(
                        gpxFile, session.name, gpsPoints,
                        mapOf(
                            "distance_m" to Formatters.number(gpsDistanceM, 1),
                            "duration_sec" to ((endedAt - startedAtMs) / 1000).toString(),
                            "live_steps" to liveDetector.steps.toString(),
                        ),
                    )
                    gpxPath = gpxFile.absolutePath
                } catch (e: Exception) {
                    error = e.message
                }
                container.sessions.saveGpsPoints(gpsPoints)
            }
            val csvFile = container.sessions.files.csvFile(sessionId)
            container.sessions.update(
                session.copy(
                    endedAt = endedAt,
                    durationMs = endedAt - startedAtMs,
                    csvPath = csvFile.absolutePath,
                    gpxPath = gpxPath,
                    accelCount = counts["accel"] ?: 0,
                    uaccelCount = counts["uaccel"] ?: 0,
                    gyroCount = counts["gyro"] ?: 0,
                    magCount = counts["mag"] ?: 0,
                    gpsCount = gpsPoints.size,
                    gpsDistanceM = gpsDistanceM,
                    hwStepCounter = hwCounter,
                    hwStepDetector = hwDetector,
                    liveSteps = liveDetector.steps,
                    fileBytes = csvFile.length(),
                )
            )
        }
        RecordingBus.publish(RecordingState(status = RecordingStatus.IDLE, lastSavedSessionId = sessionId, error = error))
        stopSelfSafely()
    }

    private fun stopSelfSafely() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ------------------------------------------------------------------ callbacks

    private fun onSensor(event: SensorEvent) {
        if (!recording) return
        when (event.sensor.type) {
            Sensor.TYPE_STEP_COUNTER -> {
                if (hwCounterBase < 0) hwCounterBase = event.values[0]
                hwCounter = (event.values[0] - hwCounterBase).toInt()
                return
            }
            Sensor.TYPE_STEP_DETECTOR -> {
                hwDetector++
                return
            }
        }
        val name = sensorNames[event.sensor.type] ?: return
        val epochMs = bootEpochOffsetMs + event.timestamp / 1_000_000
        val v = event.values
        writer?.append(epochMs, name, v[0], v[1], v[2])
        counts[name] = (counts[name] ?: 0) + 1

        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            liveDetector.onSample(event.timestamp, v[0], v[1], v[2])
            // live chart is decimated to ~100 Hz
            if (event.timestamp - liveLastPushNs >= 9_500_000L) {
                liveLastPushNs = event.timestamp
                liveBuffer[liveBufferPos] = liveDetector.lastFiltered
                liveBufferPos = (liveBufferPos + 1) % liveBuffer.size
                if (liveBufferFilled < liveBuffer.size) liveBufferFilled++
            }
            accelCountWindow++
            val now = System.currentTimeMillis()
            if (now - accelWindowStartMs >= 1000) {
                accelRateHz = accelCountWindow * 1000.0 / (now - accelWindowStartMs)
                accelCountWindow = 0
                accelWindowStartMs = now
            }
        }
    }

    private fun onLocation(location: Location) {
        if (!recording) return
        gpsHasFix = true
        gpsAccuracy = if (location.hasAccuracy()) location.accuracy else Float.NaN
        val point = GpsPoint(
            sessionId = sessionId,
            timeMs = location.time,
            lat = location.latitude,
            lon = location.longitude,
            ele = if (location.hasAltitude()) location.altitude else Double.NaN,
            accuracyM = gpsAccuracy,
            speedMps = if (location.hasSpeed()) location.speed else 0f,
        )
        gpsPoints.lastOrNull()?.let { prev ->
            gpsDistanceM += Gpx.haversine(prev.lat, prev.lon, point.lat, point.lon)
        }
        gpsPoints.add(point)
    }

    private fun publish() {
        val now = System.currentTimeMillis()
        val live = FloatArray(liveBufferFilled)
        if (liveBufferFilled > 0) {
            val start = if (liveBufferFilled < liveBuffer.size) 0 else liveBufferPos
            for (i in 0 until liveBufferFilled) live[i] = liveBuffer[(start + i) % liveBuffer.size]
        }
        RecordingBus.publish(
            RecordingState(
                status = RecordingStatus.RECORDING,
                sessionId = sessionId,
                startedAtMs = startedAtMs,
                elapsedMs = now - startedAtMs,
                counts = HashMap(counts),
                accelRateHz = accelRateHz,
                gpsEnabled = gpsEnabled,
                gpsPoints = gpsPoints.size,
                gpsDistanceM = gpsDistanceM,
                gpsAccuracyM = gpsAccuracy,
                gpsHasFix = gpsHasFix,
                liveSteps = liveDetector.steps,
                hwStepCounter = hwCounter,
                hwStepDetector = hwDetector,
                liveSignal = live,
                availableSensors = availableSensors.toSet(),
            )
        )
        if (now - lastNotificationUpdateMs >= 1000) {
            lastNotificationUpdateMs = now
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.notify(AppConfig.Recording.NOTIFICATION_ID, buildNotification(now - startedAtMs, liveDetector.steps))
        }
    }

    // ------------------------------------------------------------------ notification

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                AppConfig.Recording.NOTIFICATION_CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = getString(R.string.notif_channel_desc) }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(elapsedMs: Long, steps: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, RecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, AppConfig.Recording.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_footprints)
            .setContentTitle(getString(R.string.notif_recording_title))
            .setContentText(getString(R.string.notif_recording_text, Formatters.duration(elapsedMs), steps))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(open)
            .addAction(R.drawable.ic_square, getString(R.string.action_stop), stop)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    companion object {
        const val ACTION_START = "com.vislupus.stepanalyzer.action.START"
        const val ACTION_STOP = "com.vislupus.stepanalyzer.action.STOP"

        fun start(context: Context) {
            val intent = Intent(context, RecordingService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, RecordingService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }
    }
}
