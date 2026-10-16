package dev.africatwin.telemetry

/** A packet must have been measured and received inside this listening window. */
internal class TpmsPollWindow(val expected: Set<String>, val startedNanos: Long) {
    val deadlineNanos = startedNanos + 15_000_000_000L
    private val seen = mutableSetOf<String>()
    fun observe(id: String, packetNanos: Long, receivedNanos: Long): Boolean {
        if (id in expected && packetNanos in startedNanos..deadlineNanos &&
            receivedNanos in packetNanos..deadlineNanos) seen.add(id)
        return complete
    }
    val complete get() = expected.isNotEmpty() && seen.containsAll(expected)
    val missing get() = expected - seen
}

internal object TpmsAge {
    const val STALE_MILLIS = 300_000L
    fun idleStale(readingAgeMillis: Long?, listeningMillis: Long) =
        readingAgeMillis?.let { it < 0 || it >= STALE_MILLIS } ?: (listeningMillis >= STALE_MILLIS)
    fun staleAfterTimeout(readingTime: Long?, timeoutTime: Long) = timeoutTime > 0 && (readingTime == null || readingTime <= timeoutTime)
    fun repeatZero(kpa: Int?, ageMillis: Long) = kpa == 0 && ageMillis in 0L until STALE_MILLIS
    fun warning(kpa: Int, lowPsi: Double): Int = if (kpa == 0) 2 else if (lowPsi > 0 && kpa / 6.894757293 < lowPsi) 1 else 0
    fun label(ageMillis: Long): String = when {
        ageMillis < 0 -> "Stale • time unavailable"
        ageMillis >= STALE_MILLIS -> "Stale • ${ageMillis / 60_000} min ago"
        ageMillis >= 60_000 -> "${ageMillis / 60_000} min ago"
        else -> "${ageMillis / 1000}s ago"
    }
}

internal data class FoboCandidate(val kpa: Int, val celsius: Int?, val batteryEvidence: String? = null, val experimentalVolts: Double? = null, val experimentalBatteryPercent: Int? = null) {
    val psi get() = kpa / 6.894757293
    val fahrenheit get() = celsius?.let { it * 1.8 + 32 }
}

/** Experimental Type-E interpretation, NOT verified for this user's Bike 2 firmware.
 * Format reference: bkbilly/tpms_ble, tpms_parser/parser.py (_process_tpms_e).
 * No pressure reference/altitude/temperature compensation is inferred.
 */
internal object FoboDecoder {
    /** User's 0x0126 sample: six address bytes then four data bytes.
     * Pressure matched once. User opted into the provisional temperature interpretation.
     */
    fun decodeBike2Pressure(data: ByteArray, address: String): FoboCandidate? {
        if (data.size != 10) return null
        val embedded = data.take(6).joinToString(":") { "%02X".format(it.toInt() and 255) }
        if (!embedded.equals(address, ignoreCase = true)) return null
        val pressure = (((data[8].toInt() and 255) shl 8) or (data[9].toInt() and 255)) and 1023
        val temperature = (data[7].toInt() and 127) - 40
        return FoboCandidate(pressure, temperature, "Bike 2 0126: byte6=0x%02X; pressure/status word=0x%04X; battery mapping unverified".format(data[6].toInt() and 255, ((data[8].toInt() and 255) shl 8) or (data[9].toInt() and 255)), experimentalBatteryPercent = ((data[6].toInt() and 255) * 2).takeIf { it in 0..100 })
    }
    fun decode(data: ByteArray, serviceFrame: Boolean): FoboCandidate? {
        if (data.size !in 4..32) return null
        val tail = data.takeLast(4).map { it.toInt() and 255 }
        val temperature = (tail[1] and 127) - if (serviceFrame) 40 else 50
        val pressure = ((tail[2] shl 8) or tail[3]) and 1023
        val word = (tail[2] shl 8) or tail[3]
        val volts = ((word shr 10) and 63) / 10.0
        return FoboCandidate(pressure, temperature, "Type-E battery code=${(word shr 10) and 63}; protocol unverified",
            volts.takeIf { !serviceFrame && it in 2.0..3.6 })
    }
}
