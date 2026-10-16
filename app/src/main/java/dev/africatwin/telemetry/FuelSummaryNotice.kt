package dev.africatwin.telemetry

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.car.app.notification.CarAppExtender
import androidx.car.app.notification.CarNotificationManager
import androidx.car.app.notification.CarPendingIntent
import androidx.core.app.NotificationCompat

internal object FuelSummaryNotice {
    private const val ID = 44
    fun enabled(context: Context) = context.getSharedPreferences("fuel_notice", Context.MODE_PRIVATE).getBoolean("enabled", true)
    fun interval(context: Context): Int = context.getSharedPreferences("fuel_notice", Context.MODE_PRIVATE)
        .getInt("minutes", 10).takeIf { it in listOf(5, 10, 15, 30) } ?: 10
    fun cancel(context: Context) { CarNotificationManager.from(context).cancel(ID) }
    fun show(context: Context): Boolean {
        val estimate = FuelEstimateStore(context)
        return post(context, ID, "Ride fuel estimate", "Estimated fuel ${estimate.fuelText()} • Range ${estimate.rangeText()}")
    }
    fun checkLowRange(context: Context) {
        val prefs = context.getSharedPreferences("fuel_notice", Context.MODE_PRIVATE)
        val range = FuelEstimateStore(context).range ?: return
        val threshold = prefs.getInt("lowMiles", 40)
        if (RideSafety.lowRangeDue(range, threshold, prefs.getBoolean("lowEnabled", true), prefs.getBoolean("lowAlerted", false)) &&
            post(context, 46, "Low estimated range", "${FuelEstimateStore(context).rangeText()} remaining • Estimate only; check fuel")) {
            prefs.edit().putBoolean("lowAlerted", true).apply()
        }
    }
    fun clearLowRange(context: Context) { CarNotificationManager.from(context).cancel(46) }
    fun sessionStopped(context: Context, summary: String) {
        if (context.getSharedPreferences("fuel_notice", Context.MODE_PRIVATE).getBoolean("stopSummary", true)) {
            post(context, 45, "Ride session ended", summary, NotificationManager.IMPORTANCE_DEFAULT)
        }
    }
    private fun post(context: Context, id: Int, title: String, text: String,
        importance: Int = NotificationManager.IMPORTANCE_HIGH): Boolean = try {
        val manager = CarNotificationManager.from(context)
        check(manager.areNotificationsEnabled())
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("fuel_summary", "Periodic fuel and range", NotificationManager.IMPORTANCE_DEFAULT))
        val phone = PendingIntent.getActivity(context, id, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val car = CarPendingIntent.getCarApp(context, id, Intent(context, FuelStopService::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, "fuel_summary")
            .setSmallIcon(R.drawable.ic_app).setContentTitle(title).setContentText(text)
            .setContentIntent(phone).setAutoCancel(true)
            .extend(CarAppExtender.Builder().setContentTitle(title).setContentText(text)
                .setSmallIcon(R.drawable.ic_app).setContentIntent(car)
                .setImportance(importance).build())
        manager.notify(id, notification); true
    } catch (e: RuntimeException) {
            android.util.Log.w("ATTelemetry", "Fuel summary notification failed", e); false
    }
}
