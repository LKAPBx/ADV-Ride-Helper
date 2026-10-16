package dev.africatwin.telemetry

import android.location.Location
import android.os.SystemClock
import androidx.car.app.CarContext
import androidx.car.app.hardware.CarHardwareManager
import androidx.car.app.hardware.common.*
import androidx.car.app.hardware.info.*
import androidx.lifecycle.*

internal object HostLocationFeed {
    private var last: Location? = null
    val listeners = mutableSetOf<(Location) -> Unit>()
    fun fresh(): Location? = last?.takeIf { SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos in 0L..5_000_000_000L }
    fun clear() { last = null }
    fun publish(location: Location) {
        // Display the host's usable fix even if no ride-distance service is running.
        LivePosition.offer(location)
        if (!location.hasAccuracy() || !location.accuracy.isFinite() || location.accuracy !in 0f..25f ||
            !NearestStations.validCoordinates(location.latitude, location.longitude) ||
            SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos !in 0L..5_000_000_000L) return
        last = Location(location)
        listeners.toList().forEach { it(Location(location)) }
    }
}
internal class HostLocationBridge(private val context: CarContext, lifecycle: Lifecycle) : DefaultLifecycleObserver {
    private var sensors: CarSensors? = null
    private var subscribed = false
    private val listener = OnCarDataAvailableListener<CarHardwareLocation> { data ->
        if (subscribed && data.location.status == CarValue.STATUS_SUCCESS) data.location.value?.let(HostLocationFeed::publish)
    }
    init { lifecycle.addObserver(this) }
    override fun onStart(owner: LifecycleOwner) {
        if (subscribed) return
        try {
            sensors = context.getCarService(CarHardwareManager::class.java).carSensors
            subscribed = true
            sensors!!.addCarHardwareLocationListener(CarSensors.UPDATE_RATE_NORMAL, context.mainExecutor, listener)
        } catch (_: RuntimeException) { subscribed = false }
    }
    override fun onDestroy(owner: LifecycleOwner) {
        subscribed = false
        try { sensors?.removeCarHardwareLocationListener(listener) } catch (_: RuntimeException) { }
        HostLocationFeed.clear()
    }
}
