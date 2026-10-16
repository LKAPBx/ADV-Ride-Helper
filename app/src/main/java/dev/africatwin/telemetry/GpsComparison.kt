package dev.africatwin.telemetry

internal data class ComparisonFix(val latitude: Double, val longitude: Double, val time: Long)
internal data class ComparisonPair(val separationMeters: Double, val timeDifferenceMillis: Long, val identical: Boolean)
internal object GpsComparison {
    fun pair(host: List<ComparisonFix>, phone: List<ComparisonFix>): List<ComparisonPair> = host.mapNotNull { fix ->
        if (!NearestStations.validCoordinates(fix.latitude, fix.longitude) || fix.time <= 0) return@mapNotNull null
        val nearest = phone.filter { NearestStations.validCoordinates(it.latitude, it.longitude) && it.time > 0 }
            .minByOrNull { kotlin.math.abs(it.time - fix.time) } ?: return@mapNotNull null
        val difference = nearest.time - fix.time
        if (kotlin.math.abs(difference) > 2000) return@mapNotNull null
        ComparisonPair(NearestStations.distance(fix.latitude, fix.longitude, nearest.latitude, nearest.longitude), difference,
            fix.latitude == nearest.latitude && fix.longitude == nearest.longitude && fix.time == nearest.time)
    }
}
