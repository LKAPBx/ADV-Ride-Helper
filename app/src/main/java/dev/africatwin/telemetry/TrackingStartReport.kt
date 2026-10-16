package dev.africatwin.telemetry

import android.content.Context

internal object TrackingStartReport {
    fun record(context: Context, message: String, error: Throwable? = null) {
        val prefs = context.getSharedPreferences("tracking_start_report", Context.MODE_PRIVATE)
        val entry = "${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())}\n$message" +
            (error?.let { "\n${it.stackTraceToString()}" } ?: "")
        prefs.edit().putString("report", ((prefs.getString("report", "") ?: "") + "\n\n" + entry).takeLast(16000)).apply()
        if (error != null) android.util.Log.w("ATTelemetry", message, error)
    }
    fun read(context: Context) = context.getSharedPreferences("tracking_start_report", Context.MODE_PRIVATE)
        .getString("report", "No GPS start attempts recorded yet") ?: "No report"
}
