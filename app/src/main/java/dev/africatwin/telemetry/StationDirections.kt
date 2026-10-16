package dev.africatwin.telemetry

import android.location.Location
import android.os.SystemClock

internal object LivePosition {
    var fix: Location? = null
    fun current(): Location? = fix?.takeIf { it.hasAccuracy() && NearestStations.validCoordinates(it.latitude, it.longitude) &&
        GpsDisplayPolicy.usable(SystemClock.elapsedRealtimeNanos() - it.elapsedRealtimeNanos, it.accuracy.toDouble()) }
    fun offer(location: Location) {
        if (!location.hasAccuracy() || !NearestStations.validCoordinates(location.latitude, location.longitude) ||
            !GpsDisplayPolicy.usable(SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos, location.accuracy.toDouble())) return
        val previous = current()
        if (previous == null || location.elapsedRealtimeNanos >= previous.elapsedRealtimeNanos) fix = Location(location)
    }
    fun status(context: android.content.Context): String {
        val position = current() ?: return "GPS fix [□□□□] No recent fix"
        val age = ((SystemClock.elapsedRealtimeNanos() - position.elapsedRealtimeNanos) / 1_000_000_000).coerceAtLeast(0)
        val quality = if (position.accuracy > 25) "approximate position" else "position available"
        val bars = if (age > 15 || position.accuracy > 25) 1 else if (age > 5 || position.accuracy > 15) 2 else if (position.accuracy > 10) 3 else 4
        return "GPS fix [${"■".repeat(bars)}${"□".repeat(4 - bars)}] $quality • ±${AppUnits.get(context).shortDistance(position.accuracy.toDouble())} • ${age}s ago"
    }
}
internal object StationDirections {
    private var fallbackAt = -1L
    private var cachedFallback: Pair<Location, String>? = null
    fun describe(context: android.content.Context, station: FoundStation): String {
        val current = LivePosition.current()
        val now = SystemClock.elapsedRealtime()
        val fallback = if (current == null) {
            if (cachedFallback == null || now - fallbackAt !in 0L..999L) {
                cachedFallback = LocationFallback.choose(context, context.getSystemService(android.location.LocationManager::class.java))
                fallbackAt = now
            }
            cachedFallback
        } else null
        val position = current ?: fallback?.first
        val heading = LiveHeading.current()
        val target = position?.let { station.copy(
            distanceMeters = NearestStations.distance(it.latitude, it.longitude, station.latitude, station.longitude),
            direction = NearestStations.direction(it.latitude, it.longitude, station.latitude, station.longitude),
            bearingDegrees = NearestStations.bearing(it.latitude, it.longitude, station.latitude, station.longitude)) } ?: station
        val trueHeading = heading?.let {
            if (it.source.contains("magnetic")) position?.let { p ->
                it.degrees + android.hardware.GeomagneticField(p.latitude.toFloat(), p.longitude.toFloat(),
                    (p.altitude.takeIf { value -> value.isFinite() } ?: 0.0).toFloat(), System.currentTimeMillis()).declination
            } else it.degrees
        }
        return "${AppUnits.get(context).distance(target.distanceMeters / 1609.344)} • ${target.relativeDirectionDisplay(trueHeading)} • ${if (current != null) "current GPS position" else fallback?.second ?: station.searchSource}"
    }
}
