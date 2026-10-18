package dev.africatwin.telemetry

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.car.app.Screen
import androidx.car.app.model.*
import androidx.core.graphics.drawable.IconCompat

/** Host-drawn POI map: no embedded Google Maps activity or app-rendered tiles. */
internal object MapDashboard {
    fun enabled(context: Context) = context.getSharedPreferences("dashboard", Context.MODE_PRIVATE).getBoolean("map", false)
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("dashboard", Context.MODE_PRIVATE).edit().putBoolean("map", enabled).apply()
    }

    fun modeAction(screen: Screen, showMap: Boolean, refresh: () -> Unit): Action = Action.Builder()
        .setIcon(CarIcon.Builder(IconCompat.createWithResource(screen.carContext,
            if (showMap) android.R.drawable.ic_menu_mapmode else android.R.drawable.ic_menu_view)).build())
        .setOnClickListener {
            setEnabled(screen.carContext, showMap)
            refresh()
        }.build()

    fun build(screen: Screen): Template {
        val context = screen.carContext
        val fuel = FuelEstimateStore(context)
        val ride = RideStore(context)
        val permitted = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val position = if (permitted) LivePosition.current() else null
        // Browsable rows really open details. They are not given invented POI distances.
        val list = ItemList.Builder()
            .addItem(Row.Builder().setTitle("Fuel & range")
                .addText("${fuel.fuelText()} • ${fuel.rangeText()} • Session: ${ride.currentSessionText().replace(" elapsed", "")}")
                .addText(if (!permitted) "Allow location on phone" else LivePosition.status(context))
                .setBrowsable(true).setOnClickListener { screen.screenManager.push(FuelStopScreen(context, gaugesOnly = true)) }.build())
            .apply { if (TpmsFeature.enabled(context)) {
                addItem(TireCarRow.build(context, "front") { screen.screenManager.push(TirePressureScreen(context)) })
                addItem(TireCarRow.build(context, "rear") { screen.screenManager.push(TirePressureScreen(context)) })
            } }
        // Station results belong only to Find gas, never to the ride dashboard.
        val actions = ActionStrip.Builder()
            .addAction(Action.Builder().setIcon(CarIcon.Builder(IconCompat.createWithResource(context, android.R.drawable.ic_menu_manage)).build())
                .setOnClickListener { screen.screenManager.push(AutoOptionsScreen(context)) }.build())
            .addAction(RideControls.mapAction(context) { screen.invalidate() })
            .addAction(modeAction(screen, showMap = false) { screen.invalidate() })
            .addAction(Action.Builder().setTitle("Find gas").setOnClickListener { screen.screenManager.push(StationSearchScreen(context, searchOnOpen = true)) }.build())
            .build()
        val builder = PlaceListMapTemplate.Builder().setTitle("ADV Ride Helper")
            .setHeaderAction(Action.APP_ICON).setCurrentLocationEnabled(permitted).setItemList(list.build()).setActionStrip(actions)
        // Never plot Hell/default or an old fix as the rider's current position.
        position?.let { builder.setAnchor(Place.Builder(CarLocation.create(it.latitude, it.longitude)).build()) }
        return builder.build()
    }
}
