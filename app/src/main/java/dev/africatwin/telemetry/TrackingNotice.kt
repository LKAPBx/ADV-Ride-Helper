package dev.africatwin.telemetry

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*

internal object TrackingNotice {
    private val handler = Handler(Looper.getMainLooper())
    private var pending: Runnable? = null
    fun changed(context: Context, started: Boolean) {
        val app = context.applicationContext
        pending?.let(handler::removeCallbacks)
        val task = Runnable {
            pending = null
            val ride = RideStore(app)
            if (ride.running != started) return@Runnable
            if (Build.VERSION.SDK_INT >= 33 && app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return@Runnable
            val manager = app.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("tracking_changes", "Tracking started and stopped", NotificationManager.IMPORTANCE_DEFAULT))
            val open = PendingIntent.getActivity(app, 20, Intent(app, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            manager.notify(43, Notification.Builder(app, "tracking_changes").setSmallIcon(R.drawable.ic_app)
                .setContentTitle(if (started) "GPS tracking started" else "GPS tracking stopped")
                .setContentText(if (started) "${ride.status} • ${ride.distanceText()}" else ride.status)
                .setContentIntent(open).setAutoCancel(true).build())
        }
        pending = task; handler.postDelayed(task, 8_000)
    }
}
