package dev.africatwin.telemetry

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.*
import android.os.*
import android.hardware.*

class RideTrackingService : Service(), LocationListener, SensorEventListener {
    private lateinit var sensors: SensorManager
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { if (accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) LiveHeading.magnetic = null }
    override fun onSensorChanged(event: SensorEvent) {
        if (!active || event.accuracy < SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM) return
        val rotation = FloatArray(9); val angles = FloatArray(3)
        SensorManager.getRotationMatrixFromVector(rotation, event.values)
        SensorManager.getOrientation(rotation, angles)
        val degrees = (Math.toDegrees(angles[0].toDouble()) + 360) % 360
        if (degrees.isFinite()) LiveHeading.magnetic = Heading(degrees, "Phone magnetic compass", event.timestamp / 1_000_000)
    }
    private lateinit var locations: LocationManager
    private lateinit var store: RideStore
    private val distance = RideDistance()
    private val handler = Handler(Looper.getMainLooper())
    private var active = false
    private var gpsEnabled = true
    private var selectedSource: String? = null
    private var lastFuelNotice = SystemClock.elapsedRealtime()
    private val hostListener: (Location) -> Unit = { location -> if (active) consume(location, "Android Auto GPS") }
    private val tick: Runnable = object : Runnable {
        override fun run() {
            if (!active) return
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) { finish("Location permission lost"); return }
            store.heartbeat()
            TpmsMonitor.start(this@RideTrackingService, "ride")
            FuelSummaryNotice.checkLowRange(this@RideTrackingService)
            val now = SystemClock.elapsedRealtime()
            if (FuelSummaryNotice.enabled(this@RideTrackingService) &&
                now - lastFuelNotice >= FuelSummaryNotice.interval(this@RideTrackingService) * 60_000L) {
                lastFuelNotice = now
                FuelSummaryNotice.show(this@RideTrackingService)
            }
            if (store.lastGpsSignal != 0L && System.currentTimeMillis() - store.lastGpsSignal > 30_000) store.markGap()
            getSystemService(NotificationManager::class.java).notify(42, notification())
            handler.postDelayed(this, 10_000)
        }
    }
    override fun onCreate() {
        super.onCreate()
        store = RideStore(this); locations = getSystemService(LocationManager::class.java)
        sensors = getSystemService(SensorManager::class.java)
        HostLocationFeed.listeners.add(hostListener)
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("ride", "Ride tracking", NotificationManager.IMPORTANCE_LOW))
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { finish("Tracking stopped"); return START_NOT_STICKY }
        if (active) return START_NOT_STICKY
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            TrackingStartReport.record(this, "GPS service start rejected: precise location permission missing")
            finish("Precise location required"); return START_NOT_STICKY
        }
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(42, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or
                if (TpmsMonitor.allowed(this)) ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0)
            else startForeground(42, notification())
            check(locations.isProviderEnabled(LocationManager.GPS_PROVIDER))
            gpsEnabled = true; distance.resetAnchor(); selectedSource = null
            store.start(); active = true
            TpmsMonitor.start(this, "ride")
            lastFuelNotice = SystemClock.elapsedRealtime()
            locations.requestLocationUpdates(LocationManager.GPS_PROVIDER, 2000L, 0f, this, Looper.getMainLooper())
            handler.post(tick)
            LiveHeading.clear()
            sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
            TrackingNotice.changed(this, true)
            TrackingStartReport.record(this, "GPS tracking service started successfully; waiting for reliable fixes")
        } catch (e: RuntimeException) {
            TrackingStartReport.record(this, "GPS service failed during startup", e)
            finish("GPS start failed (${e.javaClass.simpleName}); see Advanced → GPS start report")
        }
        return START_NOT_STICKY
    }
    private fun notification(): Notification {
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this, 1, Intent(this, RideTrackingService::class.java).setAction(STOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return Notification.Builder(this, "ride").setSmallIcon(R.drawable.ic_app).setContentTitle("Ride GPS tracking")
            .setContentText(store.distanceText()).setContentIntent(open).setOngoing(true)
            .addAction(Notification.Action.Builder(null, "Stop tracking", stop).build()).build()
    }
    override fun onLocationChanged(location: Location) {
        if (!active || !location.hasAccuracy()) return
        LivePosition.offer(location)
        PhoneGpsFeed.publish(location)
        val host = HostLocationFeed.fresh()
        if (host != null) consume(host, "Android Auto GPS") else consume(location, "Phone GPS")
    }
    private fun consume(location: Location, source: String) {
        if (!active || !gpsEnabled || !location.hasAccuracy() || location.accuracy !in 0f..25f ||
            !NearestStations.validCoordinates(location.latitude, location.longitude) ||
            SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos !in 0L..15_000_000_000L) return
        if (selectedSource != source) {
            if (selectedSource != null) store.markGap()
            selectedSource = source
        }
        store.validGpsSignal()
        LivePosition.offer(location)
        LocationFallback.remember(this, location)
        val fresh = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos in 0L..15_000_000_000L
        LiveSpeed.reading = if (fresh && location.hasSpeed() && RideSafety.speedMph(location.speed.toDouble()) != null &&
            (!location.hasSpeedAccuracy() || (location.speedAccuracyMetersPerSecond.isFinite() && location.speedAccuracyMetersPerSecond in 0f..3f)))
            SpeedReading(location.speed.toDouble(), source, location.elapsedRealtimeNanos / 1_000_000) else null
        if (fresh && location.accuracy <= 25 && location.hasSpeed() && location.speed >= 2f && location.hasBearing() &&
            (!location.hasBearingAccuracy() || location.bearingAccuracyDegrees <= 25f)) {
            LiveHeading.travel = Heading(location.bearing.toDouble(), "$source travel direction (true north)", location.elapsedRealtimeNanos / 1_000_000)
        } else LiveHeading.travel = null
        store.step(distance.add(RideFix(location.latitude, location.longitude, location.accuracy.toDouble(), location.elapsedRealtimeNanos / 1_000_000, source), SystemClock.elapsedRealtime()))
        store.status("GPS tracking active • $source")
    }
    override fun onProviderDisabled(provider: String) {
        if (active && provider == LocationManager.GPS_PROVIDER) {
            gpsEnabled = false; distance.resetAnchor(); store.markGap(); store.status("GPS disabled; distance may be missing")
        }
    }
    override fun onProviderEnabled(provider: String) {
        if (provider == LocationManager.GPS_PROVIDER) { gpsEnabled = true; distance.resetAnchor() }
    }
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
    private fun finish(message: String) {
        BikeConnectionService.current?.suppressForConnection()
        val wasActive = active
        active = false; handler.removeCallbacks(tick)
        FuelSummaryNotice.cancel(this)
        sensors.unregisterListener(this); LiveHeading.clear()
        locations.removeUpdates(this); store.stop(message); stopForeground(STOP_FOREGROUND_REMOVE); stopSelf()
        if (wasActive) TrackingNotice.changed(this, false)
    }
    override fun onDestroy() {
        TpmsMonitor.stop("ride")
        HostLocationFeed.listeners.remove(hostListener)
        FuelSummaryNotice.cancel(this)
        val wasActive = active
        active = false; handler.removeCallbacks(tick); locations.removeUpdates(this)
        sensors.unregisterListener(this); LiveHeading.clear()
        if (store.requestedRunning) { store.markGap(); store.stop("Tracking interrupted; restart from phone") }
        if (wasActive) TrackingNotice.changed(this, false)
        super.onDestroy()
    }
    companion object { const val STOP = "dev.africatwin.telemetry.STOP_RIDE" }
}
