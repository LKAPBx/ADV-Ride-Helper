package dev.africatwin.telemetry

import android.content.Context
import android.content.Intent
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.model.ParkedOnlyOnClickListener

import androidx.car.app.model.Action
import androidx.car.app.model.CarColor


internal object RideControls {
    const val START_ON_PHONE = "dev.africatwin.telemetry.START_ON_PHONE"
    private fun startFromCar(context: CarContext) {
        fun notify(text: String) { CarToast.makeText(context, text, CarToast.LENGTH_LONG).show() }
        if (context.getSharedPreferences("ride_edit", Context.MODE_PRIVATE).getBoolean("editing", false)) {
            notify("Finish editing dashboard readings on phone first"); return
        }
        if (FuelEstimateStore(context).baselineOdometer == null) {
            notify("Record a fill-up on your phone first"); return
        }
        val precise = context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val background = android.os.Build.VERSION.SDK_INT < 29 || context.checkSelfPermission(android.Manifest.permission.ACCESS_BACKGROUND_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (precise && background) {
            try {
                context.applicationContext.startForegroundService(Intent(context, RideTrackingService::class.java))
                TrackingStartReport.record(context, "Manual head-unit Start: direct GPS service start requested")
                notify("GPS start requested; check tracking status")
                return
            } catch (error: RuntimeException) {
                TrackingStartReport.record(context, "Manual head-unit Start: Android blocked direct service start", error)
            }
        } else TrackingStartReport.record(context, "Manual head-unit Start: phone required; precise=$precise, background=$background")
        try {
            context.applicationContext.startActivity(Intent(context, MainActivity::class.java).setAction(START_ON_PHONE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
            TrackingStartReport.record(context, "Phone fallback: activity launch requested")
            notify("Continue on phone; allow location All the time for direct head-unit Start")
        } catch (error: RuntimeException) {
            TrackingStartReport.record(context, "Phone fallback: activity launch failed", error)
            notify("GPS start blocked (${error.javaClass.simpleName}); open phone. Details saved in Advanced")
        }
    }
    fun stop(context: Context, reason: String = "Tracking stopped") {
        val prefs = context.getSharedPreferences("ride_edit", Context.MODE_PRIVATE)
        prefs.edit().putLong("stopSerial", prefs.getLong("stopSerial", 0) + 1).apply()
        BikeConnectionService.current?.suppressForConnection()
        RideStore(context).stop(reason)
        context.stopService(Intent(context, RideTrackingService::class.java))
    }
    fun carAction(context: CarContext, refresh: () -> Unit): Action {
        val running = RideStore(context).running
        val button = Action.Builder().setBackgroundColor(if (running) CarColor.GREEN else CarColor.BLUE)
        button.setTitle(if (running) "Stop GPS • ON" else "Start GPS • OFF")
        if (running) button.setOnClickListener { stop(context); refresh() }
        else button.setOnClickListener(ParkedOnlyOnClickListener.create { startFromCar(context); refresh() })
        return button.build()
    }
    fun mapAction(context: CarContext, refresh: () -> Unit): Action {
        val running = RideStore(context).running
        // Map action strips accept icon actions, without Pane-only primary flags.
        val button = Action.Builder().setIcon(androidx.car.app.model.CarIcon.Builder(
            androidx.core.graphics.drawable.IconCompat.createWithResource(context,
                if (running) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)).build())
        if (running) button.setOnClickListener { stop(context); refresh() }
        else button.setOnClickListener(ParkedOnlyOnClickListener.create { startFromCar(context); refresh() })
        return button.build()
    }
}

