package dev.africatwin.telemetry

import android.location.Location

/** Main-thread observers of the GPS_PROVIDER fixes already requested by ride tracking. */
internal object PhoneGpsFeed {
    val listeners = mutableSetOf<(Location) -> Unit>()
    fun publish(location: Location) { listeners.toList().forEach { it(Location(location)) } }
}
