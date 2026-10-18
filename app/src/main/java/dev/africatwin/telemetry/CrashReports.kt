package dev.africatwin.telemetry

import android.app.Application
import android.content.Context

class TelemetryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        WidgetUpdates.start(this)
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { CrashReports.record(this, "Uncaught on ${thread.name}", error) }
            if (previous != null) previous.uncaughtException(thread, error)
            else { android.os.Process.killProcess(android.os.Process.myPid()); kotlin.system.exitProcess(10) }
        }
    }
}

internal object CrashReports {
    fun pending(context: Context) = context.getSharedPreferences("crash_report", Context.MODE_PRIVATE).getBoolean("pending", false)
    fun report(context: Context) = context.getSharedPreferences("crash_report", Context.MODE_PRIVATE).getString("report", "No error recorded") ?: "No error recorded"
    fun acknowledge(context: Context) { context.getSharedPreferences("crash_report", Context.MODE_PRIVATE).edit().putBoolean("pending", false).commit() }
    fun record(context: Context, stage: String, error: Throwable) {
        val version = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull()
        val report = "${java.text.DateFormat.getDateTimeInstance().format(java.util.Date())}\nApp $version • $stage\n${error.stackTraceToString()}"
        context.getSharedPreferences("crash_report", Context.MODE_PRIVATE).edit()
            .putString("report", report.take(16000)).putBoolean("pending", true).commit()
        android.util.Log.e("ATTelemetry", stage, error)
    }
}
