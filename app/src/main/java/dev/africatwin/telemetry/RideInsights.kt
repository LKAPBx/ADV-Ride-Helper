package dev.africatwin.telemetry

internal object GpsDisplayPolicy {
    fun usable(ageNanos: Long, accuracyMeters: Double) = ageNanos in 0L..30_000_000_000L &&
        accuracyMeters.isFinite() && accuracyMeters in 0.0..100.0
}

internal object FuelConfidence {
    fun describe(hasBaseline: Boolean, hasEconomy: Boolean, gap: Boolean, interrupted: Boolean,
                 manualMiles: Double, calibratedAt: Long, now: Long, estimatedMeters: Double = 0.0): String = when {
        !hasBaseline || !hasEconomy -> "Not calibrated • record a full fill-up and average economy"
        interrupted -> "Fuel estimate: tracking was interrupted; distance may be missing"
        estimatedMeters > 0 -> "Estimated distance across GPS gaps • curved roads may use more fuel than estimated"
        gap -> "Fuel estimate: missing distance may overstate fuel remaining"
        calibratedAt <= 0 -> "Calibration age unknown • confirm a dashboard reading"
        now < calibratedAt || now - calibratedAt > 30L * 24 * 3600 * 1000 -> "Review calibration • dashboard economy is over 30 days old or its date is unavailable"
        manualMiles > 0 -> "Manually corrected • accuracy depends on the confirmed distance and economy"
        else -> "Recently calibrated • no recorded distance gaps; fuel is still an estimate"
    }
}

internal object RideCsv {
    fun cell(value: String): String {
        // Prevent spreadsheet formula execution from imported/textual fields.
        val safe = if (value.trimStart().firstOrNull() in listOf('=', '+', '-', '@')) "'$value" else value
        return "\"${safe.replace("\"", "\"\"")}\""
    }
}

internal data class PacketTiming(val count: Long = 0, val intervalCount: Long = 0,
    val meanMillis: Double = 0.0, val maxMillis: Long = 0) {
    fun add(intervalMillis: Long): PacketTiming {
        if (intervalMillis !in 1000..86_400_000) return copy(count = count + 1)
        return PacketTiming(count + 1, intervalCount + 1,
            meanMillis + (intervalMillis - meanMillis) / (intervalCount + 1), maxOf(maxMillis, intervalMillis))
    }
}
