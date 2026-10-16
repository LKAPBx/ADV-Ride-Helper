package dev.africatwin.telemetry

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

internal object HondaAutoTracking {
    fun deviceName(context: Context) = context.getSharedPreferences("honda_auto_tracking", Context.MODE_PRIVATE)
        .getString("deviceName", null)?.trim()?.takeIf { it.isNotEmpty() } ?: "Honda Motorcycle"
    fun setDeviceName(context: Context, value: String) {
        require(value.trim().isNotEmpty() && value.trim().length <= 100)
        context.getSharedPreferences("honda_auto_tracking", Context.MODE_PRIVATE).edit().putString("deviceName", value.trim()).apply()
    }
    fun bluetoothEnabled(context: Context) = context.getSharedPreferences("honda_auto_tracking", Context.MODE_PRIVATE).getBoolean("bluetooth", true)
    fun setBluetoothEnabled(context: Context, enabled: Boolean) { context.getSharedPreferences("honda_auto_tracking", Context.MODE_PRIVATE).edit().putBoolean("bluetooth", enabled).apply() }
    fun attempt(context: Context, source: String = "Honda_Wing recognized"): String {
        if (context.getSharedPreferences("ride_edit", Context.MODE_PRIVATE).getBoolean("editing", false)) return "Automatic tracking paused while editing fuel/distance"
        if (BikeConnectionService.current?.suppressed == true) return "Automatic tracking suppressed after Stop for this connection"
        val ride = RideStore(context)
        if (ride.running) return "Honda_Wing recognized; GPS already running"
        fun blocked(reason: String): String { ride.status(reason); return reason }
        if (FuelEstimateStore(context).baselineOdometer == null) return blocked("Honda recognized; record a fill-up before tracking")
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return blocked("Honda recognized; precise location required")
        if (Build.VERSION.SDK_INT >= 29 && context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return blocked("Honda recognized; allow location all the time for automatic start, or tap Start manually")
        return try {
            context.startForegroundService(Intent(context, RideTrackingService::class.java))
            "$source; automatic GPS start requested (check live tracking status)"
        } catch (e: RuntimeException) {
            blocked("Automatic GPS start blocked by Android (${e.javaClass.simpleName}); tap Start while parked")
        }
    }
}
