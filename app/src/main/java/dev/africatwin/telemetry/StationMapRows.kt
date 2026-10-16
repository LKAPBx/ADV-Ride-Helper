package dev.africatwin.telemetry

import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.model.*

internal object StationMapRows {
    /** One-line rows let three station markers share the host's visible list viewport. */
    fun create(station: FoundStation, meters: Double, index: Int, units: UnitFormats, clicked: () -> Unit): Row {
        val title = SpannableString("${station.name} •  ")
        title.setSpan(DistanceSpan.create(Distance.create(units.distanceValue(meters / 1609.344), if (units.distanceUnit == "km") Distance.UNIT_KILOMETERS else Distance.UNIT_MILES)),
            title.length - 1, title.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        return Row.Builder().setTitle(title)
            .setMetadata(Metadata.Builder().setPlace(Place.Builder(CarLocation.create(station.latitude, station.longitude))
                .setMarker(PlaceMarker.Builder().setLabel((index + 1).toString()).build()).build()).build())
            .setOnClickListener { clicked() }.build()
    }
}
