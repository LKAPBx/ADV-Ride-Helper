package dev.africatwin.telemetry

import android.Manifest
import android.content.pm.PackageManager
import android.os.*
import androidx.car.app.*
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.*
import androidx.car.app.hardware.info.*
import androidx.car.app.model.*
import androidx.lifecycle.*
import java.util.Date
import android.location.Location
import java.util.Locale

internal class HostSensorTestScreen(context: CarContext) : Screen(context) {
    private val handler = Handler(Looper.getMainLooper())
    private var sensors: CarSensors? = null
    private var locationRegistered = false
    private var compassRegistered = false
    private var running = false
    private var start = 0L
    private var locationCount = 0
    private var compassCount = 0
    private var locationText = "No test run"
    private var compassText = "No test run"
    private var state = "Ready: run a 30-second test"
    private val hostFixes = mutableListOf<ComparisonFix>()
    private val phoneFixes = mutableListOf<ComparisonFix>()
    private val sampleLog = mutableListOf<String>()
    private var phoneCount = 0
    private var phoneText = "Start GPS tracking before comparison"
    private fun record(source: String, location: Location, fixes: MutableList<ComparisonFix>) {
        if (fixes.size >= 60) return
        fixes.add(ComparisonFix(location.latitude, location.longitude, location.time))
        if (sampleLog.size < 120) sampleLog.add("$source: lat=${location.latitude}, lon=${location.longitude}, fixTime=${location.time}, elapsedNanos=${location.elapsedRealtimeNanos}, receivedNanos=${SystemClock.elapsedRealtimeNanos()}, accuracyMeters=${if (location.hasAccuracy()) location.accuracy else "unknown"}, provider=${location.provider}, mock=${location.isFromMockProvider}")
    }
    private val phoneListener: (Location) -> Unit = { location ->
        if (running) {
            phoneCount++; record("PHONE GPS_PROVIDER", location, phoneFixes)
            phoneText = "Provider ${location.provider}; accuracy ${AppUnits.get(carContext).shortDistance(location.accuracy.toDouble())}"
        }
    }
    private val locationListener = OnCarDataAvailableListener<CarHardwareLocation> { data ->
        if (running) {
            locationCount++
            val value = data.location
            val location = value.value
            if (value.status == CarValue.STATUS_SUCCESS && location != null) record("ANDROID AUTO HOST", location, hostFixes)
            locationText = "${status(value)}\n" + if (value.status == CarValue.STATUS_SUCCESS && location != null) {
                "Lat/lon: ${location.latitude}, ${location.longitude}\nProvider: ${location.provider}; accuracy: ${if (location.hasAccuracy()) AppUnits.get(carContext).shortDistance(location.accuracy.toDouble()) else "not supplied"}\n" +
                    "Location time: ${location.time}; elapsed age: ${(SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) / 1_000_000} ms\n" +
                    "Bearing: ${if (location.hasBearing()) location.bearing else "not supplied"}; speed: ${if (location.hasSpeed()) AppUnits.get(carContext).speed(location.speed * 2.2369362921) else "not supplied"}\nMock flag: ${location.isFromMockProvider}"
            } else "No usable location value"
        }
    }
    private val compassListener = OnCarDataAvailableListener<Compass> { data ->
        if (running) { compassCount++; compassText = "${status(data.orientations)}\nOrientations: ${if (data.orientations.status == CarValue.STATUS_SUCCESS) data.orientations.value else "not usable"}" }
    }
    private fun status(value: CarValue<*>): String = "Status: " + when (value.status) {
        CarValue.STATUS_SUCCESS -> "SUCCESS"
        CarValue.STATUS_UNKNOWN -> "UNKNOWN"
        CarValue.STATUS_UNAVAILABLE -> "UNAVAILABLE"
        CarValue.STATUS_UNIMPLEMENTED -> "UNIMPLEMENTED"
        else -> value.status.toString()
    } + "; CarValue timestamp: ${value.timestampMillis} ms"
    private val pulse = object : Runnable {
        override fun run() {
            if (!running) return
            if (SystemClock.elapsedRealtime() - start >= 30_000) { stop("Test completed"); return }
            save(); invalidate(); handler.postDelayed(this, 2000)
        }
    }
    init { lifecycle.addObserver(object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) { if (running) stop("Test stopped when screen closed") }
        override fun onDestroy(owner: LifecycleOwner) { cleanup() }
    }) }
    private fun begin() {
        cleanup(); start = SystemClock.elapsedRealtime(); locationCount = 0; compassCount = 0
        hostFixes.clear(); phoneFixes.clear(); sampleLog.clear(); phoneCount = 0
        phoneText = if (RideStore(carContext).running) "Waiting for phone GPS_PROVIDER fixes" else "Phone GPS missing: start tracking on phone, then rerun"
        locationText = "Waiting for location callback"; compassText = "Waiting for compass callback"
        if (carContext.carAppApiLevel < 3) { state = "Car App API 3 required"; save(); invalidate(); return }
        running = true; state = "Testing Android Auto host sensors"
        PhoneGpsFeed.listeners.add(phoneListener)
        try {
            sensors = carContext.getCarService(CarHardwareManager::class.java).carSensors
            if (carContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                try { locationRegistered = true; sensors!!.addCarHardwareLocationListener(CarSensors.UPDATE_RATE_NORMAL, carContext.mainExecutor, locationListener) }
                catch (e: RuntimeException) { locationText = "Location subscription failed: ${e.javaClass.simpleName}" }
            } else locationText = "Precise location permission required; grant on phone"
            try { compassRegistered = true; sensors!!.addCompassListener(CarSensors.UPDATE_RATE_NORMAL, carContext.mainExecutor, compassListener) }
            catch (e: RuntimeException) { compassText = "Compass subscription failed: ${e.javaClass.simpleName}" }
            handler.post(pulse)
        } catch (e: RuntimeException) { stop("Sensor service unavailable: ${e.javaClass.simpleName}") }
    }
    private fun cleanup() {
        running = false; handler.removeCallbacks(pulse)
        PhoneGpsFeed.listeners.remove(phoneListener)
        if (locationRegistered) try { sensors?.removeCarHardwareLocationListener(locationListener) } catch (_: RuntimeException) { }
        if (compassRegistered) try { sensors?.removeCompassListener(compassListener) } catch (_: RuntimeException) { }
        locationRegistered = false; compassRegistered = false; sensors = null
    }
    private fun stop(message: String) { cleanup(); state = message; save(); invalidate() }
    private fun save() {
        val pairs = GpsComparison.pair(hostFixes, phoneFixes)
        val units = AppUnits.get(carContext)
        val summary = if (pairs.isEmpty()) "No time-matched pairs. Phone tracking must be running; both sources need fresh fixes." else
            "Pairs within 2 seconds: ${pairs.size}\nSeparation min/mean/max: ${units.shortDistance(pairs.minOf { it.separationMeters })} / ${units.shortDistance(pairs.map { it.separationMeters }.average())} / ${units.shortDistance(pairs.maxOf { it.separationMeters })}\nExact position+time matches: ${pairs.count { it.identical }}\nMax absolute fix-time difference: ${pairs.maxOf { kotlin.math.abs(it.timeDifferenceMillis) }} ms"
        val report = "Android Auto / phone GPS comparison • ${Date()}\nApp ${BuildConfig.VERSION_NAME}; host API ${carContext.carAppApiLevel}\n$state\nSource unverified: matching or differing streams do not establish Honda GPS origin. DHU can simulate values.\n\nLocation callbacks: $locationCount\n$locationText\n\nPhone callbacks: $phoneCount\n$phoneText\n\n$summary\nEach host sample pairs with nearest phone fix time, which can be reused.\n\nCompass callbacks: $compassCount\n$compassText\n\nRecorded samples (up to 60 per source):\n${sampleLog.joinToString("\n")}" 
        carContext.getSharedPreferences("host_sensor_test", 0).edit().putString("report", report).apply()
    }
    override fun onGetTemplate(): Template {
        val list = ItemList.Builder()
        list.addItem(Row.Builder().setTitle(if (running) "Stop comparison" else "Start GPS comparison").addText(state)
            .setOnClickListener { if (running) stop("Test stopped manually") else begin() }.build())
        list.addItem(Row.Builder().setTitle("Host location: $locationCount callbacks").addText(locationText.lineSequence().first()).build())
        list.addItem(Row.Builder().setTitle("Host compass: $compassCount callbacks").addText(compassText.lineSequence().first()).build())
        list.addItem(Row.Builder().setTitle("Phone GPS: $phoneCount callbacks").addText(phoneText).build())
        list.addItem(Row.Builder().setTitle("Saved report on phone").addText("Settings → Advanced → Saved GPS / compass test")
            .addText("Reported by Android Auto; physical source unverified").build())
        return ListTemplate.Builder().setTitle("Host GPS / compass test").setHeaderAction(Action.BACK).setSingleList(list.build()).build()
    }
}
