package dev.africatwin.telemetry

import android.os.SystemClock

internal data class Heading(val degrees: Double, val source: String, val time: Long)
internal object LiveHeading {
    var magnetic: Heading? = null
    var travel: Heading? = null
    fun current(): Heading? {
        val now = SystemClock.elapsedRealtime()
        return listOf(travel, magnetic).firstOrNull { it != null && now - it.time in 0L..10_000L }
    }
    fun clear() { magnetic = null; travel = null; LiveSpeed.reading = null; LivePosition.fix = null }
    fun cardinal(degrees: Double) = arrayOf("N", "NE", "E", "SE", "S", "SW", "W", "NW")[(((degrees + 360) % 360 + 22.5) / 45).toInt() % 8]
}

internal data class SpeedReading(val metersPerSecond: Double, val source: String, val time: Long)
internal object LiveSpeed {
    var reading: SpeedReading? = null
    fun current(): SpeedReading? = reading?.takeIf {
        SystemClock.elapsedRealtime() - it.time in 0L..10_000L && RideSafety.speedMph(it.metersPerSecond) != null
    }
    fun text(context: android.content.Context): String = current()?.let { AppUnits.get(context).speed(RideSafety.speedMph(it.metersPerSecond)!!) } ?: "— ${AppUnits.get(context).speedUnit}"
}
