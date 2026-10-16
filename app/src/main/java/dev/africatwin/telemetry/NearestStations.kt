package dev.africatwin.telemetry

import kotlin.math.*

internal data class StationPoint(val id: String, val name: String, val latitude: Double, val longitude: Double)
internal data class FoundStation(val name: String, val latitude: Double, val longitude: Double, val distanceMeters: Double, val direction: String = "", val bearingDegrees: Double? = null, val searchSource: String = "search position") {
    fun relativeDirectionDisplay(heading: Double?): String {
        if (direction == "Here") return "● Here"
        val index = listOf("N", "NE", "E", "SE", "S", "SW", "W", "NW").indexOf(direction)
        val bearing = bearingDegrees ?: index.takeIf { it >= 0 }?.times(45.0) ?: return "Direction unavailable"
        val relative = heading?.takeIf { it.isFinite() }?.let { (bearing - it + 720) % 360 }
        val angle = relative ?: bearing
        val arrow = arrayOf("↑", "↗", "→", "↘", "↓", "↙", "←", "↖")[(((angle % 360 + 360) % 360 + 22.5) / 45).toInt() % 8]
        return "$arrow (${if (relative == null) "north-up" else "relative"}) • $direction"
    }
    val directionDisplay get() = when (direction) {
        "N" -> "↑ N"; "NE" -> "↗ NE"; "E" -> "→ E"; "SE" -> "↘ SE"
        "S" -> "↓ S"; "SW" -> "↙ SW"; "W" -> "← W"; "NW" -> "↖ NW"
        "Here" -> "● Here"; else -> "Direction unavailable"
    }
}
internal object StationNames {
    fun choose(tags: Map<String, String>): String? = listOf("name", "name:en", "brand", "brand:en", "operator", "network", "official_name", "short_name")
        .firstNotNullOfOrNull { tags[it]?.trim()?.takeIf(String::isNotEmpty) }
}

internal object NearestStations {
    const val INITIAL_SEARCH_RADIUS = 15.0 * 1609.344
    private const val SECOND_SEARCH_RADIUS = 30.0 * 1609.344
    private const val MAX_SEARCH_RADIUS = 60.0 * 1609.344
    fun searchRadiusText(radius: Double) = "${(radius / 1609.344).roundToInt()} mi"
    fun nextSearchRadius(foundCount: Int, radius: Double): Double? = when {
        foundCount >= 3 || radius >= MAX_SEARCH_RADIUS -> null
        radius < SECOND_SEARCH_RADIUS -> SECOND_SEARCH_RADIUS
        else -> MAX_SEARCH_RADIUS
    }
    fun newestKnownIndex(points: List<Triple<Double, Double, Long>>, now: Long): Int? = points.indices
        .filter { validCoordinates(points[it].first, points[it].second) && points[it].third in 1..now }
        .maxByOrNull { points[it].third }
    fun direction(latitude: Double, longitude: Double, otherLatitude: Double, otherLongitude: Double): String {
        if (distance(latitude, longitude, otherLatitude, otherLongitude) < 5.0) return "Here"
        val bearing = bearing(latitude, longitude, otherLatitude, otherLongitude)
        return arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[((bearing + 22.5) / 45).toInt() % 8]
    }
    fun bearing(latitude: Double, longitude: Double, otherLatitude: Double, otherLongitude: Double): Double {
        val from = Math.toRadians(latitude); val to = Math.toRadians(otherLatitude)
        val delta = Math.toRadians(otherLongitude - longitude)
        return (Math.toDegrees(atan2(sin(delta) * cos(to), cos(from) * sin(to) - sin(from) * cos(to) * cos(delta))) + 360) % 360
    }
    fun validCoordinates(latitude: Double, longitude: Double) =
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
    fun distance(latitude: Double, longitude: Double, otherLatitude: Double, otherLongitude: Double): Double {
        val lat = Math.toRadians(otherLatitude - latitude)
        val lon = Math.toRadians(otherLongitude - longitude)
        val a = sin(lat / 2).pow(2) + cos(Math.toRadians(latitude)) * cos(Math.toRadians(otherLatitude)) * sin(lon / 2).pow(2)
        return 6_371_000 * 2 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }
    fun select(points: List<StationPoint>, latitude: Double, longitude: Double, radius: Double): List<FoundStation> {
        require(validCoordinates(latitude, longitude) && radius.isFinite() && radius > 0)
        return points.filter { validCoordinates(it.latitude, it.longitude) }.distinctBy { it.id }
            .map { FoundStation(it.name, it.latitude, it.longitude, distance(latitude, longitude, it.latitude, it.longitude), direction(latitude, longitude, it.latitude, it.longitude), bearing(latitude, longitude, it.latitude, it.longitude)) }
            .filter { it.distanceMeters <= radius }.sortedWith(compareBy<FoundStation> { it.distanceMeters }.thenBy { it.name }).take(3)
    }
}

