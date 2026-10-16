package dev.africatwin.telemetry

import android.content.Intent
import android.net.Uri
import android.Manifest
import android.content.pm.PackageManager
import android.location.LocationManager
import android.text.SpannableString
import android.text.Spanned
import androidx.car.app.*
import androidx.car.app.model.*
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner

internal class StationSearchScreen(context: CarContext, searchOnOpen: Boolean = false) : Screen(context) {
    private val search = FuelSearchController(context, lifecycle, autoRefresh = false) { invalidate() }
    private var mapVisible = false
    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            private var requested = false
            override fun onStart(owner: LifecycleOwner) {
                if (searchOnOpen && !requested) {
                    requested = true
                    search.searchOnOpen()
                }
            }
        })
    }
    override fun onGetTemplate(): Template {
        try { return buildTemplate() }
        catch (error: RuntimeException) {
            CrashReports.record(carContext, "Station screen construction; API ${carContext.carAppApiLevel}", error)
            return PaneTemplate.Builder(Pane.Builder().addRow(Row.Builder().setTitle("Station screen unavailable")
                .addText("Error saved. Copy error report from Advanced on the phone.").build()).build())
                .setTitle("Find gas").setHeaderAction(Action.BACK).build()
        }
    }
    private fun buildTemplate(): Template {
        if (mapVisible) {
            try { return mapTemplate() }
            catch (error: RuntimeException) {
                CrashReports.record(carContext, "Station map construction; API ${carContext.carAppApiLevel}", error)
                mapVisible = false
                CarToast.makeText(carContext, "Map unavailable; showing station list", CarToast.LENGTH_LONG).show()
            }
        }
        val list = ItemList.Builder()
        val status = Row.Builder().setTitle(search.buttonText).addText(search.message).addText(search.freshness)
        if (search.canSearch) status.setOnClickListener { search.search() }
        list.addItem(status.build())
        search.stations.forEach { station ->
            list.addItem(Row.Builder().setTitle(station.name)
                .addText(StationDirections.describe(carContext, station))
                .addText("Tap to open map • Straight-line; hours/fuel unverified")
                .setOnClickListener {
                    try { carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE, Uri.parse("geo:${station.latitude},${station.longitude}?q=${station.latitude},${station.longitude}"))) }
                    catch (_: RuntimeException) { CarToast.makeText(carContext, "Navigation unavailable", CarToast.LENGTH_SHORT).show() }
                }.build())
        }
        if (search.attribution.isNotEmpty()) list.addItem(Row.Builder().setTitle(search.attribution).addText("openstreetmap.org/copyright").build())
        return ListTemplate.Builder().setTitle("Gas stations (${search.stations.size})").setHeaderAction(Action.BACK)
            .setActionStrip(ActionStrip.Builder().addAction(Action.Builder().setTitle("Map")
                .setOnClickListener { mapVisible = true; invalidate() }.build()).build())
            .setSingleList(list.build()).build()
    }

    private fun mapTemplate(): Template {
        val permitted = carContext.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val current = if (permitted) LivePosition.current() else null
        val fallback = if (permitted && current == null) LocationFallback.choose(carContext,
            carContext.getSystemService(LocationManager::class.java)) else null
        // A default search center is never a marker representing the user's position.
        val reference = current ?: fallback?.first?.takeUnless { it.provider == "Default location" }
        val label = when {
            !permitted -> "Allow location in phone Settings"
            current != null -> "Reference: current GPS position"
            reference != null -> "Reference: ${fallback?.second}"
            else -> "No app position; location dot managed by Android Auto"
        }
        val list = ItemList.Builder()
        if (search.stations.isEmpty()) list.addItem(Row.Builder().setTitle(search.buttonText).addText(label)
            .addText("${search.message} • ${search.freshness} • © OpenStreetMap contributors")
            .setBrowsable(true).setOnClickListener {
                if (search.canSearch) search.search()
                else CarToast.makeText(carContext, search.buttonText, CarToast.LENGTH_SHORT).show()
            }.build())
        search.stations.forEachIndexed { index, station ->
            val distance = reference?.let { NearestStations.distance(it.latitude, it.longitude, station.latitude, station.longitude) }
                ?: station.distanceMeters
            list.addItem(StationMapRows.create(station, distance, index, AppUnits.get(carContext)) {
                    try { carContext.startCarApp(Intent(CarContext.ACTION_NAVIGATE,
                        Uri.parse("geo:${station.latitude},${station.longitude}?q=${station.latitude},${station.longitude}"))) }
                    catch (_: RuntimeException) { CarToast.makeText(carContext, "Navigation unavailable", CarToast.LENGTH_SHORT).show() }
                })
        }
        if (search.stations.isNotEmpty()) list.addItem(Row.Builder().setTitle(search.buttonText)
            .addText("${search.message} • $label")
            .addText("${search.freshness} • © OpenStreetMap contributors • Straight-line distances")
            .setBrowsable(true).setOnClickListener { mapVisible = false; invalidate() }.build())
        val builder = PlaceListMapTemplate.Builder().setTitle("Gas station map (${search.stations.size})").setHeaderAction(Action.BACK)
            .setCurrentLocationEnabled(permitted).setItemList(list.build())
            .setActionStrip(ActionStrip.Builder().addAction(Action.Builder().setTitle("List")
                .setOnClickListener { mapVisible = false; invalidate() }.build()).build())
        // Anchor only a fresh fix: an old reference is not the moving car dot.
        current?.let { builder.setAnchor(Place.Builder(CarLocation.create(it.latitude, it.longitude)).build()) }
        return builder.build()
    }
}
