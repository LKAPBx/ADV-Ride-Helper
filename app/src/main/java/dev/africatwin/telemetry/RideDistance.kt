package dev.africatwin.telemetry

internal object HondaIdentity {
    fun bluetoothMatches(name: String?, match: String = "Honda Motorcycle") =
        match.isNotBlank() && name?.contains(match.trim(), ignoreCase = true) == true
    fun matches(manufacturer: String?, usable: Boolean) = usable && manufacturer == "Honda_Wing"
}

internal data class RideFix(val latitude: Double, val longitude: Double, val accuracyMeters: Double, val elapsedMillis: Long, val source: String? = null)
internal data class RideStep(val meters: Double = 0.0, val gap: Boolean = false, val accepted: Boolean = false, val estimatedMeters: Double = 0.0)
internal class RideDistance {
    companion object { const val MAX_BRIDGE_METERS_PER_SECOND = 100.0 * 0.44704 }
    fun resetAnchor() { anchor = null; lastSeen = null }
    private var anchor: RideFix? = null
    private var lastSeen: Long? = null
    fun add(fix: RideFix, now: Long): RideStep {
        if (!NearestStations.validCoordinates(fix.latitude, fix.longitude) || !fix.accuracyMeters.isFinite() ||
            fix.accuracyMeters !in 0.0..25.0 || now - fix.elapsedMillis !in 0L..15_000L) return RideStep()
        val seen = lastSeen
        if (seen != null && fix.elapsedMillis <= seen) return RideStep()
        lastSeen = fix.elapsedMillis
        val before = anchor
        if (before == null) { anchor = fix; return RideStep(accepted = true) }
        val lostFix = seen != null && fix.elapsedMillis - seen > 30_000
        if (!lostFix && before.source != null && fix.source != null && before.source != fix.source) {
            anchor = fix; return RideStep(gap = true, accepted = true)
        }
        val distance = NearestStations.distance(before.latitude, before.longitude, fix.latitude, fix.longitude)
        val seconds = (fix.elapsedMillis - before.elapsedMillis) / 1000.0
        val maxSpeed = MAX_BRIDGE_METERS_PER_SECOND
        if (seconds <= 0 || distance / seconds > maxSpeed) {
            // Do not use a rejected jump as the origin of a later estimate.
            anchor = null; return RideStep(gap = true, accepted = true)
        }
        val driftThreshold = maxOf(8.0, (before.accuracyMeters + fix.accuracyMeters) / 2)
        if (distance < driftThreshold) {
            if (lostFix) anchor = fix
            return RideStep(gap = lostFix, accepted = true)
        }
        anchor = fix
        return RideStep(meters = distance, gap = lostFix, accepted = true, estimatedMeters = if (lostFix) distance else 0.0)
    }
}

internal object EstimatedFuel {
    fun addMiles(current: Double, missed: Double): Double? {
        if (!current.isFinite() || current < 0 || !missed.isFinite() || missed <= 0 || missed > 1000000) return null
        return (current + missed).takeIf { it.isFinite() }
    }
    fun gallons(start: Double, miles: Double, mpg: Double): Double? {
        if (!start.isFinite() || start <= 0 || !miles.isFinite() || miles < 0 || !mpg.isFinite() || mpg <= 0) return null
        return (start - miles / mpg).coerceAtLeast(0.0)
    }
}

internal object RideSafety {
    fun continueAfterDisconnect(metersPerSecond: Double?, thresholdMph: Int = 5, enabled: Boolean = true,
        continuing: Boolean = false): Boolean {
        if (continuing) return true
        return enabled && thresholdMph in 0..100 && metersPerSecond?.let {
            speedMph(it) != null && it > thresholdMph * .44704
        } == true
    }
    fun speedMph(metersPerSecond: Double): Double? = metersPerSecond.takeIf { it.isFinite() && it in 0.0..90.0 }?.times(2.2369362921)
    fun stationSearchDue(remaining: Double?, full: Double?, threshold: Int): Boolean =
        remaining != null && full != null && remaining.isFinite() && full.isFinite() && full > 0 &&
            remaining >= 0 && threshold in 1..100 && remaining / full * 100 <= threshold
    fun undoMeters(before: Double, after: Double, current: Double): Double {
        require(listOf(before, after, current).all { it.isFinite() && it >= 0 })
        return before + (current - after).coerceAtLeast(0.0)
    }
    fun lowRangeDue(range: Double?, threshold: Int, enabled: Boolean, alerted: Boolean): Boolean =
        enabled && !alerted && threshold > 0 && range != null && range.isFinite() && range >= 0 && range <= threshold
}

internal object SessionCounters {
    fun elapsed(start: Long, now: Long, saved: Long, running: Boolean): Long =
        if (running && start >= 0 && now >= start) now - start else saved.coerceAtLeast(0)
    fun format(meters: Double, elapsed: Long): String {
        val seconds = elapsed.coerceAtLeast(0) / 1000
        val safeMeters = meters.takeIf { it.isFinite() && it >= 0 } ?: 0.0
        return String.format(java.util.Locale.US, "%.2f mi • %02d:%02d:%02d elapsed", safeMeters / 1609.344, seconds / 3600, seconds / 60 % 60, seconds % 60)
    }
}
