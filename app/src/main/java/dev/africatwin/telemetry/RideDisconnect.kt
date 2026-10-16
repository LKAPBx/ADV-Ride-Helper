package dev.africatwin.telemetry

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

internal object RideDisconnect {
    private fun prefs(context: Context) = context.getSharedPreferences("ride_disconnect", Context.MODE_PRIVATE)
    fun enabled(context: Context) = prefs(context).getBoolean("enabled", true)
    fun setEnabled(context: Context, enabled: Boolean) { prefs(context).edit().putBoolean("enabled", enabled).apply() }
    fun thresholdMph(context: Context) = prefs(context).getInt("thresholdMph", 5).takeIf { it in 0..100 } ?: 5
    fun setThresholdMph(context: Context, value: Int) {
        require(value in 0..100)
        prefs(context).edit().putInt("thresholdMph", value).apply()
    }
    fun reconnected(context: Context) {
        val continued = prefs(context).getBoolean("continuing", false)
        clear(context)
        if (continued && RideStore(context).running)
            RideStore(context).status("Android Auto reconnected; normal disconnect behavior restored")
    }
    fun clear(context: Context) {
        prefs(context).edit().putBoolean("continuing", false).apply()
        context.getSystemService(NotificationManager::class.java).cancel(48)
    }
    fun handle(context: Context, source: String, stopWhenStationary: Boolean = true) {
        val ride = RideStore(context)
        if (!ride.running) return
        val continued = prefs(context).getBoolean("continuing", false)
        val threshold = thresholdMph(context)
        val keepTracking = RideSafety.continueAfterDisconnect(LiveSpeed.current()?.metersPerSecond,
            threshold, enabled(context), continued)
        // Multiple transport/session callbacks must not undo the initial keep-tracking decision.
        if (keepTracking) {
            if (continued) return
            prefs(context).edit().putBoolean("continuing", true).apply()
            ride.status("$source disconnected while moving; GPS tracking continues. Stop manually when finished.")
            notify(context, source, threshold)
        } else if (stopWhenStationary) RideControls.stop(context, "$source disconnected; GPS tracking stopped")
    }
    private fun notify(context: Context, source: String, threshold: Int) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("tracking_disconnect", "Tracking after disconnect", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 48, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(48, Notification.Builder(context, "tracking_disconnect").setSmallIcon(R.drawable.ic_app)
            .setContentTitle("Connection lost — GPS still tracking")
            .setContentText("$source disconnected above ${AppUnits.get(context).speed(threshold.toDouble())}. Tracking continues; stop it manually when finished.")
            .setStyle(Notification.BigTextStyle().bigText("$source disconnected while your fresh GPS speed was above ${AppUnits.get(context).speed(threshold.toDouble())}. The app is still recording this ride. Use Stop GPS when finished."))
            .setContentIntent(open).setOngoing(true).build())
    }
}
