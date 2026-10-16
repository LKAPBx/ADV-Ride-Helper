package dev.africatwin.telemetry

import android.content.Context
import android.location.Location
import android.location.LocationManager

internal object LocationFallback {
    fun remember(context: Context, location: Location) {
        if (!NearestStations.validCoordinates(location.latitude, location.longitude) || location.time <= 0) return
        val prefs = context.getSharedPreferences("last_location", Context.MODE_PRIVATE)
        if (location.time <= prefs.getLong("time", 0)) return
        prefs.edit().putString("lat", location.latitude.toString()).putString("lon", location.longitude.toString())
            .putLong("time", location.time).apply()
    }
    fun choose(context: Context, manager: LocationManager): Pair<Location, String> {
        val prefs = context.getSharedPreferences("last_location", Context.MODE_PRIVATE)
        val lat = prefs.getString("lat", "")?.toDoubleOrNull()
        val lon = prefs.getString("lon", "")?.toDoubleOrNull()
        val saved = if (lat != null && lon != null && NearestStations.validCoordinates(lat, lon))
            Location("Saved location").apply { latitude = lat; longitude = lon; time = prefs.getLong("time", 0) } else null
        val candidates = listOfNotNull(saved, LivePosition.fix) + listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).mapNotNull {
            runCatching { manager.getLastKnownLocation(it) }.getOrNull()
        }
        val last = NearestStations.newestKnownIndex(candidates.map { Triple(it.latitude, it.longitude, it.time) }, System.currentTimeMillis())?.let { candidates[it] }
        if (last != null) {
            remember(context, last)
            val age = ((System.currentTimeMillis() - last.time).coerceAtLeast(0) / 60_000)
            return Location(last) to "Last known location (${age}m old at search)"
        }
        // Hell, Michigan: 42°26′05″ N, 83°59′06″ W.
        return Location("Default location").apply { latitude = 42.434722; longitude = -83.985 } to "Hell, Michigan (default; no GPS location)"
    }
}
